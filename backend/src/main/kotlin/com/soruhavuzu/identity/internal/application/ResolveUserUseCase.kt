package com.soruhavuzu.identity.internal.application

import com.soruhavuzu.events.identity.UserRegistered
import com.soruhavuzu.identity.internal.domain.Handle
import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.IdGenerator
import com.soruhavuzu.shared.RandomSource
import com.soruhavuzu.shared.UserId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * IdP kimliğini iç kullanıcıya çözer; yoksa oluşturur ve [UserRegistered] yayınlar (mimari.md §14, ADR 0018).
 * Eşzamanlı ilk istekler birincil anahtar çakışmasıyla tek kullanıcıya iner.
 */
@Service
class ResolveUserUseCase(
    private val users: UserRepository,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
    private val ids: IdGenerator,
    private val random: RandomSource,
    private val clock: Clock,
) {
    fun resolve(identity: ExternalIdentity): User = users.findByIdentity(identity) ?: registerWithRetry(identity)

    private fun registerWithRetry(identity: ExternalIdentity): User {
        var lastConflict: DuplicateKeyException? = null
        repeat(MAX_ATTEMPTS) {
            try {
                return register(identity)
            } catch (e: DuplicateKeyException) {
                // Ya başka bir istek aynı kişiyi az önce oluşturdu ya da üretilen takma ad çakıştı
                lastConflict = e
                users.findByIdentity(identity)?.let { return it }
            }
        }
        throw IllegalStateException("Kullanıcı $MAX_ATTEMPTS denemede oluşturulamadı", lastConflict)
    }

    private fun register(identity: ExternalIdentity): User =
        checkNotNull(
            transactions.execute {
                val user =
                    User.register(
                        id = UserId(ids.newId()),
                        handle = Handle.generated(random.nextBytes(Handle.GENERATED_SUFFIX_LENGTH)),
                        now = clock.instant(),
                    )
                users.insert(user, identity)
                events.publishEvent(UserRegistered(user.id))
                user
            },
        )

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
