package com.soruhavuzu.practice.internal.application

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/** `practice.attempts` için gelecek ayların partition'larını önceden oluşturur (fazlar.md Faz 0). */
@Component
class AttemptPartitionJob(
    private val partitions: AttemptPartitionPort,
    private val properties: PracticeProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${soruhavuzu.practice.attempt-partitions.cron}", zone = "UTC")
    @SchedulerLock(name = "practice.ensureAttemptPartitions", lockAtMostFor = "PT5M")
    fun scheduled() {
        ensurePartitions()
    }

    fun ensurePartitions(): Int {
        val created = partitions.ensurePartitions(LocalDate.now(clock), properties.attemptPartitions.monthsAhead)
        log.info("attempt_partitions_ensured created={}", created)
        return created
    }
}
