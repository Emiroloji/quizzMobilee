package com.soruhavuzu.identity

import com.soruhavuzu.identity.internal.application.ExternalIdentity
import com.soruhavuzu.identity.internal.application.NewUserFactory
import com.soruhavuzu.identity.internal.application.ResolveUserUseCase
import com.soruhavuzu.identity.internal.application.UserRepository
import com.soruhavuzu.shared.RandomSource
import com.soruhavuzu.shared.UuidV7Generator
import com.soruhavuzu.shared.logging.LoggingProperties
import com.soruhavuzu.shared.logging.UserIdHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ResolveUserUseCaseTest {
    private val subject = "gizli-idp-sub-123"

    @Test
    fun `kayıt sürekli çakışırsa fırlatılan hata zinciri IdP sub değerini taşımaz`() {
        val users = mockk<UserRepository>()
        every { users.findByIdentity(any()) } returns null
        every { users.insert(any(), any()) } throws
            DuplicateKeyException("ERROR: duplicate key; Detail: Key (provider, subject)=(local, $subject) already exists.")
        val transactions = mockk<TransactionTemplate>()
        every { transactions.execute(any<TransactionCallback<Any>>()) } answers {
            firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true))
        }
        val clock = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC)
        val random =
            object : RandomSource {
                override fun nextDouble() = 0.5

                override fun nextBytes(size: Int) = ByteArray(size) { it.toByte() }
            }
        val useCase =
            ResolveUserUseCase(
                users = users,
                events = mockk(relaxed = true),
                transactions = transactions,
                newUsers = NewUserFactory(UuidV7Generator(clock, random), random, clock),
                hasher = UserIdHasher(LoggingProperties("anahtar")),
            )

        val error = shouldThrow<IllegalStateException> { useCase.resolve(ExternalIdentity("local", subject)) }

        val chain = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        chain shouldNotContain subject
    }
}
