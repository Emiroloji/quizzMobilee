# ADR 0016 — Soru versiyonu embedding'leri ayrı tabloda

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
mimari.md §6, `content.question_versions` tablosuna `embedding vector(512)` kolonu koyuyor. Bu kolon v2'de, versiyon oluşturulduktan **sonra** asenkron (Bedrock) doldurulacak. Ama aynı tablo değişmez: kurallar.md A5 ve ADR 0006 gereği UPDATE trigger ile yasak. Dolayısıyla kolon hiçbir zaman doldurulamaz. Embedding modeli değiştiğinde yeniden hesaplamak da mümkün olmaz.

## Karar
- `question_versions.embedding` kolonu oluşturulmaz.
- Onun yerine **`content.question_version_embeddings(version_id, model, embedding vector(512), created_at)`** tablosu eklenir. PK `(version_id, model)`'dir; modele özgü HNSW index'i v2'de eklenir.
- Versiyonun içeriği değişmez kalır. Embedding, içerikten türetilen ve yeniden üretilebilen bir veridir.

## Alternatifler
- **Trigger'da istisna** (yalnızca `embedding` NULL'dan dolu değere geçebilir): Değişmezlik kuralını delik açarak zayıflatır, model değişimini desteklemez.

## Sonuçlar
- (+) A5 delinmeden v2 benzerlik araması mümkün olur; birden fazla model yan yana tutulabilir.
- (−) Benzerlik sorgusunda bir JOIN gerekir (aynı şema içinde, sorun değil).
