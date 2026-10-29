package com.soruhavuzu.identity.internal.web

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import java.time.Clock

/** Sağlayıcıdan bağımsız access token doğrulaması: süre + issuer + audience + (varsa) istemci izin listesi (ADR 0024). */
object AccessTokenValidators {
    const val CLIENT_ID = "client_id"

    fun create(
        token: IdentityProperties.Token,
        clock: Clock,
    ): OAuth2TokenValidator<Jwt> {
        val validators =
            mutableListOf<OAuth2TokenValidator<Jwt>>(
                JwtTimestampValidator().apply { setClock(clock) },
                JwtIssuerValidator(token.issuerUri),
                JwtClaimValidator<Any>(JwtClaimNames.AUD) { aud ->
                    (aud as? Collection<*>)?.any { it in token.audiences } == true
                },
            )
        val allowedClients = token.allowedClientIds
        if (allowedClients.isNotEmpty()) {
            validators += JwtClaimValidator<String>(CLIENT_ID) { it in allowedClients }
        }
        return DelegatingOAuth2TokenValidator(validators)
    }
}
