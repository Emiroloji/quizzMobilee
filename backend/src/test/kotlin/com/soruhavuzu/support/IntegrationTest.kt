package com.soruhavuzu.support

import com.soruhavuzu.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/** Gerçek PostgreSQL (pgvector) üzerinde, tüm migration'lar uygulanmış bağlamla çalışan testler. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest
