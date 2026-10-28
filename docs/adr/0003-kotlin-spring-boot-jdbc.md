# ADR 0003 — Kotlin + Spring Boot + Spring Data JDBC

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Geliştiricinin mevcut Java/Kotlin bilgisi var. Olgun, iyi belgelenmiş ve "sıkıcı" bir yığın isteniyor.

## Karar
Backend **Kotlin 2.x (JVM, Java 25 LTS)**, **Spring Boot 4.x**, **Spring Modulith 2.x**, **Spring MVC + virtual threads** ile yazılır. Kalıcılık **Spring Data JDBC + JdbcClient**, migration **Flyway**.

## Alternatifler
- **JPA/Hibernate:** Lazy-loading sürprizleri, gizli SQL, aggregate sınırlarının bulanıklaşması.
- **WebFlux/reaktif:** Virtual threads ile bloklayan model yeterli ölçeklenir; reaktif karmaşıklığa gerek yok.
- **Node/TS backend:** Tek dil avantajı var, ancak Modulith benzeri sınır doğrulama ve tip güvenli domain modellemesi daha zayıf.

## Sonuçlar
- (+) Null-safety, value class'larla tipli kimlikler, görünür SQL.
- (−) JVM başlangıç süresi ve bellek; Fargate 1 GB ile yeterli.
