package com.soruhavuzu.architecture

/**
 * ArchUnit'in bytecode'da göremediği, SQL metni üzerindeki kurallar (kurallar.md A3, A5, A13).
 * Fonksiyonlar saf metin alır; böylece negatif testlerle doğrudan sınanabilir.
 */
object SourceLint {
    val moduleSchemas = ArchitectureRules.businessModules.toSet()
    private val allSchemas = moduleSchemas + "platform"

    private val forbiddenSelection =
        listOf(
            Regex("""(?i)order\s+by\s+random\s*\("""),
            Regex("""(?i)\btablesample\b"""),
        )

    private val immutableTables =
        listOf(
            "content.question_versions",
            "practice.attempts",
            "practice.attempt_keys",
            "moderation.moderation_actions",
            "reputation.reputation_events",
        )
    private val immutableMutation =
        Regex("""(?i)\b(update|delete\s+from)\s+(${immutableTables.joinToString("|") { Regex.escape(it) }})\b""")

    private val schemaReference = Regex("""\b(${allSchemas.joinToString("|")})\.[a-z_]""")
    private val stringLiteral = Regex("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", RegexOption.DOT_MATCHES_ALL)

    /** A13: seçim sorgularında ORDER BY random() ve TABLESAMPLE yasak. */
    fun forbiddenSelectionViolations(text: String): List<String> =
        forbiddenSelection.flatMap { rx -> rx.findAll(text).map { it.value }.toList() }

    /** A5: değişmez tablolara UPDATE/DELETE yazılmaz. */
    fun immutableMutationViolations(text: String): List<String> = immutableMutation.findAll(text).map { it.value }.toList()

    /**
     * A3: bir modülün Kotlin kaynağındaki string literal'lar yalnızca kendi şemasına başvurur.
     * `shared` yalnızca altyapı (`platform`) şemasına başvurabilir (ADR 0014).
     */
    fun foreignSchemaViolations(
        module: String,
        kotlinSource: String,
    ): List<String> {
        val allowed = if (module == "shared") setOf("platform") else setOf(module)
        return stringLiteral
            .findAll(kotlinSource)
            .map { it.groupValues[1] + it.groupValues[2] }
            .flatMap { literal -> schemaReference.findAll(literal).map { it.groupValues[1] } }
            .filterNot { it in allowed }
            .distinct()
            .toList()
    }

    /** A3: bir modülün migration'ı yalnızca kendi şemasına (ve public eklenti tiplerine) başvurur. */
    fun migrationForeignSchemaViolations(
        module: String,
        sql: String,
    ): List<String> =
        schemaReference
            .findAll(sql)
            .map { it.groupValues[1] }
            .filterNot { it == module }
            .distinct()
            .toList()
}
