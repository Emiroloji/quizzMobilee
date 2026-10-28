# SoruHavuzu

Herkesin soru yükleyebildiği, her sorunun zorluğunun önce yükleyenin puanıyla başlayıp gerçek çözüm verisiyle kalibre edildiği mobil soru bankası.

> Ürün, mimari, kurallar ve fazlar için **tek doğruluk kaynağı** `docs/` altındadır:
> [`proje.md`](docs/proje.md) · [`mimari.md`](docs/mimari.md) · [`kurallar.md`](docs/kurallar.md) · [`fazlar.md`](docs/fazlar.md) · [`adr/`](docs/adr/)

## Repo yapısı

```
docs/                 proje, mimari, kurallar, fazlar, ADR'ler
backend/              Kotlin + Spring Boot 4 + Spring Modulith (soruhavuzu-api)
mobile/               React Native + Expo
admin/                React + Vite moderasyon paneli
lambdas/              media-processor (Node/TS)
packages/
  content-schema/     blok-JSON şeması, zod, KaTeX allowlist, render
  api-client/         OpenAPI'den üretilen TS istemci (elle düzenlenmez)
infra/                AWS CDK (TS)
```

## Gereksinimler

| Araç | Sürüm |
|---|---|
| JDK | 21+ yüklü olması yeterli; Java 25 toolchain'i Gradle otomatik indirir |
| Node | 24 LTS (`.nvmrc`) |
| pnpm | 10.x |
| Docker | Desktop çalışır durumda |

## Lokal kurulum

> Faz 0 sürüyor; tek komutluk kurulum (`make dev`) bu fazın sonunda burada tarif edilecek.

## Durum

Aktif faz: **Faz 0 — Temel** (bkz. [`docs/fazlar.md`](docs/fazlar.md)).
