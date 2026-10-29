package com.soruhavuzu.shared.logging

import com.soruhavuzu.shared.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Kimlik doğrulamasından sonra çalışır (güvenlik zincirine identity modülü ekler) ve isteğin loglarına
 * kullanıcının özetini koyar. Bean değildir; servlet zincirine ikinci kez kaydedilmez.
 */
class UserIdLoggingFilter(
    private val hasher: UserIdHasher,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        if (user == null) {
            filterChain.doFilter(request, response)
            return
        }
        MDC.put(JsonLogFormatter.USER_ID_HASH_MDC, hasher.hash(user.userId))
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(JsonLogFormatter.USER_ID_HASH_MDC)
        }
    }
}
