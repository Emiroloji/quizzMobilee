# ADR 0008 — Yüzdelik tabanlı 1–10 kova

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Kullanıcı "7'yi getir" dediğinde havuz boş dönmemeli. Ham logit dağılımı dengesiz olabilir.

## Karar
Kullanıcıya gösterilen `difficultyBucket`, aktif soru sayısı < 500 iken doğrusal (`clamp(round(1 + 9·(d + 3)/6), 1, 10)`), üstünde sınav bazında `d` yüzdeliğidir (`1 + floor(9.999·p)`). Kova, 0.3 kova histerezisle değişir. Yüzdelik gece işiyle hesaplanır ve `BucketsRecomputed` yayınlanır.

## Alternatifler
- **Sabit logit aralıkları:** Bazı kovalar boş kalır.

## Sonuçlar
- (+) Her kova dolu; kullanıcı beklentisiyle uyumlu.
- (−) Kova göreli bir değerdir; havuz değiştikçe bir sorunun kovası kayabilir (histerezis yumuşatır).
