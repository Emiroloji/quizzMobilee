package archfixture.arch.alpha.internal.application

import archfixture.arch.alpha.internal.infrastructure.BadRepository
import archfixture.arch.events.beta.BetaHappened
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.RestController

/**
 * KASITLI İHLALLER: application katmanında JDBC (A3), Jwt (A10), @PreAuthorize ve @RestController;
 * infrastructure'a doğrudan bağımlılık; başka modülün olayını oluşturma (ADR 0013).
 */
@RestController
class BadService(
    val jdbc: JdbcTemplate,
    val repository: BadRepository,
) {
    @PreAuthorize("hasRole('ADMIN')")
    fun subject(jwt: Jwt): String? = jwt.subject

    fun publishForeignEvent(): BetaHappened = BetaHappened("x")
}
