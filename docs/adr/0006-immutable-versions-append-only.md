# ADR 0006 — Değişmez soru versiyonları, append-only attempts

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Kalibrasyon ve denetim, bir cevabın hangi içeriğe verildiğini kesin bilmeyi gerektirir. Geçmişin yeniden yazılması istatistiği bozar.

## Karar
- Soru içeriği `content.question_versions` içinde **değişmezdir**; düzenleme = yeni versiyon.
- `practice.attempts`, `moderation.moderation_actions`, `reputation.reputation_events` **append-only**'dir.
- Bu tablolara UPDATE/DELETE hem kodda (repository'de update metodu yok, ArchUnit) hem veritabanında (trigger) engellenir.
- Silme gerekiyorsa soft-delete (soru `REMOVED`) veya anonimleştirme kullanılır.

## Alternatifler
- **Yerinde güncelleme + audit tablosu:** Audit'in atlanma riski, eski cevapların içeriği kaybolur.

## Sonuçlar
- (+) Her attempt, cevaplandığı versiyona bağlıdır; yanlış anahtar düzeltmesi geçmişi bozmaz.
- (−) `attempts` büyür → aylık partition (RANGE `created_at`).
- (−) Hesap silmede PII anonimleştirmesi için trigger'ın kontrollü istisnası gerekir (Faz 3'te ele alınır).
