# proje.md — Ürün Tanımı

> Bu dosya onaylı v1.0 metnidir (NotebookLM kaynağından aktarıldı). Sonradan kabul edilen kararlar docs/adr/0013 ve sonrasındadır; çelişki halinde ADR geçerlidir.

**Durum:** Onaylı · **Sürüm:** 1.0 · **Tarih:** 2026-10-28

Bu dosya ürünün **ne** olduğunu ve **neden** öyle olduğunu tanımlar. Teknik **nasıl** için `mimari.md`, uyulacak kurallar için `kurallar.md`, sıralama için `fazlar.md`.

Çalışma adı: **SoruHavuzu** (kod tabanında `soruhavuzu`). İsim değişirse yalnızca markalama değişir, paket/şema adları değişmez.

---

## 1. Tek Cümlelik Tanım

Herkesin soru yükleyebildiği, her sorunun zorluğunun **önce yükleyenin puanıyla başlayıp sonra gerçek çözüm verisiyle kalibre edildiği**, kullanıcının "bana 10 üzerinden 7 zorlukta soru getir" diyerek çalışabildiği mobil soru bankası.

## 2. Problem ve Çözüm

| Problem | Bizim çözümümüz |
| --- | --- |
| Soru bankalarında zorluk etiketi ya yok ya da yayınevinin öznel tahmini. | Zorluk **ölçülür**: yükleyen puanı başlangıç tahminidir, her çözümle güncellenir (Elo/Rasch). |
| Öğrenci kendi seviyesine uygun soruyu bulamıyor. | Zorluk (1–10) + konu seçerek çözme; "Beni zorla" modu seviyeye otomatik uyar. |
| İçerik üretimi pahalı ve yavaş. | Açık yükleme: öğretmen, öğrenci, herkes soru üretebilir. |
| Açık yüklemede kalite çöker. | Durum makinesi, itibar kapılı inceleme, otomatik kontroller, yanlış cevap anahtarı tespiti. |

## 3. Hedef Kitle

- **Birincil:** Türkiye'de YKS (TYT/AYT) ve KPSS'ye hazırlanan öğrenciler.
- **Başlangıç alanı:** TYT Matematik. Diğer sınav/dersler **veriyle** (taksonomi kaydı) eklenir, kod değişmez.
- **İçerik üreticileri:** Matematik öğretmenleri, üniversite öğrencileri, ileri seviye öğrenciler.
- **Önemli not:** Kullanıcıların bir kısmı 18 yaş altıdır. Veri ve bildirim kararları buna göre muhafazakâr alınır (bkz. §10).

## 4. Kavramlar Sözlüğü

Kodda, API'de, veritabanında ve konuşmada **bu terimler** kullanılır. Eş anlamlı uydurulmaz.

| Terim (TR) | Kod adı (EN) | Tanım |
| --- | --- | --- |
| Soru | `Question` | Kimliği, sahibi, durumu ve konusu olan kalıcı varlık. İçeriği versiyonlarda durur. |
| Soru versiyonu | `QuestionVersion` | Sorunun **değişmez** içeriği (gövde, cevap şeması, çözüm). Düzenleme = yeni versiyon. |
| Cevap şeması | `AnswerSpec` | Soru tipine göre doğru cevabı tanımlayan yapı (`mcq`, `numeric`, `open`). |
| Deneme / Çözüm | `Attempt` | Bir kullanıcının bir soru versiyonuna verdiği tek cevap. Silinmez, değişmez. |
| İlk deneme | `isFirst` | Kullanıcının o soruya **ilk** cevabı. Zorluk kalibrasyonunu yalnızca bu etkiler. |
| Oturum | `PracticeSession` | Kullanıcının bir mod ve filtreyle başlattığı çözme seansı. |
| Mod | `SessionType` | `PRACTICE` (zorluk seçerek), `CHALLENGE` (beni zorla), `TIMED` (v2), `REVIEW` (yanlışlarım), `OFFLINE_REVIEW`. |
| Yükleyen puanı | `uploaderRating` | Yükleyenin verdiği ham 1–10 puan. Değişmez, saklanır. |
| Zorluk (logit) | `difficultyLogit` | Sorunun iç ölçekteki zorluğu (d). Gerçek "doğru" bu. |
| Zorluk kovası | `difficultyBucket` | Kullanıcıya gösterilen 1–10 değer. `difficultyLogit`'ten türetilir. |
| Yetenek | `theta` (θ) | Kullanıcının iç ölçekteki seviyesi. Global + konu bazlı. |
| Yükleyen yanlılığı | `uploaderBias` | Yükleyenin puanlarının gerçeğe göre sistematik sapması. |
| Sınav | `Exam` | Taksonominin kökü (TYT, AYT, KPSS, GENEL). |
| Konu | `Topic` | Sınav altındaki ağaç düğümü (Matematik › Fonksiyonlar › Bileşke). |
| İtibar | `Reputation` | Kullanıcının topluluk güven puanı. Olay defterinden türetilir. |
| Şikayet | `Report` | Bir soru hakkında kullanıcı bildirimi. |
| Anahtar şüphesi | `KeySuspect` | İstatistiksel olarak cevap anahtarı yanlış olabilecek soru işareti. |
| Varlık | `Asset` | Yüklenen görsel dosya. |

## 5. Kullanıcı Rolleri ve Yetkiler

Rol sabittir, yetkinin bir kısmı **itibarla** açılır.

| Rol | Kim | Yapabilir |
| --- | --- | --- |
| `USER` | Herkes | Soru çöz, soru yükle, şikayet et, favori/yanlışlarım |
| `USER` + itibar ≥ 50 | Güvenilir yükleyen | Soruları inceleme kuyruğuna girmeden doğrudan yayına çıkar |
| `USER` + itibar ≥ 200 | Doğrulayıcı | Anahtar şüphesi görevleri, şikayeti 2 kat ağırlıklı |
| `USER` + itibar ≥ 1000 | Topluluk moderatörü | İnceleme kuyruğunda oy |
| `EDITOR` | Kurucu/editör hesaplar | Seed içerik; soruları "Editör" etiketiyle görünür |
| `MODERATOR` | Atanmış | Kuyruk, FLAGGED, kaldırma, kullanıcı yaptırımı |
| `ADMIN` | Sistem sahibi | Her şey + taksonomi + parametre yönetimi |

## 6. Kapsam

### 6.1 v1 (MVP) — "Çekirdek döngü + güven"

| # | Özellik | Neden v1'de |
| --- | --- | --- |
| F1 | Kayıt/giriş: Apple, Google, e-posta | iOS'ta sosyal giriş varsa Apple zorunlu. |
| F2 | Soru yükleme: metin + LaTeX + görsel, 2–5 şık, doğru şık, zorluk 1–10, konu, **zorunlu çözüm açıklaması**, özgünlük beyanı | Çözüm zorunluluğu kaliteyi yükseltir, spam maliyetini artırır. |
| F3 | Zorluk (tek değer veya aralık) + konu seçerek çözme | Ürünün varlık nedeni. |
| F4 | Anında sonuç + çözüm açıklaması | Öğrenme olmadan skor anlamsız. |
| F5 | Hibrit zorluk: yükleyen puanı prior + Elo | Soğuk başlangıçta yükleyen, sonra veri belirler. |
| F6 | "Beni zorla" modu | Elo zaten var; maliyeti düşük, değeri yüksek. |
| F7 | Soru durum makinesi + inceleme kuyruğu | Açık yüklemenin ön şartı. |
| F8 | Şikayet | Topluluk filtresi. |
| F9 | İtibar (defter tabanlı) + yetki kapıları | Spam ve kalite kontrolü. |
| F10 | Rate limit | Kötüye kullanıma ilk savunma. |
| F11 | Duplicate tespiti (metin hash + görsel pHash) | Aynı sorunun kopyaları seçimi bozar. |
| F12 | Otomatik görsel moderasyonu + EXIF temizleme | Uygunsuz içerik ve konum sızıntısı. |
| F13 | ÖSYM/telif filtresi + kaldırma süreci | En büyük hukuki risk. |
| F14 | Yanlışlarım + favoriler (offline okunabilir) | Tekrar döngüsü. |
| F15 | Basit istatistik: çözülen, doğru oranı, konu bazlı seviye | Motivasyon. |
| F16 | Push: "sorun yayında / kaldırıldı" | Yükleyenin geri dönmesi. |
| F17 | Hesap silme + veri indirme | KVKK ve mağaza zorunluluğu. |
| F18 | Web moderasyon paneli | Moderasyon mobilden yapılmaz. |

### 6.2 v2 — "Alışkanlık + derinlik"

Günlük seri ve hedef · Zaman sınırlı mod (`TIMED`) · Hata analizi (konu × çeldirici türü) · Çözüm sonrası zorluk oylaması · Aralıklı tekrar (yanlışları akıllı sırayla getirme) · Yükleyen profili ve takip · Çözüm yorumları / alternatif çözüm · Sayısal cevap tipi (`numeric`) · Anahtar doğrulama görevleri · Embedding tabanlı benzer soru tespiti.

**Kural:** v2 özelliklerinin ihtiyaç duyduğu **ham veri v1'den itibaren toplanır** (cevap süresi, seçilen şık, oturum tipi, θ/d anlık değerleri, günlük aktivite). Bu yüzden v2 şema değişikliği gerektirmez.

### 6.3 Sonrası

Asenkron meydan okuma · Deneme sınavı oluşturucu · Öğretmen/sınıf hesapları · Fotoğraftan LaTeX'e OCR · AI destekli çözüm doğrulama · Açık uçlu soru tipi · Premium.

### 6.4 Kapsam Dışı (bilinçli olarak)

- **Gerçek zamanlı düello / rakiple yarışma.** (Veri modeli `SessionType` ile ileride izin verir, ama planlanmadı.)
- Sohbet / mesajlaşma.
- Sosyal akış (feed).
- Web'den soru çözme (admin paneli hariç).

## 7. Temel Kullanıcı Akışları

### 7.1 Soru çözme

```text
Ana sayfa → Zorluk/konu seç → Oturum başlar → Soru gösterilir (cevap anahtarı istemcide YOK)
→ Kullanıcı şık seçer → Sunucu değerlendirir → Doğru/yanlış + doğru şık + çözüm
→ Favori / şikayet / sonraki → ... → Oturum özeti (doğru/yanlış, seviye değişimi)
```

- Aynı kullanıcıya aynı soru, kendisi `REVIEW` modunu seçmedikçe tekrar gelmez.
- Seçilen kovada soru biterse: "7'de soru kalmadı, 6–8 aralığından devam edelim mi?" Boş ekran gösterilmez.
- Kullanıcı kendi yüklediği soruyu çözme havuzunda görmez.

### 7.2 Beni zorla

```text
Ana sayfa → Beni zorla (konu opsiyonel) → Sistem, başarı olasılığı ≈ %60 olan soruları seçer
→ 3 yanlış üst üste: hedef %75'e çıkar (kolaylaşır) · 3 doğru üst üste: hedef %50'ye iner (zorlaşır)
```

### 7.3 Soru yükleme

```text
Yükle → Kamera/galeri veya metin → LaTeX editörü + canlı önizleme → Şıklar + doğru şık
→ Zorluk 1–10 → Konu → Çözüm açıklaması → Özgünlük beyanı → Gönder
→ PENDING (otomatik kontroller) → itibar ≥ 50: ACTIVE · değilse: IN_REVIEW → moderatör onayı → ACTIVE
→ Push: "Sorun yayında"
```

### 7.4 Şikayet ve kaldırma

```text
Soru ekranı → Şikayet (yanlış anahtar / telif / uygunsuz / kopya / anlaşılmaz / diğer)
→ Ağırlıklı şikayet skoru eşiği aşarsa ACTIVE → FLAGGED (havuzdan çıkar)
→ Moderatör: düzelt (yeni versiyon → ACTIVE) veya kaldır (REMOVED)
→ Telif şikayeti: öncelikli kuyruk, 48 saat içinde karar
```

## 8. Ekranlar (v1)

| # | Ekran | Not |
| --- | --- | --- |
| S1 | Onboarding | Sınav/hedef seçimi, doğum yılı, aydınlatma metni |
| S2 | Giriş / Kayıt | Apple, Google, e-posta |
| S3 | Ana sayfa | Devam et · Zorluk seç · Beni zorla · Yanlışlarım · Favoriler |
| S4 | Zorluk + konu seçimi | 1–10 seçici (tek/aralık), konu ağacı |
| S5 | Soru çözme | Gövde (LaTeX), şıklar, favori, şikayet |
| S6 | Sonuç | Doğru şık, çözüm açıklaması |
| S7 | Oturum özeti | Doğru/yanlış, seviye değişimi |
| S8 | Soru yükle | Çok adımlı form, canlı önizleme |
| S9 | Yüklediklerim | Durum rozeti, çözüm sayısı, doğru oranı |
| S10 | Yanlışlarım / Favoriler | Offline çalışır |
| S11 | İstatistik | Konu bazlı seviye, doğru oranı, çözülen sayısı |
| S12 | Profil / Ayarlar | Bildirimler, veri indir, hesap sil |
| S13 | Şikayet akışı | Sebep + not |
| W1 | (Web) Moderasyon paneli | Kuyruk, FLAGGED, anahtar şüphesi, duplicate adayları, yaptırımlar |

## 9. Zorluk Sistemi (ürün açısından)

1. Yükleyen 1–10 puan verir. Bu **ilk tahmindir**; soru yayına bu tahminle çıkar.
2. Her ilk çözümde sistem sorunun zorluğunu ve kullanıcının seviyesini birlikte günceller. Güçlü kullanıcının kolay soruyu yapması zorluğu az değiştirir; zayıf kullanıcının zor soruyu yapması çok değiştirir.
3. Veri arttıkça yükleyenin etkisi azalır: ~5 çözümde %50, ~45 çözümde %10.
4. Kullanıcıya gösterilen 1–10, sorunun tüm aktif sorular içindeki **sırasıdır**. Bu sayede "7'yi getir" her zaman dolu döner.
5. Sürekli abartılı puan veren yükleyenin sonraki sorularının başlangıç tahmini otomatik düzeltilir.

Formüller ve parametreler: `mimari.md §8`.

## 10. Hukuk ve Uyum

| Konu | Karar |
| --- | --- |
| ÖSYM soruları | **Yasak.** Kullanım şartlarında açıkça yazar. Yükleme anında özgünlük beyanı alınır. Otomatik filtre (filigran metni + benzerlik) v1'de vardır. Telif şikayetleri öncelikli kuyruktadır. |
| Yayınevi soruları | ÖSYM ile aynı kural. |
| Yer sağlayıcı statüsü | 5651 kapsamında hızlı kaldırma süreci işletilir, kaldırma kayıtları saklanır. |
| KVKK yurt dışı aktarım | AWS bölgesi Türkiye dışında olduğu için Kurul standart sözleşmesi imzalanır ve 5 iş günü içinde bildirilir. Aydınlatma metninde açıkça yazar. |
| 18 yaş altı | Doğum yılı alınır. 18 altı için pazarlama/profilleme kapalıdır. Ad-soyad zorunlu değildir; takma ad yeterlidir. |
| Veri minimizasyonu | Görsellerden EXIF silinir. Loglarda kişisel veri tutulmaz. |
| Haklar | Uygulama içinden hesap silme ve veri indirme. |

Yayına çıkmadan önce kullanım şartları, aydınlatma metni ve kaldırma süreci bir bilişim/fikri mülkiyet avukatına kontrol ettirilir (`fazlar.md` Faz 4).

## 11. Başarı Metrikleri

| Metrik | v1 hedefi (kapalı beta sonu) |
| --- | --- |
| Her kovada aktif soru (TYT Mat.) | ≥ 100 |
| D7 retention | ≥ %25 |
| Kullanıcı başına haftalık çözüm | ≥ 30 |
| Topluluk yüklemesi / toplam yükleme | ≥ %30 |
| FLAGGED → REMOVED oranı | Takip edilir (hedef yok, kalibrasyon için) |
| Zorluk modeli log-loss | Sabit %50 tahmininden belirgin iyi |
| Telif şikayeti çözüm süresi | ≤ 48 saat |

## 12. Riskler

| Risk | Etki | Önlem |
| --- | --- | --- |
| Soğuk başlangıç (boş uygulama) | Yüksek | 1.500+ editör sorusu, 20–50 kurucu yazar, tek sınav/tek dersle başlama |
| ÖSYM telif davası | Yüksek | Yasak + filtre + hızlı kaldırma + avukat kontrolü |
| Yanlış cevap anahtarları | Orta | Anahtar şüphesi analizi + şikayet + doğrulayıcılar |
| Spam / çöp içerik | Orta | Rate limit, inceleme kuyruğu, itibar |
| LaTeX render kalitesi | Orta | Tek render bileşeni, gömülü KaTeX, izinli makro listesi |
| Zorluk modelinin yanlış ayarı | Düşük | Parametreler konfigürasyonda; beta verisiyle ayarlanır |

## 13. İlgili Dokümanlar

- `mimari.md` — teknik tasarım
- `kurallar.md` — kodlama, mimari ve süreç kuralları
- `fazlar.md` — geliştirme fazları ve kabul kriterleri
- `docs/adr/` — mimari karar kayıtları
