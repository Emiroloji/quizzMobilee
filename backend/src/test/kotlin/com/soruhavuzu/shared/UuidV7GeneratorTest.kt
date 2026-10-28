package com.soruhavuzu.shared

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UuidV7GeneratorTest {
    private val fixedInstant = Instant.parse("2026-10-28T12:00:00.123Z")
    private val generator = UuidV7Generator(Clock.fixed(fixedInstant, ZoneOffset.UTC), FixedRandomSource(0xAB.toByte()))

    @Test
    fun `üretilen id sürüm 7 ve RFC varyantındadır`() {
        val id = generator.newId()
        id.version() shouldBe 7
        id.variant() shouldBe 2
    }

    @Test
    fun `ilk 48 bit Clock'tan gelen milisaniyedir`() {
        val id = generator.newId()
        (id.mostSignificantBits ushr 16) shouldBe fixedInstant.toEpochMilli()
    }

    @Test
    fun `farklı zamanlarda üretilen id'ler zamana göre sıralanır`() {
        val earlier = UuidV7Generator(Clock.fixed(fixedInstant, ZoneOffset.UTC), FixedRandomSource(0xFF.toByte())).newId()
        val later = UuidV7Generator(Clock.fixed(fixedInstant.plusMillis(1), ZoneOffset.UTC), FixedRandomSource(0x00)).newId()
        (earlier.toString() < later.toString()) shouldBe true
    }
}

private class FixedRandomSource(private val fill: Byte) : RandomSource {
    override fun nextDouble(): Double = 0.5

    override fun nextBytes(size: Int): ByteArray = ByteArray(size) { fill }
}
