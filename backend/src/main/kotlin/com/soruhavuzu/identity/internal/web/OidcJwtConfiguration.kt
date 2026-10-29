package com.soruhavuzu.identity.internal.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Clock

/** staging/prod: OIDC sağlayıcısının (Logto) JWKS'iyle imza doğrulaması; anahtarlar ilk istekte çekilir. */
@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
class OidcJwtConfiguration {
    @Bean
    fun jwtDecoder(
        properties: IdentityProperties,
        clock: Clock,
    ): JwtDecoder {
        val token = properties.token
        val jwkSetUri = requireNotNull(token.jwkSetUri) { "soruhavuzu.identity.token.jwk-set-uri zorunlu" }
        return NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri)
            .jwsAlgorithms { algorithms ->
                token.jwsAlgorithms.forEach {
                    algorithms.add(
                        requireNotNull(SignatureAlgorithm.from(it)) { "Desteklenmeyen JWS algoritması: $it" },
                    )
                }
            }.build()
            .apply { setJwtValidator(AccessTokenValidators.create(token, clock)) }
    }
}
