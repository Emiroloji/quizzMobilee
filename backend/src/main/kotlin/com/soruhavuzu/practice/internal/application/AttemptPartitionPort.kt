package com.soruhavuzu.practice.internal.application

import java.time.LocalDate

interface AttemptPartitionPort {
    /** [from] ayından başlayarak [months] aylık partition'ı garanti eder; yeni oluşturulan sayıyı döner. */
    fun ensurePartitions(from: LocalDate, months: Int): Int
}
