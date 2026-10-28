package archfixture.arch.alpha.internal.domain

import archfixture.arch.alpha.internal.application.BadService
import org.springframework.stereotype.Component

/** KASITLI İHLAL: domain Spring'e (A8) ve application katmanına bağımlı. */
@Component
class BadDomain(
    val service: BadService,
)
