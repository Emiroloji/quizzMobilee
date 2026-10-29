package com.soruhavuzu.shared.logging

import com.soruhavuzu.shared.UserId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.util.UUID

class UserIdHasherTest {
    private val user = UserId(UUID.fromString("01a182f9-1724-717f-9eb9-79a6c076b649"))

    @Test
    fun `aynı anahtarla aynı kullanıcı her zaman aynı özeti verir, kimliği açığa vermez`() {
        val hasher = UserIdHasher(LoggingProperties("anahtar-1"))
        val hash = hasher.hash(user)
        hash shouldMatch Regex("^[0-9a-f]{16}$")
        hash shouldBe hasher.hash(user)
        hash shouldNotContain "01a182f9"
    }

    @Test
    fun `farklı anahtar farklı özet üretir`() {
        UserIdHasher(LoggingProperties("anahtar-1")).hash(user) shouldNotBe UserIdHasher(LoggingProperties("anahtar-2")).hash(user)
    }
}
