package com.soruhavuzu.identity.internal.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Clock
import java.time.Duration
import java.time.Instant

class AccessTokenValidatorsTests {
    private val now: Instant = Instant.parse("2026-10-29T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, java.time.ZoneOffset.UTC)

    private fun properties(clientIds: Set<String>) =
        IdentityProperties.Token(
            provider = "logto",
            issuerUri = "https://auth.example",
            audiences = setOf("https://api.example"),
            clientIds = clientIds,
        )

    private fun jwt(clientId: String = "mobil") =
        Jwt
            .withTokenValue("t")
            .header("alg", "ES384")
            .issuer("https://auth.example")
            .audience(listOf("https://api.example"))
            .claim("client_id", clientId)
            .issuedAt(now.minus(Duration.ofMinutes(1)))
            .expiresAt(now.plus(Duration.ofMinutes(10)))
            .build()

    @Test
    fun `boş OIDC_CLIENT_IDS değeri geçerli token'ı reddetmez`() {
        AccessTokenValidators.create(properties(setOf("")), clock).validate(jwt()).hasErrors() shouldBe false
    }

    @Test
    fun `istemci izin listesi doluyken listede olmayan istemci reddedilir`() {
        AccessTokenValidators.create(properties(setOf("", "web")), clock).validate(jwt()).hasErrors() shouldBe true
    }
}
