package com.soruhavuzu.shared.logging

import com.soruhavuzu.shared.UserId
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Validated
@ConfigurationProperties("soruhavuzu.logging")
data class LoggingProperties(
    /** Log'daki userId için HMAC anahtarı; staging/prod'da Secrets Manager'dan gelir (kurallar.md §9). */
    @field:NotBlank val userIdHashKey: String,
)

/**
 * Loglarda kullanıcı kimliği yerine anahtarlı özet (HMAC-SHA256, ilk 16 hex) yazılır: aynı kullanıcının
 * kayıtları ilişkilendirilebilir, ama anahtar olmadan log'dan kimliğe gidilemez (mimari.md §18).
 */
@Component
class UserIdHasher(
    properties: LoggingProperties,
) {
    private val key = SecretKeySpec(properties.userIdHashKey.toByteArray(Charsets.UTF_8), ALGORITHM)

    fun hash(userId: UserId): String =
        Mac
            .getInstance(ALGORITHM)
            .apply { init(key) }
            .doFinal(userId.value.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(HASH_LENGTH)

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val HASH_LENGTH = 16
    }
}
