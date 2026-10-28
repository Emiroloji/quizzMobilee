package com.soruhavuzu.practice.internal.infrastructure

import com.soruhavuzu.practice.internal.application.AttemptPartitionPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JdbcAttemptPartitionAdapter(
    private val jdbc: JdbcClient,
) : AttemptPartitionPort {
    override fun ensurePartitions(
        from: LocalDate,
        months: Int,
    ): Int =
        jdbc
            .sql("SELECT practice.ensure_attempt_partitions(:from, :months)")
            .param("from", from)
            .param("months", months)
            .query(Int::class.java)
            .single()
}
