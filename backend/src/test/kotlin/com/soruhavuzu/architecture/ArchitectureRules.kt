package com.soruhavuzu.architecture

import com.soruhavuzu.shared.AppendOnly
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaConstructorCall
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.GeneralCodingRules
import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.RestController

/**
 * kurallar.md §2'deki ArchUnit ile denetlenen kurallar. Kurallar paket kalıplarıyla yazılır;
 * böylece hem üretim koduna hem de kasıtlı ihlal içeren fixture'lara uygulanabilir.
 */
object ArchitectureRules {
    /** A8: domain paketi Spring, JDBC, Jackson veya web sınıfı import etmez. */
    val domainIsFrameworkFree: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..internal.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "java.sql..",
                "javax.sql..",
                "tools.jackson..",
                "com.fasterxml.jackson..",
                "jakarta.servlet..",
                "jakarta.persistence..",
            ).allowEmptyShould(true)
            .because("domain hiçbir altyapıya bağımlı değildir (kurallar.md A8)")

    /** mimari.md §5.3 katman kuralları. */
    val layersPointInward: List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("..internal.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..internal.application..",
                    "..internal.infrastructure..",
                    "..internal.web..",
                ).allowEmptyShould(true)
                .because("domain hiçbir katmana bağımlı değildir (mimari.md §5.3)"),
            noClasses()
                .that()
                .resideInAPackage("..internal.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..internal.infrastructure..", "..internal.web..")
                .allowEmptyShould(true)
                .because("application yalnızca domain'e bağımlıdır; altyapıyı port üzerinden kullanır (mimari.md §5.3)"),
            noClasses()
                .that()
                .resideInAPackage("..internal.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal.infrastructure..")
                .allowEmptyShould(true)
                .because("web yalnızca application'ı çağırır (mimari.md §5.3)"),
        )

    /** A3: SQL yalnızca infrastructure katmanında çalıştırılır (şema adı kontrolü SourceLint'te). */
    val jdbcOnlyInInfrastructure: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackages("..internal.infrastructure..", "com.soruhavuzu.shared.config..")
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(
                JdbcClient::class.java,
                JdbcTemplate::class.java,
                NamedParameterJdbcTemplate::class.java,
            ).because("veritabanına yalnızca infrastructure katmanı erişir (kurallar.md A3, mimari.md §5.3)")

    /** A10: IdP token'ı (ve dolayısıyla `sub`) yalnızca identity modülünde görünür. */
    fun jwtOnlyInIdentity(basePackage: String): ArchRule =
        noClasses()
            .that()
            // "..identity.." kalıbı events.identity gibi paketleri de kapsardı; yalnızca modülün kendi paketi
            .resideOutsideOfPackage("$basePackage.identity..")
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(Jwt::class.java)
            .because("uygulama içinde her yerde iç UserId kullanılır (kurallar.md A10)")

    /** mimari.md §14: rol kontrolü yalnızca web katmanında. */
    val preAuthorizeOnlyInWeb: ArchRule =
        methods()
            .that()
            .areAnnotatedWith(PreAuthorize::class.java)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..internal.web..")
            .allowEmptyShould(true)
            .because("@PreAuthorize yalnızca web katmanında kullanılır (mimari.md §14)")

    /** kurallar.md §5: controller'lar web katmanında. */
    val controllersInWeb: ArchRule =
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage("..internal.web..")
            .allowEmptyShould(true)
            .because("request/response modelleri ve controller'lar web katmanındadır (kurallar.md §5)")

    /** kurallar.md §3.1: yalnızca constructor injection. */
    val noFieldInjection: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION

    /** A5: append-only repository'ler güncelleme/silme sunmaz. */
    val appendOnlyRepositories: List<ArchRule> =
        listOf(
            classes()
                .that()
                .areAnnotatedWith(AppendOnly::class.java)
                .should()
                .notBeAssignableTo(CrudRepository::class.java)
                .allowEmptyShould(true)
                .because("CrudRepository save/delete sunar; append-only tablolar için yasak (kurallar.md A5)"),
            noMethods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(AppendOnly::class.java)
                .should()
                .haveNameMatching("(update|delete|remove|upsert).*")
                .allowEmptyShould(true)
                .because("append-only tablolara UPDATE/DELETE yazılmaz (kurallar.md A5)"),
        )

    /** ADR 0013: `events.<modul>` olayını yalnızca sahibi olan modül oluşturur (dolayısıyla yayınlar). */
    fun eventsOwnedByPublisher(
        basePackage: String,
        modules: List<String>,
    ): List<ArchRule> =
        modules.map { module ->
            val ownerEvents = "$basePackage.events.$module"
            noClasses()
                .that()
                .resideOutsideOfPackages("$basePackage.$module..", "$basePackage.events..")
                .should()
                .callConstructorWhere(
                    object : DescribedPredicate<JavaConstructorCall>("$ownerEvents olayını oluşturur") {
                        override fun test(call: JavaConstructorCall): Boolean = call.targetOwner.packageName.startsWith(ownerEvents)
                    },
                ).allowEmptyShould(true)
                .because("$module olaylarını yalnızca $module modülü yayınlar (ADR 0013)")
        }

    val businessModules =
        listOf(
            "identity",
            "taxonomy",
            "media",
            "content",
            "practice",
            "calibration",
            "moderation",
            "reputation",
            "stats",
            "notification",
        )

    fun all(basePackage: String): List<ArchRule> =
        listOf(
            domainIsFrameworkFree,
            jdbcOnlyInInfrastructure,
            jwtOnlyInIdentity(basePackage),
            preAuthorizeOnlyInWeb,
            controllersInWeb,
            noFieldInjection,
        ) + layersPointInward + appendOnlyRepositories + eventsOwnedByPublisher(basePackage, businessModules)
}
