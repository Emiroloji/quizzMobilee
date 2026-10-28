package com.soruhavuzu.shared

/** Rastgelelik portu; testte sabit seed'li implementasyonla değiştirilir (kurallar.md §3.1). */
interface RandomSource {
    /** [0, 1) aralığında. */
    fun nextDouble(): Double

    fun nextBytes(size: Int): ByteArray
}
