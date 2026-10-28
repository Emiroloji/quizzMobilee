package com.soruhavuzu.identity.internal.web

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock

/**
 * local/test: açılışta üretilen RSA anahtarıyla sahte JWT üretici ve ona bağlı decoder (fazlar.md Faz 0).
 * Doğrulama kuralları Cognito ile aynıdır; yalnızca imza anahtarı farklıdır.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local | test")
class LocalJwtConfiguration {
    @Bean
    fun localSigningKey(): RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID("soruhavuzu-local").generate()

    @Bean
    fun jwtDecoder(
        localSigningKey: RSAKey,
        properties: IdentityProperties,
        clock: Clock,
    ): JwtDecoder =
        NimbusJwtDecoder
            .withPublicKey(localSigningKey.toRSAPublicKey())
            .build()
            .apply { setJwtValidator(AccessTokenValidators.create(properties.token, clock)) }

    @Bean
    fun localJwtEncoder(localSigningKey: RSAKey): JwtEncoder = NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(localSigningKey)))

    private companion object {
        const val RSA_KEY_SIZE = 2048
    }
}
