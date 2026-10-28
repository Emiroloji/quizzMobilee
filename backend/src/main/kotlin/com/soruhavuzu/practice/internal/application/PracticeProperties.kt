package com.soruhavuzu.practice.internal.application

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("soruhavuzu.practice")
data class PracticeProperties(
    @field:Valid val attemptPartitions: AttemptPartitions,
) {
    data class AttemptPartitions(
        /** Bugünün ayı dahil kaç aylık partition hazır tutulur. */
        @field:Min(2) val monthsAhead: Int,
        @field:NotBlank val cron: String,
    )
}
