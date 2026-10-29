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
        /** auth_identities.provider değeri: staging/prod'da `logto`, local/test'te `local`. */
        @field:NotBlank val provider: String,
        @field:NotBlank val issuerUri: String,
        /** JWKS adresi (Logto: <issuer>/jwks). local/test'te kullanılmaz. */
        val jwkSetUri: String? = null,
        /** Kabul edilen API resource indicator'ları; access token'daki `aud` bunlardan birini içermeli (ADR 0024). */
        @field:NotEmpty val audiences: Set<@NotBlank String>,
        /** Boş değilse yalnızca bu istemcilerin token'ı kabul edilir (`client_id`). Boş girdiler yok sayılır. */
        val clientIds: Set<String> = emptySet(),
        @field:NotEmpty val jwsAlgorithms: Set<String> = setOf("ES384"),
    ) {
        /** Boş girdiler (ör. `OIDC_CLIENT_IDS=` -> `[""]`) elenmiş izin listesi. */
        val allowedClientIds: Set<String> get() = clientIds.filterTo(linkedSetOf()) { it.isNotBlank() }
    }
}
