package com.soruhavuzu.shared.config

import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Duration
import org.springframework.web.cors.CorsConfiguration as SpringCorsConfiguration

/** Tarayıcı istemcileri (admin paneli) için izinli origin'ler; liste dışı origin'e CORS yanıtı verilmez. */
@Validated
@ConfigurationProperties("soruhavuzu.web")
data class WebProperties(
    @field:NotNull val allowedOrigins: List<String> = emptyList(),
)

@Configuration(proxyBeanMethods = false)
class CorsConfiguration {
    @Bean
    fun corsConfigurationSource(properties: WebProperties): CorsConfigurationSource {
        val cors =
            SpringCorsConfiguration().apply {
                allowedOrigins = properties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key")
                allowCredentials = false // kimlik çerezle değil Authorization başlığıyla taşınır
                maxAge = Duration.ofHours(1).seconds
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/v1/**", cors) }
    }
}
