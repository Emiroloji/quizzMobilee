package com.soruhavuzu.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.core.Violations

class ArchitectureTests {
    private val production =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.soruhavuzu")

    private val fixture = ClassFileImporter().importPackages(FIXTURE)

    @TestFactory
    fun `üretim kodu tüm mimari kurallara uyar`(): List<DynamicTest> =
        ArchitectureRules.all("com.soruhavuzu").map { rule -> dynamicTest(rule.description) { rule.check(production) } }

    @TestFactory
    fun `her mimari kural kasıtlı ihlal içeren fixture'da kırılır`(): List<DynamicTest> =
        fixtureRules().map { rule ->
            dynamicTest(rule.description) { shouldThrow<AssertionError> { rule.check(fixture) } }
        }

    // Fixture test kaynaklarında olduğu için Modulith'in varsayılan "testleri dahil etme" seçeneği kapatılır
    @Test
    fun `başka modülün internal paketine erişim Modulith doğrulamasını kırar`() {
        shouldThrow<Violations> { ApplicationModules.of("archfixture.modulith", ImportOption { true }).verify() }
            .message shouldContain "AlphaSecret"
    }

    @Test
    fun `Jwt kuralı adında identity geçen başka paketi identity modülü saymaz`() {
        val classes = ClassFileImporter().importPackages("archfixture.jwt")
        shouldThrow<AssertionError> { ArchitectureRules.jwtOnlyInIdentity("archfixture.jwt").check(classes) }
            .message shouldContain "JwtLeak"
    }

    private fun fixtureRules(): List<ArchRule> =
        listOf(
            ArchitectureRules.domainIsFrameworkFree,
            ArchitectureRules.jdbcOnlyInInfrastructure,
            ArchitectureRules.jwtOnlyInIdentity(FIXTURE),
            ArchitectureRules.preAuthorizeOnlyInWeb,
            ArchitectureRules.controllersInWeb,
            ArchitectureRules.noFieldInjection,
        ) + ArchitectureRules.layersPointInward +
            ArchitectureRules.appendOnlyRepositories +
            ArchitectureRules.eventsOwnedByPublisher(FIXTURE, listOf("beta"))

    private companion object {
        const val FIXTURE = "archfixture.arch"
    }
}
