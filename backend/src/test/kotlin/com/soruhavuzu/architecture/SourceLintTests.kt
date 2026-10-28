package com.soruhavuzu.architecture

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

class SourceLintTests {
    private val kotlinRoot = Path.of("src/main/kotlin/com/soruhavuzu")
    private val migrationRoot = Path.of("src/main/resources/db/migration")

    private val kotlinFiles = kotlinRoot.walk().filter { it.isRegularFile() && it.extension == "kt" }.toList()
    private val migrationFiles = migrationRoot.walk().filter { it.isRegularFile() && it.extension == "sql" }.toList()

    @Test
    fun `kaynaklarda ORDER BY random() ve TABLESAMPLE yoktur`() {
        (kotlinFiles + migrationFiles)
            .flatMap { file -> SourceLint.forbiddenSelectionViolations(file.readText()).map { "${file.name}: $it" } }
            .shouldBeEmpty()
    }

    @Test
    fun `kotlin kaynaklarında değişmez tablolara UPDATE veya DELETE yazılmaz`() {
        kotlinFiles
            .flatMap { file -> SourceLint.immutableMutationViolations(file.readText()).map { "${file.name}: $it" } }
            .shouldBeEmpty()
    }

    @Test
    fun `her modülün SQL'i yalnızca kendi şemasına başvurur`() {
        kotlinFiles
            .filter { it.relativeTo(kotlinRoot).nameCount > 1 }
            .flatMap { file ->
                val module = file.relativeTo(kotlinRoot).getName(0).toString()
                SourceLint.foreignSchemaViolations(module, file.readText()).map { "${file.name} → $it" }
            }.shouldBeEmpty()
    }

    @Test
    fun `her migration yalnızca kendi modülünün şemasına başvurur`() {
        migrationFiles.size shouldBe 11
        migrationFiles
            .flatMap { file ->
                val module = MIGRATION_NAME.find(file.name)?.groupValues?.get(1) ?: error("Geçersiz migration adı: ${file.name}")
                SourceLint.migrationForeignSchemaViolations(module, file.readText()).map { "${file.name} → $it" }
            }.shouldBeEmpty()
    }

    @Test
    fun `lint kasıtlı ihlalleri yakalar`() {
        SourceLint.forbiddenSelectionViolations("SELECT * FROM practice.question_pool ORDER BY random() LIMIT 20").shouldNotBeEmpty()
        SourceLint.forbiddenSelectionViolations("SELECT * FROM practice.question_pool TABLESAMPLE SYSTEM (1)").shouldNotBeEmpty()
        SourceLint.immutableMutationViolations("UPDATE practice.attempts SET is_correct = true").shouldNotBeEmpty()
        SourceLint.immutableMutationViolations("delete from content.question_versions where id = ?").shouldNotBeEmpty()
        SourceLint.foreignSchemaViolations(
            "practice",
            "val sql = \"\"\"SELECT q.status FROM content.questions q JOIN practice.question_pool p ON true\"\"\"",
        ) shouldContainExactly listOf("content")
        SourceLint.foreignSchemaViolations("content", "jdbc.sql(\"SELECT 1 FROM platform.shedlock\")") shouldContainExactly
            listOf("platform")
        SourceLint.migrationForeignSchemaViolations(
            "stats",
            "CREATE TABLE stats.x (id uuid REFERENCES identity.users(id));",
        ) shouldContainExactly
            listOf("identity")
    }

    @Test
    fun `lint meşru kullanımı ihlal saymaz`() {
        SourceLint.foreignSchemaViolations("shared", "withTableName(\"platform.shedlock\")").shouldBeEmpty()
        SourceLint.foreignSchemaViolations("practice", "import com.soruhavuzu.content.ContentApi").shouldBeEmpty()
        SourceLint.immutableMutationViolations("UPDATE practice.user_question_state SET favorite = true").shouldBeEmpty()
    }

    private companion object {
        val MIGRATION_NAME = Regex("""^V\d{12}__([a-z]+)_.+\.sql$""")
    }
}
