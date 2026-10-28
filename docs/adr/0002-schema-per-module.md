# ADR 0002 — Modül başına PostgreSQL şeması, şemalar arası FK yok

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Modüllerin kendi verisinin sahibi olması ve ileride ayrı veritabanına taşınabilmesi gerekiyor.

## Karar
Her modülün PostgreSQL'de kendi şeması vardır (`identity`, `content`, `practice`…). Bir modül yalnızca kendi şemasına SQL yazar. Şemalar arası foreign key yoktur; referans yalnızca ID ile yapılır. Başka modülün verisine ihtiyaç duyan modül, olaylardan beslenen kendi projeksiyonunu tutar (ör. `practice.question_pool`).

## Alternatifler
- **Modül başına ayrı veritabanı:** Bu aşamada operasyon maliyeti yüksek.
- **Tek şema, tablo öneki:** Sınır ihlali kolay ve görünmez.

## Sonuçlar
- (+) Modül servise ayrıldığında şeması doğrudan taşınabilir.
- (−) Referans bütünlüğü uygulama ve olaylarla sağlanır; projeksiyonların yeniden kurulabilir olması gerekir.
- Kontrol: ArchUnit + SQL şema adı lint'i (kurallar.md A3), migration review (A4).
