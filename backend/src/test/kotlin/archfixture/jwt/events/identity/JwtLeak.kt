package archfixture.jwt.events.identity

import org.springframework.security.oauth2.jwt.Jwt

/** KASITLI İHLAL (A10): adında "identity" geçen ama identity modülü olmayan paket Jwt taşıyor. */
data class JwtLeak(
    val jwt: Jwt,
)
