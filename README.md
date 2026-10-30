# SoruHavuzu

Herkesin soru yükleyebildiği, her sorunun zorluğunun önce yükleyenin puanıyla başlayıp gerçek çözüm verisiyle kalibre edildiği mobil soru bankası.

> Ürün, mimari, kurallar ve fazlar için **tek doğruluk kaynağı** `docs/` altındadır:
> [`proje.md`](docs/proje.md) · [`mimari.md`](docs/mimari.md) · [`kurallar.md`](docs/kurallar.md) · [`fazlar.md`](docs/fazlar.md) · [`adr/`](docs/adr/)

## Hızlı başlangıç

```bash
make dev
```

Bu komut sırasıyla şunları yapar:
1. Araçları kontrol eder.
2. JS bağımlılıklarını kurar.
3. Postgres, S3 ve SQS konteynerlerini başlatır.
4. Backend ile admin panelini birlikte çalıştırır. Durdurmak için `Ctrl-C`.

| Adres | Ne |
|---|---|
| http://localhost:8080 | API (`local` profili; Flyway migration'ları açılışta uygulanır) |
| http://localhost:8080/swagger-ui.html | API belgesi |
| http://localhost:5173 | Admin paneli: "Geliştirici olarak giriş yap" → İçerik önizleme |

Tüm komutları görmek için `make help` çalıştırın.

## Deploy

Beta, tek bir VPS'te Dokploy ile çalışır ([ADR 0023](docs/adr/0023-single-server-dokploy.md); kimlik: Logto, [ADR 0024](docs/adr/0024-logto-identity.md)).
- `main`'e merge → CI → GHCR imajları (amd64 + arm64) → Dokploy redeploy → duman testi.
- Kurulum adımları: [deploy/README.md](deploy/README.md). Yerel doğrulama: `make images TAG=smoke OWNER=local && make beta-smoke`.

## Gereksinimler

| Araç | Sürüm | Not |
|---|---|---|
| Node | 24 LTS | `.nvmrc`. macOS: `brew install node@24` |
| pnpm | 10.x | `corepack enable pnpm` |
| JDK | 21+ | Java 25 toolchain'i Gradle ilk derlemede kendisi indirir |
| Docker | Desktop çalışır durumda | |

`make doctor` bunları kontrol eder.

**İlk kurulum süresi:** Temiz makinede ilk çalıştırmada JDK 25, Gradle, Maven/npm bağımlılıkları ve Docker imajları (toplam yaklaşık 1.5 GB) indirilir. İndirme süresi bağlantıya göre değişir. Sonraki çalıştırmalarda sistem yaklaşık 30 saniyede ayağa kalkar.

## Lokal ortam

| Servis | Adres | Not |
|---|---|---|
| PostgreSQL 17 + pgvector + ltree | `localhost:5432` | db/kullanıcı/parola: `soruhavuzu` |
| S3 (SeaweedFS) | `http://localhost:8333` | erişim anahtarı `local` / `local-secret`; bucket `soruhavuzu-media-local` |
| SQS (ElasticMQ) | `http://localhost:9324` | kuyruklar: `media-in`, `media-results` (+DLQ) |

LocalStack yerine neden bunlar kullanılıyor: [ADR 0022](docs/adr/0022-local-aws-emulation.md). Verileri sıfırlamak için `make reset`.

**Kimlik:** `local` profilinde Cognito yerine sahte JWT üretici çalışır (`POST /v1/dev/tokens`, [ADR 0018](docs/adr/0018-cognito-access-token-jit-user.md)). Mobil ve admin `dev` modunda bunu kullanır; herhangi bir "geliştirici kimliği" yeni bir kullanıcı oluşturur.

## Mobil uygulama

```bash
cp mobile/.env.example mobile/.env.local   # gerekirse EXPO_PUBLIC_API_URL'i düzenleyin
make mobile
```

- **Android emülatörü:** `a` tuşuna basın. API adresi `http://10.0.2.2:8080` olmalı.
- **Fiziksel cihaz:** Telefon ile bilgisayar aynı ağda olmalı. API adresi bilgisayarın yerel IP'si olmalı (ör. `http://192.168.1.20:8080`).
  - Android: Expo Go ile QR kodu okutun.
  - iOS: Development build gerekir. Proje Expo SDK 57'dedir; App Store'daki Expo Go yalnızca en son SDK'yı çalıştırır ([ADR 0021](docs/adr/0021-mobile-admin-toolchain.md)).
- Giriş yaptıktan sonra ana sayfadaki **"Örnek soru (KaTeX)"** ekranı, Faz 0'ın offline render kabul kriterini gösterir. İnterneti kapatıp tekrar açın; içerik aynı görünmelidir.

## Gözlemlenebilirlik

`make backend-otel` backend'i OpenTelemetry Java agent'ı ve yapılandırılmış JSON log ile çalıştırır. Log satırlarında şu alanlar bulunur: `timestamp, level, module, event, traceId, spanId, userId`. `userId` HMAC özetidir; kişisel veri, token ve içerik gövdesi loglanmaz ([mimari.md §18](docs/mimari.md)).

## Repo yapısı

```
docs/                 proje, mimari, kurallar, fazlar, ADR'ler
backend/              Kotlin + Spring Boot 4 + Spring Modulith (soruhavuzu-api)
mobile/               React Native + Expo (SDK 57)
admin/                React + Vite moderasyon paneli
workers/              media-processor (Node/TS) — Faz 1
packages/
  content-schema/     blok-JSON şeması (zod → schema.json), KaTeX allowlist, render
  api-client/         OpenAPI'den üretilen TS istemci (elle düzenlenmez)
deploy/               Dokploy dağıtım tanımları ve runbook (ADR 0023)
infra/local/          lokal docker-compose yapılandırmaları
```

## Kalite kontrolleri

`make check` CI'daki kontrollerin aynısını çalıştırır:
- ESLint, ktlint ve detekt
- TS tip kontrolü
- Backend testleri: mimari kurallar, Testcontainers ile entegrasyon testleri
- TS testleri

## Durum

Aktif faz: **Faz 0 — Temel** (bkz. [`docs/fazlar.md`](docs/fazlar.md)).
