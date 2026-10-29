package com.soruhavuzu.identity

import com.soruhavuzu.identity.internal.web.LocalProfileGuard
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class LocalProfileGuardTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(LocalProfileGuard::class.java)

    @Test
    fun `sahte JWT üretici staging veya prod ile birlikte açılamaz`() {
        runner.withInitializer { it.environment.setActiveProfiles("prod", "local") }.run { context ->
            (context.startupFailure != null) shouldBe true
            context.startupFailure?.stackTraceToString().orEmpty() shouldContain "Sahte JWT"
        }
    }

    @Test
    fun `yalnızca local profilde sorun çıkarmaz`() {
        runner.withInitializer { it.environment.setActiveProfiles("local") }.run { context ->
            context.startupFailure shouldBe null
        }
    }
}
