# ADR 0010 — Medya işleme Lambda'da, S3 pre-signed yükleme

- **Tarih:** 2026-10-28
- **Durum:** Kısmen yerini aldı: 0023 (presigned yükleme korunur; Lambda/Rekognition/CloudFront kaldırıldı)

## Bağlam
Görseller EXIF'ten temizlenmeli, küçültülmeli, pHash'lenmeli ve uygunsuz içerik açısından denetlenmeli. Bu iş API sürecini meşgul etmemeli.

## Karar
İstemci, `media` modülünden alınan kısa ömürlü, tür/boyut kısıtlı **pre-signed PUT** URL ile doğrudan S3 `uploads/` önekine yükler. S3 olayı → SQS `media-in` → **Lambda media-processor** (Node/TS): decode → EXIF sil → max 1600px WebP → pHash → Rekognition → `public/{assetId}.webp` → sonuç SQS `media-results`. `media` modülü sonucu tüketir ve `AssetProcessed` yayınlar. Okuma yalnızca CloudFront OAC ile.

## Alternatifler
- **API üzerinden yükleme ve işleme:** API'ye bellek/CPU yükü, ölçekleme zorluğu.

## Sonuçlar
- (+) API hafif kalır; işleme bağımsız ölçeklenir.
- (−) Asenkron akış: soru gönderiminde yalnızca READY asset kabul edilir.
