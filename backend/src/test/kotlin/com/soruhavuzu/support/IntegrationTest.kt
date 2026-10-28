package com.soruhavuzu.support

import com.soruhavuzu.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** Gerçek PostgreSQL (pgvector) üzerinde, tüm migration'lar uygulanmış bağlamla çalışan testler. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest
