package com.soruhavuzu

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTests {
    private val modules = ApplicationModules.of(SoruhavuzuApiApplication::class.java)

    @Test
    fun `modül sınırları ve izinli bağımlılıklar ihlal edilmez`() {
        modules.verify()
    }

    @Test
    fun `beklenen tüm modüller tanımlıdır`() {
        val names = modules.map { it.identifier.toString() }.toSet()
        check(names == EXPECTED_MODULES) { "Beklenen: $EXPECTED_MODULES, bulunan: $names" }
    }

    @Test
    fun `modül dokümantasyonu üretilir`() {
        Documenter(modules).writeDocumentation()
    }

    private companion object {
        val EXPECTED_MODULES = setOf(
            "shared", "events", "identity", "taxonomy", "media", "content", "practice",
            "calibration", "moderation", "reputation", "stats", "notification",
        )
    }
}
