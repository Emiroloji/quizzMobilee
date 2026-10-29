package com.soruhavuzu.identity.internal.application

import com.soruhavuzu.events.identity.UserRegistered
import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.logging.UserIdHasher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException

/**
 * IdP kimliğini iç kullanıcıya çözer; yoksa oluşturur ve [UserRegistered] yayınlar (mimari.md §14, ADR 0018).
 * Eşzamanlı ilk istekler birincil anahtar çakışmasıyla tek kullanıcıya iner.
 */
@Service
class ResolveUserUseCase(
    private val users: UserRepository,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
    private val newUsers: NewUserFactory,
    private val hasher: UserIdHasher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(identity: ExternalIdentity): User = users.findByIdentity(identity) ?: registerWithRetry(identity)

    private fun registerWithRetry(identity: ExternalIdentity): User {
        var lastSqlState: String? = null
        repeat(MAX_ATTEMPTS) {
            try {
                return register(identity)
            } catch (e: DuplicateKeyException) {
                // Ya başka bir istek aynı kişiyi az önce oluşturdu ya da üretilen takma ad çakıştı
                lastSqlState = (e.cause as? SQLException)?.sqlState
                users.findByIdentity(identity)?.let { return it }
            }
        }
        // Veritabanı hata mesajı anahtar değerlerini (IdP sub) içerir; log'a taşınmasın diye yalnızca SQLState
        error("Kullanıcı $MAX_ATTEMPTS denemede oluşturulamadı (SQLState=$lastSqlState)")
    }

    private fun register(identity: ExternalIdentity): User =
        checkNotNull(
            transactions.execute {
                val user = newUsers.create()
                users.insert(user, identity)
                events.publishEvent(UserRegistered(user.id))
                log
                    .atInfo()
                    .addKeyValue("event", "user_registered")
                    .addKeyValue("user", hasher.hash(user.id))
                    .log("Yeni kullanıcı oluşturuldu")
                user
            },
        )

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
