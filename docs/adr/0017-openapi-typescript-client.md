# ADR 0017 — TS istemcisi: openapi-typescript + openapi-fetch

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
mimari.md §15 ve kurallar.md §5, `packages/api-client` paketinin OpenAPI belgesinden üretilmesini, elle düzenlenmemesini ve üretim farkında CI'ın kırılmasını istiyor. Hangi aracın kullanılacağı belirtilmemiş. Yeni bağımlılık ADR gerektiriyor (kurallar.md §9).

## Karar
- OpenAPI belgesi backend testinde (`OpenApiDocumentTests`) springdoc'tan alınır ve `packages/api-client/openapi.json` dosyasına deterministik biçimde yazılır.
- Tipler **openapi-typescript** (yalnızca devDependency, MIT) ile `src/schema.ts` olarak üretilir.
- Çağrılar **openapi-fetch** (MIT, yaklaşık 6 KB, sıfır bağımlılık) üzerinden yapılır. Paket, `createApiClient({ baseUrl, getToken })` sarmalayıcısını dışa aktarır.
- CI, üretimi tekrarlar ve `git diff --exit-code packages/api-client` ile fark kontrol eder.

## Alternatifler
- **openapi-generator (typescript-fetch):** JVM gerekir; çıktısı büyük ve okunması zor.
- **orval / hey-api:** Güçlü ama TanStack Query hook'larını da üretir. Kurallar sunucu durumunu TanStack Query'de istiyor, ancak hook'ların feature içinde elle yazılması sınırları daha net tutar.

## Sonuçlar
- (+) Üretilen kod yalnızca tiplerden oluşur; runtime yüzeyi çok küçüktür.
- (+) Yol, metot, gövde ve yanıt tipleri derleme zamanında kontrol edilir.
- (−) openapi-fetch 0.x sürümündedir; sürüm kilidiyle (pnpm-lock) sabitlenir.
