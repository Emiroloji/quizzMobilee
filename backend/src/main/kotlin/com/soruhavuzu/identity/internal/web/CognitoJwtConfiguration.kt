package com.soruhavuzu.identity.internal.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Clock

/** staging/prod: Cognito JWKS ile imza doğrulaması (anahtarlar ilk istekte çekilir ve önbelleklenir). */
@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
class CognitoJwtConfiguration {
    @Bean
    fun jwtDecoder(
        properties: IdentityProperties,
        clock: Clock,
    ): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri("${properties.token.issuerUri}/.well-known/jwks.json")
            .build()
            .apply { setJwtValidator(AccessTokenValidators.create(properties.token, clock)) }
}
