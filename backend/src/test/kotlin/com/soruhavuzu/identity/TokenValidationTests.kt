package com.soruhavuzu.identity

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.soruhavuzu.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.time.Instant

/** ADR 0018: yalnızca doğru issuer'lı, süresi geçmemiş, izinli istemcinin ACCESS token'ı kabul edilir. */
class TokenValidationTests : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var encoder: JwtEncoder

    private fun token(
        encoder: JwtEncoder = this.encoder,
        issuer: String = ISSUER,
        tokenUse: String = "access",
        clientId: String = "test-mobile",
        expiresAt: Instant = Instant.now().plus(Duration.ofMinutes(10)),
    ): String {
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(issuer)
                .subject("dogrulama-${System.nanoTime()}")
                .issuedAt(expiresAt.minus(Duration.ofMinutes(20)))
                .expiresAt(expiresAt)
                .claim("client_id", clientId)
                .claim("token_use", tokenUse)
                .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).tokenValue
    }

    private fun meStatus(token: String): Int =
        mvc
            .get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andReturn()
            .response.status

    @Test
    fun `geçerli access token kabul edilir`() {
        meStatus(token()) shouldBe OK
    }

    @Test
    fun `süresi dolmuş token reddedilir`() {
        meStatus(token(expiresAt = Instant.now().minus(Duration.ofMinutes(5)))) shouldBe UNAUTHORIZED
    }

    @Test
    fun `id token reddedilir, yalnızca access token kabul edilir`() {
        meStatus(token(tokenUse = "id")) shouldBe UNAUTHORIZED
    }

    @Test
    fun `başka issuer'ın token'ı reddedilir`() {
        meStatus(token(issuer = "https://kotu.example")) shouldBe UNAUTHORIZED
    }

    @Test
    fun `başka anahtarla imzalanmış token reddedilir`() {
        val foreignKey = RSAKeyGenerator(2048).keyID("yabanci").generate()
        val foreignEncoder = NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(foreignKey)))
        meStatus(token(encoder = foreignEncoder)) shouldBe UNAUTHORIZED
    }

    private companion object {
        const val ISSUER = "https://auth.test.soruhavuzu.invalid"
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
