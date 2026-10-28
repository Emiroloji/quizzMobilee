package com.soruhavuzu.identity

import com.soruhavuzu.identity.internal.domain.Handle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

class HandleTest {
    @Test
    fun `üretilen takma ad kalıba uyar`() {
        val bytes = ByteArray(Handle.GENERATED_SUFFIX_LENGTH) { (it * 37 - 128).toByte() }
        Handle.generated(bytes).value shouldMatch Regex("^kullanici_[a-z0-9]{8}$")
    }

    @Test
    fun `kalıba uymayan takma ad reddedilir`() {
        listOf("ab", "Büyük_Harf", "boşluk var", "a".repeat(31)).forEach { invalid ->
            shouldThrow<IllegalArgumentException> { Handle(invalid) }
        }
    }
}
