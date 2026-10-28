package com.soruhavuzu.shared.config

import com.soruhavuzu.shared.IdGenerator
import com.soruhavuzu.shared.RandomSource
import com.soruhavuzu.shared.UuidV7Generator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class SharedConfiguration {
    /** Zaman yalnızca bu Clock'tan alınır; Instant.now() doğrudan çağrılmaz (kurallar.md §3.1). */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun randomSource(): RandomSource = SecureRandomSource(SecureRandom())

    @Bean
    fun idGenerator(
        clock: Clock,
        randomSource: RandomSource,
    ): IdGenerator = UuidV7Generator(clock, randomSource)
}

private class SecureRandomSource(
    private val random: SecureRandom,
) : RandomSource {
    override fun nextDouble(): Double = random.nextDouble()

    override fun nextBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}
