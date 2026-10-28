package com.soruhavuzu.identity.internal.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Her uç nokta varsayılan olarak kimlik doğrulama ister; anonim olanlar burada açıkça listelenir (kurallar.md §9).
 * Güvenlik hataları da problem+json olarak döner (ProblemHandler).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        userAuthenticationConverter: UserAuthenticationConverter,
        problemSecurityHandler: ProblemSecurityHandler,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // Yalnızca local/test profilinde var; diğer ortamlarda 404 döner
                    .requestMatchers(HttpMethod.POST, "/v1/dev/tokens")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer {
                it
                    .jwt { jwt -> jwt.jwtAuthenticationConverter(userAuthenticationConverter) }
                    .authenticationEntryPoint(problemSecurityHandler)
                    .accessDeniedHandler(problemSecurityHandler)
            }.exceptionHandling {
                it.authenticationEntryPoint(problemSecurityHandler).accessDeniedHandler(problemSecurityHandler)
            }.build()

    @Bean
    fun problemSecurityHandler(
        @Qualifier("handlerExceptionResolver") resolver: HandlerExceptionResolver,
    ): ProblemSecurityHandler = ProblemSecurityHandler(resolver)
}

/** Güvenlik filtresindeki hataları MVC hata işleyicisine (ProblemHandler) devreder. */
class ProblemSecurityHandler(
    private val resolver: HandlerExceptionResolver,
) : AuthenticationEntryPoint,
    AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        resolver.resolveException(request, response, null, authException)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        resolver.resolveException(request, response, null, accessDeniedException)
    }
}
