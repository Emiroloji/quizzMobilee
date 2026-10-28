# ADR 0007 — Elo/Rasch zorluk modeli, yükleyen puanı prior

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Zorluk etiketi öznel olmamalı, ölçülmeli. Soğuk başlangıçta veri yok; yükleyenin tahmini tek bilgi.

## Karar
Tahmin düzeltmeli Rasch/Elo modeli: `P(doğru) = 1/k + (1 − 1/k)·σ(θ_eff − d)`. Yükleyen puanı `d0 = (r − 5.5)·s + b_u` prior'udur ve `n0` sanal gözlem ağırlığıyla başlar; güncelleme yalnızca uygun ilk denemelerde, azalan K ile yapılır. Yükleyen yanlılığı `b_u` büzülmeli ortalamayla öğrenilir. Tüm parametreler `calibration.*` konfigürasyonundadır (mimari.md §8).

## Alternatifler
- **Yalnızca yükleyen puanı:** Ölçüm yok.
- **Toplu IRT (gece batch, MLE):** Daha doğru ama soğuk başlangıçta ve anlık geri bildirimde zayıf; ileride doğrulama aracı olarak eklenebilir.
- **Glicko/TrueSkill:** Belirsizlik takibi güzel ama karmaşıklık/fayda oranı v1 için düşük.

## Sonuçlar
- (+) Online, ucuz, açıklanabilir.
- (−) Parametre ayarı beta verisiyle yapılmalı; sağlık için haftalık log-loss/AUC izlenir. Parametre değişiklikleri `docs/calibration-log.md`'ye yazılır.
