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

    private fun jwt(
        clientId: String = "mobil",
        audience: List<String>? = listOf("https://api.example"),
    ) = Jwt
        .withTokenValue("t")
        .header("alg", "ES384")
        .issuer("https://auth.example")
        .claim("client_id", clientId)
        .apply { audience?.let { claim("aud", it) } }
        .issuedAt(now.minus(Duration.ofMinutes(1)))
        .expiresAt(now.plus(Duration.ofMinutes(10)))
        .build()

    private fun hasErrors(audience: List<String>?) =
        AccessTokenValidators.create(properties(emptySet()), clock).validate(jwt(audience = audience)).hasErrors()

    @Test
    fun `aud claim'i olmayan token doğrulayıcıda hata üretir`() {
        hasErrors(null) shouldBe true
    }

    @Test
    fun `boş aud listesi olan token doğrulayıcıda hata üretir`() {
        hasErrors(emptyList()) shouldBe true
    }

    @Test
    fun `aud listesi izinli audience ile eşleşmeyen token doğrulayıcıda hata üretir`() {
        hasErrors(listOf("https://baska-api.example")) shouldBe true
    }

    @Test
    fun `aud listesi izinli audience içeren token doğrulayıcıda hata üretmez`() {
        hasErrors(listOf("https://baska-api.example", "https://api.example")) shouldBe false
    }

    @Test
    fun `boş OIDC_CLIENT_IDS değeri geçerli token'ı reddetmez`() {
        AccessTokenValidators.create(properties(setOf("")), clock).validate(jwt()).hasErrors() shouldBe false
    }

    @Test
    fun `istemci izin listesi doluyken listede olmayan istemci reddedilir`() {
        AccessTokenValidators.create(properties(setOf("", "web")), clock).validate(jwt()).hasErrors() shouldBe true
    }
}
