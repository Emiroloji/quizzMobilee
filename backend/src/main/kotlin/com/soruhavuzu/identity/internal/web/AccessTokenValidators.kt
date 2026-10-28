package com.soruhavuzu.identity.internal.web

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import java.time.Clock

/** Cognito access token doğrulaması: issuer + süre + `client_id` + `token_use=access` (ADR 0018). */
object AccessTokenValidators {
    const val CLIENT_ID = "client_id"
    const val TOKEN_USE = "token_use"
    const val ACCESS = "access"

    fun create(
        token: IdentityProperties.Token,
        clock: Clock,
    ): OAuth2TokenValidator<Jwt> =
        DelegatingOAuth2TokenValidator(
            JwtTimestampValidator().apply { setClock(clock) },
            JwtIssuerValidator(token.issuerUri),
            JwtClaimValidator<String>(CLIENT_ID) { it in token.clientIds },
            JwtClaimValidator<String>(TOKEN_USE) { it == ACCESS },
        )
}
