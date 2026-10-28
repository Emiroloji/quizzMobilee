package com.soruhavuzu.shared

import java.nio.ByteBuffer
import java.time.Clock
import java.util.UUID

/** Uygulamada UUIDv7 üretir (ADR 0011). */
interface IdGenerator {
    fun newId(): UUID
}

/**
 * RFC 9562 UUIDv7: 48 bit Unix ms zaman damgası + sürüm/varyant + 74 bit rastgele.
 * Aynı milisaniyede üretilen ID'ler arasında sıralama garanti edilmez; gerek de yok.
 */
class UuidV7Generator(
    private val clock: Clock,
    private val random: RandomSource,
) : IdGenerator {
    override fun newId(): UUID {
        val millis = clock.millis()
        val rand = random.nextBytes(RANDOM_BYTES)
        val bytes = ByteBuffer.allocate(UUID_BYTES)
            .putShort((millis ushr 32).toShort())
            .putInt(millis.toInt())
            .put(rand)
            .array()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // sürüm 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // varyant 10xx
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    private companion object {
        const val UUID_BYTES = 16
        const val RANDOM_BYTES = 10
    }
}
