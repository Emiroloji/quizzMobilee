# ADR 0001 — Modüler monolit (Spring Modulith), mikroservis değil

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Tek kişilik, yarı zamanlı bir ekip; ürün henüz doğrulanmadı. Yine de modüllerin (content, practice, calibration, moderation…) ileride bağımsız ölçeklenebilmesi isteniyor.

## Karar
Tek deploy birimi olan bir **modüler monolit** kurulur. Modül sınırları Spring Modulith ile derleme/test zamanında doğrulanır (`ApplicationModules.verify()`). Modüller arası iletişim: senkron ihtiyaçta modülün kök paketindeki `<Modul>Api`, diğer her şeyde domain olayları.

## Alternatifler
- **Mikroservisler:** Ağ, dağıtık transaction, gözlemlenebilirlik ve deploy maliyeti bu ölçekte gereksiz.
- **Sınırsız monolit:** Hızlı başlar ama sınırlar zamanla aşınır; servise ayırma imkânsızlaşır.

## Sonuçlar
- (+) Tek deploy, tek veritabanı, basit lokal geliştirme.
- (+) Bir modül, olaylar Modulith externalization ile SQS'e yönlendirilerek ve API'si HTTP adaptörüyle değiştirilerek servise ayrılabilir (mimari.md §20.5).
- (−) Modül disiplini CI testleriyle zorlanmalı (kurallar.md A1, A2).
