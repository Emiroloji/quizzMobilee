package archfixture.arch.alpha.internal.web

import archfixture.arch.alpha.internal.infrastructure.BadRepository
import org.springframework.beans.factory.annotation.Autowired

/** KASITLI İHLAL: web katmanı infrastructure'a bağımlı ve field injection kullanıyor. */
class BadController {
    @Autowired
    lateinit var repository: BadRepository
}
