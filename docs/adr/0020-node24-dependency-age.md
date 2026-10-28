# ADR 0020 — Node 24 LTS ve bağımlılık yaşı politikası

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
- Geliştirme makinesinde Node 20 vardı. **Node 20, Nisan 2026'da destek dışı kaldı.** Güncel araçlar (ör. vitest 5) Node ≥ 22.12 istiyor.
- Kötü amaçlı paket sürümleri genelde yayından sonraki ilk günlerde tespit edilip kaldırılıyor (tedarik zinciri saldırıları). kurallar.md §9 "bilinen kritik zafiyet yok" diyor, ama zaman kuralı koymuyor.

## Karar
- Tüm TS/JS iş akışları (mobil, admin, paketler, Lambda, CDK) **Node 24 LTS** kullanır. Sabitleme: `.nvmrc` (24) ve kök `package.json` içindeki `engines.node: >=24.11 <25`. Lambda runtime'ı `nodejs24.x` olur.
- Yeni eklenen veya yükseltilen her bağımlılık sürümü **en az 14 günlük** olmalıdır. Kritik güvenlik yaması bu kuralın tek istisnasıdır (PR'da gerekçesi yazılır).
- Sürümler kesin yazılır (`^` yok). Kilit dosyası (`pnpm-lock.yaml`) commit'lenir.

## Alternatifler
- **Node 22 LTS:** Destek süresi daha kısa (Nisan 2027'de biter).

## Sonuçlar
- (+) Güvenlik yamaları alınır; araç uyumluluğu sağlanır.
- (+) Yeni yayınlanmış kötü amaçlı bir sürümün projeye girme riski azalır.
- (−) En yeni özellikler iki hafta gecikmeli gelir.
