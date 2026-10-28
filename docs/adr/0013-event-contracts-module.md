# ADR 0013 — Olay sözleşmeleri ayrı bir açık modülde

- **Tarih:** 2026-10-28
- **Durum:** Kabul (2026-10-28, proje sahibi onayı)

## Bağlam
mimari.md §5.2 (senkron bağımlılık yönü) ile §5.4 (olay kataloğu) birlikte uygulandığında Spring Modulith **döngü** tespit eder. Bunun nedeni, bir olayı dinleyen modülün o olayın tipine, yani yayınlayan modülün kök paketine derleme zamanında bağımlı olmasıdır.

Faz 0'da geçici kodla doğrulandı. `calibration`, `practice`'in olayını dinlediğinde ve `practice` de `CalibrationApi`'yi kullandığında `ModularityTests` şu hatayla kırılır:

```
Cycle detected: Slice calibration -> Slice practice -> Slice calibration
Module 'calibration' depends on module 'practice' via ...AttemptRecordedProbe. Allowed targets: shared.
```

Belgelerden çıkan döngüler:

| Döngü | Senkron yön (§5.2) | Ters yöndeki olay (§5.4) |
|---|---|---|
| practice ↔ calibration | practice → `CalibrationApi.predict`; practice, `DifficultyUpdated` ve `BucketsRecomputed` olaylarını dinler | calibration, `AttemptRecorded` olayını dinler |
| content ↔ reputation | content → `ReputationApi.hasPrivilege` | reputation, `QuestionStatusChanged` olayını dinler |
| moderation ↔ reputation | moderation → reputation (şikayet ağırlığı) | reputation, `ReportResolved` olayını dinler |
| content ↔ moderation | moderation → `ContentApi` | content, `QuestionFlagged` olayını dinler |

Döngü olmasa da §5.2 tablosu yalnızca olay dinleyen bağımlılıkları göstermiyor. Örneğin `calibration`, `stats` ve `notification` modüllerinin `allowedDependencies` listesi, dinledikleri olaylar yüzünden genişlemek zorunda kalır.

Bu sorun Faz 1'de ilk olay yazıldığında ortaya çıkar. Faz 0 iskeletini engellemez.

## Karar
Modüller arası olaylar, açık (`Type.OPEN`) ve bağımlılığı yalnızca `shared` olan **`events`** modülünde tanımlanır:

```
com.soruhavuzu.events/
├── ModuleMetadata.kt          # @ApplicationModule(type = OPEN, allowedDependencies = ["shared"])
├── content/ContentEvents.kt   # QuestionSubmitted, QuestionStatusChanged, QuestionVersionCreated
├── practice/PracticeEvents.kt # AttemptRecorded
├── calibration/...            # DifficultyUpdated, BucketsRecomputed
└── ...
```

- Olayın **sahibi** alt paket adıyla belli olur ve yalnızca sahibi olan modül o olayı yayınlar. Bu kural ArchUnit ile denetlenir.
- Olay kuralları (§5.4) aynen geçerlidir: değişmez `data class`, geçmiş zamanlı ad, yalnızca ID ve skaler değer, geriye uyumlu evrim.
- `allowedDependencies` yalnızca **senkron** API bağımlılıklarını gösterir; §5.2 tablosu olduğu gibi kalır.
- Modül servise ayrıldığında `events` paketi olay sözleşmesi olur (SQS mesaj şeması).

## Alternatifler
1. **Döngü başına yönü ters çevirmek** (ör. moderation, `QuestionFlagged` yayınlamak yerine `ContentApi.flag()` çağırır). Bu, content ↔ moderation döngüsünü çözer ama practice ↔ calibration döngüsünü çözmez. O döngüde `AttemptRecorded` için senkron çağrı gerekir ve asenkron kalibrasyon kaybolur.
2. **Yayınlayan modülde `@NamedInterface("events")` kullanmak.** Bağımlılık yine yayınlayan modüle gider, döngü kalır.
3. **Modulith döngü kontrolünü kapatmak.** kurallar.md A2'yi çiğner.

## Sonuçlar
- (+) Olay yönünden kaynaklanan döngü yapısal olarak imkânsız hale gelir. §5.2'deki senkron yön korunur.
- (+) Olaylar tek yerde toplanır ve olay kataloğu kodda görünür.
- (−) mimari.md §5.3'teki `<Modul>Events.kt` dosyası modül kökünden çıkar, `events/<modul>/` altına taşınır. §5.3, §5.4 ve §20.1 güncellenir, mimari.md sürümü 1.1 olur.
- (−) Bir olayın sahibi dışında yayınlanmasını önlemek için ek bir ArchUnit kuralı gerekir.
