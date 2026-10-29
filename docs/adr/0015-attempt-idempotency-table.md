# ADR 0015 — Attempt idempotency'si ayrı anahtar tablosuyla

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
mimari.md §6, `practice.attempts` tablosunu `created_at` üzerinden aylık partition'lıyor ve idempotency için `UNIQUE (client_attempt_id, created_at)` tanımlıyor.

PostgreSQL'de partition'lı bir tablonun unique index'i partition anahtarını içermek zorunda. Ama `created_at` sunucuda, Clock'tan üretiliyor. Aynı `clientAttemptId` ile gelen ikinci istek (retry ya da offline senkron) farklı bir `created_at` alıyor ve bu kısıttan geçiyor. Sonuçta iki attempt oluşuyor ve Elo iki kez güncelleniyor. Bu, Faz 2'nin kabul kriterini ("aynı `clientAttemptId` iki kez → tek attempt") doğrudan çiğner.

## Karar
- Partition'sız **`practice.attempt_keys(client_attempt_id PK, user_id, attempt_id, created_at)`** tablosu eklenir.
- Attempt kaydı aynı transaction'da şöyle yapılır: önce `INSERT INTO practice.attempt_keys … ON CONFLICT DO NOTHING RETURNING`. Satır döndüyse attempt yazılır. Dönmediyse mevcut `attempt_id` ile önceki sonuç döndürülür.
- Etkisiz olan `ux_attempts_client` index'i oluşturulmaz.
- `attempt_keys` append-only'dir (UPDATE/DELETE trigger ile yasaktır). Hacmi küçüktür: satır başına yaklaşık 64 bayt.

## Alternatifler
- **`created_at`'i istemciden almak:** Saat sahteciliği ve partition dışı tarih riski.
- **Partition'lamamak:** `attempts` en büyük tablo; aylık partition bakım ve arşiv için gerekli.
- **Uygulama içinde "önce SELECT, sonra INSERT":** Yarış koşulu var.

## Sonuçlar
- (+) Idempotency veritabanı seviyesinde garanti edilir.
- (−) Her attempt için bir ek satır yazılır. Sıcak yol iki INSERT olur (aynı transaction).

## Ek (2026-10-29, Faz 0 code review)
Birincil anahtar `(user_id, client_attempt_id)` olarak değiştirildi (`V202610291001`). `client_attempt_id` istemciden geldiği için, yalnızca ona göre tekillik bir kullanıcının başkasının anahtarını kullanarak onun cevabını engellemesine izin veriyordu.
