package com.soruhavuzu.practice

import com.soruhavuzu.practice.internal.application.AttemptPartitionJob
import com.soruhavuzu.practice.internal.application.AttemptPartitionPort
import com.soruhavuzu.practice.internal.application.PracticeProperties
import com.soruhavuzu.support.IntegrationTest
import com.soruhavuzu.support.attemptPartitionNames
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AttemptPartitionJobTests : IntegrationTest() {
    @Autowired
    lateinit var port: AttemptPartitionPort

    @Autowired
    lateinit var properties: PracticeProperties

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun jobAt(instant: String) = AttemptPartitionJob(port, properties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))

    @Test
    fun `iş, Clock'taki aydan başlayarak ayarlanan sayıda aylık partition oluşturur`() {
        jobAt("2031-11-15T10:00:00Z").ensurePartitions() shouldBe 3

        val partitions =
            jdbc.attemptPartitionNames()
        partitions shouldContainAll listOf("attempts_y2031m11", "attempts_y2031m12", "attempts_y2032m01")
    }

    @Test
    fun `iş tekrar çalıştığında var olan partition'ları atlar`() {
        val job = jobAt("2033-03-01T00:00:00Z")
        job.ensurePartitions() shouldBe 3
        job.ensurePartitions() shouldBe 0
    }

    @Test
    fun `partition sınırları oturumun saat diliminden bağımsız olarak UTC ay sınırlarıdır`() {
        // Geliştirici makinesi UTC+3 olabilir; sınırlar yine de UTC gece yarısı olmalı
        jdbc.execute(
            ConnectionCallback { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET TIME ZONE 'Europe/Istanbul'")
                    statement.execute("SELECT practice.ensure_attempt_partitions(DATE '2045-07-01', 1)")
                    statement.execute("RESET TIME ZONE")
                }
            },
        )

        partitionOf("2045-07-01T00:00:00Z") shouldBe "practice.attempts_y2045m07"
        partitionOf("2045-07-31T23:59:59Z") shouldBe "practice.attempts_y2045m07"
    }

    private fun partitionOf(createdAt: String): String? {
        jdbc.execute("SELECT practice.ensure_attempt_partitions(DATE '2045-06-01', 3)")
        return jdbc.queryForObject(
            """
            INSERT INTO practice.attempts (id, client_attempt_id, user_id, question_id, version_id, session_type,
              selected, is_correct, is_first, duration_ms, created_at)
            VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
              'PRACTICE', '{"options":["A"]}', false, true, 3000, ?::timestamptz)
            RETURNING tableoid::regclass::text
            """.trimIndent(),
            String::class.java,
            createdAt,
        )
    }

    @Test
    fun `oluşturulan partition o aya ait attempt'i kabul eder`() {
        jobAt("2034-06-10T00:00:00Z").ensurePartitions()
        jdbc.update(
            """
            INSERT INTO practice.attempts (id, client_attempt_id, user_id, question_id, version_id, session_type,
              selected, is_correct, is_first, duration_ms, created_at)
            VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
              'PRACTICE', '{"options":["A"]}', false, true, 3000, '2034-07-31T23:59:59Z')
            """.trimIndent(),
        ) shouldBe 1
    }
}
