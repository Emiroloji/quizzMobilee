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
        val bytes =
            ByteBuffer
                .allocate(UUID_BYTES)
                .putShort((millis ushr TIMESTAMP_HIGH_SHIFT).toShort())
                .putInt(millis.toInt())
                .put(rand)
                .array()
        bytes[VERSION_BYTE] = ((bytes[VERSION_BYTE].toInt() and LOW_NIBBLE) or VERSION_7).toByte()
        bytes[VARIANT_BYTE] = ((bytes[VARIANT_BYTE].toInt() and LOW_SIX_BITS) or RFC_VARIANT).toByte()
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    private companion object {
        const val UUID_BYTES = 16
        const val RANDOM_BYTES = 10
        const val TIMESTAMP_HIGH_SHIFT = 32
        const val VERSION_BYTE = 6
        const val VARIANT_BYTE = 8
        const val LOW_NIBBLE = 0x0F
        const val LOW_SIX_BITS = 0x3F
        const val VERSION_7 = 0x70
        const val RFC_VARIANT = 0x80 // 10xx
    }
}
