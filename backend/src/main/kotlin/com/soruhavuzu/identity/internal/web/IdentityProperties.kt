package com.soruhavuzu.identity.internal.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("soruhavuzu.identity")
data class IdentityProperties(
    @field:Valid val token: Token,
) {
    data class Token(
        /** auth_identities.provider değeri: prod/staging'de `cognito`, local/test'te `local`. */
        @field:NotBlank val provider: String,
        @field:NotBlank val issuerUri: String,
        /** Kabul edilen uygulama istemcileri (mobil, admin); access token'daki `client_id` (ADR 0018). */
        @field:NotEmpty val clientIds: Set<String>,
    )
}
