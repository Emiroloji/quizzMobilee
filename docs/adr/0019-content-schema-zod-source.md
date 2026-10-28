# ADR 0019 — İçerik şemasının yazım kaynağı zod; schema.json bundan üretilir

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
mimari.md §7: "Tek kaynak `packages/content-schema/schema.json` (JSON Schema). Backend aynı dosyayla doğrular, TS tarafı zod şemasını buradan türetir."

JSON Schema → zod yönünde iki yol var:
- **Kod üretici** (`json-schema-to-zod`): zod 3 sözdizimine göre yazılmış; zod 4 ile uyumu kısmi. Üçüncü taraf bir araca bağımlılık getirir.
- **Çalışma anında `z.fromJSONSchema()`:** Statik TS tiplerini kaybettirir (`unknown`). Editör, form ve render kodu tipsiz kalır.

Ayrıca bazı kurallar JSON Schema ile ifade edilemiyor: doğru şık, şıklardan biri olmalı; şık kimlikleri tekil olmalı; şıklar birebir aynı olmamalı; LaTeX makro izin listesi.

## Karar
- Şemanın **yazım kaynağı** `packages/content-schema/src/schema.ts` dosyasındaki zod tanımlarıdır.
- **`schema.json`, zod'un yerleşik `z.toJSONSchema()` fonksiyonuyla üretilir** (draft 2020-12, `$defs`: `Content`, `AnswerSpec`). Dosya repoya commit'lenir. Üretim bir testtir; CI fark görürse build kırılır. Backend bu dosyayla doğrular (mimari.md §7'deki yol ve rol değişmez).
- JSON Schema'ya sığmayan **anlamsal kurallar** hata kodlarıyla tanımlanır (`LATEX_*`, `MCQ_*`). Bunlar paylaşılan **`fixtures/`** korpusuyla test edilir. Backend aynı korpusu Faz 1'de kendi testlerinde kullanır; böylece iki taraf aynı girdiye aynı kararı verir.
- Testte Ajv, üretilen `schema.json`'ı aynı korpus üzerinde çalıştırır. Yapısal geçerlilik kararları zod ile birebir aynı olmalıdır.

## Alternatifler
- Yukarıdaki iki JSON Schema → zod yolu (gerekçeleriyle reddedildi).
- **İki ayrı elle yazılmış tanım:** Zamanla ayrışır.

## Sonuçlar
- (+) Tek tanım; statik tipler (`z.infer`); ek kod üretici bağımlılığı yok.
- (+) Anlamsal kurallar kodla değil, ortak fixture korpusuyla iki dilde senkron tutulur.
- (−) Şema değişikliği önce TS'te yapılır; Kotlin geliştirici `schema.json` dosyasını doğrudan düzenlemez.
