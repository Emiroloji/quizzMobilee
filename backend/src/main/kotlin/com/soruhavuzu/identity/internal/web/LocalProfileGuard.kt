package com.soruhavuzu.identity.internal.web

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * Savunma derinliği: sahte JWT üreticisinin (local/test) gerçek ortam profiliyle birlikte açılmasını engeller.
 * Aksi halde yanlış bir profil kombinasyonu (ör. prod,local) herkese token üretme imkânı verirdi (ADR 0018).
 */
@Configuration(proxyBeanMethods = false)
@Profile("local | test")
class LocalProfileGuard(
    environment: Environment,
) {
    init {
        check(!environment.acceptsProfiles(Profiles.of("staging | prod"))) {
            "Sahte JWT üretici (local/test) staging veya prod profiliyle birlikte açılamaz: ${environment.activeProfiles.toList()}"
        }
    }
}
