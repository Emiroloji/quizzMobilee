# ADR 0012 — ÖSYM/yayınevi içerik yasağı ve kaldırma süreci

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Açık yüklemede en büyük hukuki risk ÖSYM ve yayınevi sorularının telif ihlalidir. Platform 5651 kapsamında yer sağlayıcıdır.

## Karar
- ÖSYM ve yayınevi soruları **yasaktır**; kullanım şartlarında açıkça yazar.
- Yüklemede **özgünlük beyanı** zorunludur (`originality_declared_at`).
- v1'de otomatik filtre: filigran/anahtar ifade listesi (+ görsellerde OCR ile metin çıkarma); v2'de embedding benzerliği.
- Telif şikayetleri yüksek öncelikli `COPYRIGHT` review item olarak açılır, **48 saat** içinde karara bağlanır; kaldırma kayıtları (`moderation_actions`) saklanır.
- Kullanım şartları, aydınlatma metni ve kaldırma süreci yayından önce avukata kontrol ettirilir (Faz 4).

## Alternatifler
- **Yalnızca şikayet üzerine kaldırma:** Risk çok yüksek.

## Sonuçlar
- (+) Hukuki riskin azaltılması, hızlı kaldırma kanıtı.
- (−) Yanlış pozitif filtreler meşru içeriği reddedebilir; COPYRIGHT kuyruğu ile insan incelemesi.
