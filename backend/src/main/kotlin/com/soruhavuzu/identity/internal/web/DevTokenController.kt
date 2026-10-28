package com.soruhavuzu.identity.internal.web

import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Yalnızca local/test: Cognito'nun yerine geçen sahte access token üretici. API sözleşmesinde yer almaz. */
@Hidden
@Profile("local | test")
@RestController
@RequestMapping("/v1/dev/tokens")
class DevTokenController(
    private val encoder: JwtEncoder,
    private val properties: IdentityProperties,
    private val clock: Clock,
) {
    @PostMapping
    fun issue(
        @Valid @RequestBody request: DevTokenRequest,
    ): DevTokenResponse {
        val now = clock.instant()
        val expiresAt = now.plus(TOKEN_LIFETIME)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.token.issuerUri)
                .subject(request.subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AccessTokenValidators.CLIENT_ID, request.clientId ?: properties.token.clientIds.first())
                .claim(AccessTokenValidators.TOKEN_USE, AccessTokenValidators.ACCESS)
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return DevTokenResponse(token, expiresAt)
    }

    private companion object {
        val TOKEN_LIFETIME: Duration = Duration.ofHours(12)
    }
}

data class DevTokenRequest(
    @field:NotBlank @field:Size(max = 128) val subject: String,
    val clientId: String? = null,
)

data class DevTokenResponse(
    val accessToken: String,
    val expiresAt: Instant,
)
