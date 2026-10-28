package com.soruhavuzu

import com.soruhavuzu.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID

class SchemaMigrationTests : IntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `her modülün kendi şeması ve platform şeması oluşur`() {
        val schemas = jdbc.queryForList("SELECT schema_name FROM information_schema.schemata", String::class.java)
        schemas shouldContainAll EXPECTED_TABLES.keys
    }

    @Test
    fun `mimari dokümandaki tüm tablolar v2 dahil mevcuttur`() {
        EXPECTED_TABLES.forEach { (schema, tables) ->
            val actual = jdbc.queryForList(
                """
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r', 'p') AND NOT c.relispartition
                """.trimIndent(),
                String::class.java,
                schema,
            ).toSet()
            actual shouldBe tables
        }
    }

    @Test
    fun `soru versiyonunda UPDATE ve DELETE veritabanında reddedilir`() {
        val versionId = insertQuestionVersion()
        assertImmutable("content.question_versions", "body = '{}'::jsonb", versionId)
    }

    @Test
    fun `attempt kaydında UPDATE ve DELETE veritabanında reddedilir`() {
        val attemptId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO practice.attempts (id, client_attempt_id, user_id, question_id, version_id, session_type,
              selected, is_correct, is_first, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, 'PRACTICE', '{"options":["B"]}', true, true, 4200, now())
            """.trimIndent(),
            attemptId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        )
        assertImmutable("practice.attempts", "is_correct = false", attemptId)
    }

    @Test
    fun `idempotency anahtarında UPDATE ve DELETE reddedilir, aynı anahtar ikinci kez yazılmaz`() {
        val key = UUID.randomUUID()
        val insert = """
            INSERT INTO practice.attempt_keys (client_attempt_id, user_id, attempt_id, created_at)
            VALUES (?, ?, ?, now()) ON CONFLICT DO NOTHING
        """.trimIndent()
        jdbc.update(insert, key, UUID.randomUUID(), UUID.randomUUID()) shouldBe 1
        jdbc.update(insert, key, UUID.randomUUID(), UUID.randomUUID()) shouldBe 0
        shouldThrow<DataAccessException> {
            jdbc.update("UPDATE practice.attempt_keys SET attempt_id = ? WHERE client_attempt_id = ?", UUID.randomUUID(), key)
        }.message shouldContain "IMMUTABLE_TABLE"
    }

    @Test
    fun `denetim izinde UPDATE ve DELETE veritabanında reddedilir`() {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO moderation.moderation_actions (id, target_type, target_id, actor_id, action, created_at)
            VALUES (?, 'QUESTION', ?, ?, 'APPROVE', now())
            """.trimIndent(),
            id, UUID.randomUUID(), UUID.randomUUID(),
        )
        assertImmutable("moderation.moderation_actions", "reason = 'x'", id)
    }

    @Test
    fun `itibar defterinde UPDATE ve DELETE veritabanında reddedilir`() {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO reputation.reputation_events (id, user_id, kind, delta, ref_id, created_at) VALUES (?, ?, 'QUESTION_ACTIVE', 5, ?, now())",
            id, UUID.randomUUID(), UUID.randomUUID(),
        )
        assertImmutable("reputation.reputation_events", "delta = 500", id)
    }

    @Test
    fun `değişmez tablolar TRUNCATE CASCADE ile de boşaltılamaz`() {
        IMMUTABLE_TABLES.forEach { table ->
            shouldThrow<DataAccessException> { jdbc.execute("TRUNCATE $table CASCADE") }.message shouldContain "IMMUTABLE_TABLE"
        }
    }

    @Test
    fun `attempts bu ay dahil en az 3 aylık partition ile başlar`() {
        val month = LocalDate.now().withDayOfMonth(1)
        val expected = (0L..2L).map { month.plusMonths(it) }
            .map { "attempts_y%04dm%02d".format(it.year, it.monthValue) }
        val partitions = jdbc.queryForList(
            "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid WHERE i.inhparent = 'practice.attempts'::regclass",
            String::class.java,
        )
        partitions shouldContainAll expected
    }

    @Test
    fun `olay kaydı, kilit ve migration geçmişi platform şemasındadır`() {
        val tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'platform'",
            String::class.java,
        )
        tables shouldContainAll listOf("event_publication", "shedlock", "flyway_schema_history")
    }

    private fun assertImmutable(table: String, setClause: String, id: UUID) {
        shouldThrow<DataAccessException> { jdbc.update("UPDATE $table SET $setClause WHERE id = ?", id) }
            .message shouldContain "IMMUTABLE_TABLE"
        shouldThrow<DataAccessException> { jdbc.update("DELETE FROM $table WHERE id = ?", id) }
            .message shouldContain "IMMUTABLE_TABLE"
    }

    private fun insertQuestionVersion(): UUID {
        val questionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO content.questions (id, author_id, exam_id, primary_topic_id, status, uploader_rating,
              originality_declared_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'DRAFT', 7, now(), now(), now())
            """.trimIndent(),
            questionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        )
        jdbc.update(
            """
            INSERT INTO content.question_versions (id, question_id, version, body, answer_spec, solution,
              question_type, option_count, text_hash, schema_version, created_by, created_at)
            VALUES (?, ?, 1, '{"schema":1,"blocks":[]}', '{"type":"mcq"}', '{"schema":1,"blocks":[]}',
              'mcq', 5, '\x00'::bytea, 1, ?, now())
            """.trimIndent(),
            versionId, questionId, UUID.randomUUID(),
        )
        return versionId
    }

    private companion object {
        val IMMUTABLE_TABLES = listOf(
            "content.question_versions", "practice.attempts", "practice.attempt_keys",
            "moderation.moderation_actions", "reputation.reputation_events",
        )

        val EXPECTED_TABLES = mapOf(
            "platform" to setOf("event_publication", "shedlock", "flyway_schema_history"),
            "identity" to setOf("users", "auth_identities", "devices"),
            "taxonomy" to setOf("exams", "topics"),
            "media" to setOf("assets"),
            "content" to setOf(
                "questions", "question_versions", "question_version_embeddings", "question_topics", "question_assets",
            ),
            "practice" to setOf("sessions", "attempts", "attempt_keys", "user_question_state", "question_pool"),
            "calibration" to setOf("question_difficulty", "user_skill", "uploader_bias", "difficulty_votes"),
            "moderation" to setOf("reports", "review_items", "key_suspects", "moderation_actions", "sanctions"),
            "reputation" to setOf("reputation_events", "reputation_balances"),
            "stats" to setOf("daily_activity", "topic_stats"),
            "notification" to setOf("notifications"),
        )
    }
}
