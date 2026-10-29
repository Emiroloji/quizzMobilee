package archfixture.jwt.identity

import org.springframework.security.oauth2.jwt.Jwt

/** İzinli: identity modülü Jwt görebilir. */
class TokenReader {
    fun subject(jwt: Jwt): String? = jwt.subject
}
