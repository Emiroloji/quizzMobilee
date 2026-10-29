# fazlar.md — Geliştirme Fazları

> Bu dosya onaylı v1.0 metnidir (NotebookLM kaynağından aktarıldı). Sonradan kabul edilen kararlar docs/adr/0013 ve sonrasındadır; çelişki halinde ADR geçerlidir.

**Durum:** Onaylı · **Sürüm:** 1.0 · **Tarih:** 2026-10-28

Fazlar **sıralıdır**. Bir faz, kabul kriterlerinin tamamı sağlanmadan kapanmaz ve sonraki faz başlamaz (`kurallar.md §13.2`).

Süreler tek kişi, yarı zamanlı çalışma için tahmindir.

---

## Genel Bakış

| Faz | Ad | Süre | Çıktı | Durum |
| --- | --- | --- | --- | --- |
| 0 | Temel | 2 hafta | Çalışan iskelet, şema, CI/CD, altyapı | ⬜ |
| 1 | İçerik | 3 hafta | Soru yükleme uçtan uca | ⬜ |
| 2 | Çözme + Kalibrasyon | 3 hafta | Zorluk seçip çözme, Elo, yanlışlarım/favoriler | ⬜ |
| 3 | Güven | 2 hafta | Moderasyon, itibar, şikayet, duplicate, telif filtresi | ⬜ |
| 4 | Seed + Kapalı Beta | 3–4 hafta | Dolu içerik, gerçek kullanıcı, ayarlanmış parametreler | ⬜ |
| 5 | v1 Lansmanı | 2 hafta | Mağazalarda yayın | ⬜ |
| 6 | v2 | 6–8 hafta | Alışkanlık + derinlik özellikleri | ⬜ |

Durum işaretleri: ⬜ başlamadı · 🟨 sürüyor · ✅ tamamlandı

---

## Faz 0 — Temel

**Amaç:** Hiçbir iş özelliği olmadan, sonraki tüm fazların üzerine oturacağı iskeleti kurmak. Bu fazdan sonra mimari iskelet değişmez.

### Görevler

**Repo ve dokümanlar**

- [ ] Monorepo yapısı (`mimari.md §4`), pnpm workspace, Gradle multi-project gerekmez (tek backend modülü)
- [ ] `docs/` altına dört doküman + `docs/adr/0001…0012` (`mimari.md §21`)
- [ ] README: lokal kurulum tek komut (`make dev` veya `pnpm dev:all`)

**Backend**

- [ ] Spring Boot 4 + Kotlin + Spring Modulith iskeleti; tüm modül paketleri boş `Api` ve `@ApplicationModule` ile
- [ ] `shared`: ID value class'ları, `Clock`/`RandomSource` portları, problem+json hata işleyici, cursor sayfalama
- [ ] `ModularityTests` (Modulith `verify()`) + ArchUnit kuralları (`kurallar.md §2`)
- [ ] detekt + ktlint konfigürasyonu (`!!`, `LocalDateTime`, `Instant.now()` yasakları)
- [ ] Flyway: tüm şemalar ve `mimari.md §6`'daki **tüm tablolar** (v2 tabloları dahil)
- [ ] Değişmez tablolar için UPDATE/DELETE engelleyen DB trigger'ları
- [ ] `attempts` partition'ları + aylık partition oluşturan zamanlanmış iş
- [ ] Spring Security resource server; `local` profilde sahte JWT üretici
- [ ] `identity`: ilk istekte kullanıcı oluşturma, `GET /me`
- [ ] Event Publication Registry kurulumu
- [ ] springdoc → OpenAPI; `packages/api-client` üretimi
- [ ] OpenTelemetry agent + JSON log
- [ ] Testcontainers tabanlı entegrasyon test altyapısı

**Paylaşılan paketler**

- [ ] `content-schema`: JSON Schema v1, zod türetimi, KaTeX allowlist, `toPlainText()`, `renderHtml()`

**Mobil / Admin**

- [ ] Expo + expo-router + TS strict + eslint (boundaries, i18n) iskeleti
- [ ] `shared/api`, `shared/i18n`, `shared/ui` temel bileşenler, tema
- [ ] `QuestionBody` bileşeni: gömülü KaTeX ile örnek blok-JSON render (offline)
- [ ] Cognito girişi (Apple, Google, e-posta) → `GET /me`
- [ ] Admin: Vite + React iskeleti, giriş, `renderHtml` ile önizleme

**Altyapı**

- [ ] docker-compose: Postgres (+pgvector, ltree) + LocalStack
- [ ] CDK: VPC (NAT'sız), RDS, ECS Fargate + ALB, S3 (uploads/public), CloudFront OAC, SQS ×2, Cognito (Lite), Secrets Manager, VPC endpoint'ler — `staging` ve `prod`
- [ ] GitHub Actions: lint → test → mimari testler → OpenAPI farkı → gitleaks → Docker → ECR → ECS (staging)
- [ ] EAS Build profilleri (development, preview, production)

### Kabul Kriterleri

- [ ] Temiz makinede README adımlarıyla lokal ortam ≤ 15 dk'da ayağa kalkar
- [ ] Mobil uygulama staging'e giriş yapar ve `GET /me` sonucunu gösterir
- [ ] `QuestionBody` örnek integral/kesir/limit içeren soruyu iOS ve Android'de offline doğru render eder
- [ ] CI'da mimari testler, başka modülün `internal` paketine kasıtlı erişimde **kırılır** (negatif test)
- [ ] Değişmez tabloya UPDATE denemesi DB seviyesinde reddedilir
- [ ] `main`'e merge staging'e otomatik deploy olur

---

## Faz 1 — İçerik

**Amaç:** Bir kullanıcının soru yükleyebilmesi ve sorunun sistemde doğru durumda saklanması.

### Görevler

- [ ] `taxonomy`: exams/topics, `GET /exams`, `GET /exams/{id}/topics`, admin'de taksonomi yönetimi
- [ ] TYT Matematik konu ağacının seed migration'ı
- [ ] `media`: `POST /uploads`, pre-signed URL kısıtları, `AssetProcessed`
- [ ] `lambdas/media-processor`: EXIF silme, WebP/1600px, pHash, Rekognition, sonuç SQS
- [ ] `content`: `Question` aggregate, durum makinesi (`mimari.md §10`) ve tüm geçiş testleri
- [ ] `content`: versiyonlama, `POST /questions`, `POST /questions/{id}/versions`, `withdraw`, `GET /me/questions`
- [ ] `content`: JSON Schema + KaTeX allowlist doğrulaması, metin normalizasyonu + `text_hash`
- [ ] `content`: `AnswerGrader` arayüzü + `McqGrader`
- [ ] `content`: v1'de durum yönlendirmesi geçici olarak "PENDING → IN_REVIEW" (itibar Faz 3'te bağlanır)
- [ ] `calibration`: `QuestionSubmitted` dinleyip `question_difficulty` satırını prior ile oluşturur (`mimari.md §8.2`, b_u = 0)
- [ ] Mobil: S8 Soru yükle (çok adımlı form, kamera/galeri + kırpma, LaTeX editör + canlı önizleme, şıklar, zorluk, konu, çözüm, özgünlük beyanı)
- [ ] Mobil: S9 Yüklediklerim (durum rozetleri)
- [ ] Admin: basit inceleme kuyruğu (IN_REVIEW listesi, önizleme, onayla/reddet + gerekçe)

### Kabul Kriterleri

- [ ] Görselli ve LaTeX'li bir soru mobilden yüklenir, admin'de aynı görünümle önizlenir, onaylanınca ACTIVE olur
- [ ] Geçersiz LaTeX makrosu (`\href`) içeren gönderim 400 + anlamlı `code` döner
- [ ] Görselin EXIF'i silinmiş, CloudFront'tan WebP olarak gelir; uygunsuz görsel REJECTED olur
- [ ] ACTIVE soruyu düzenlemek yeni versiyon oluşturur, eski versiyon değişmez
- [ ] Her ACTIVE soru için `calibration.question_difficulty` satırı doğru prior ile mevcuttur
- [ ] Durum makinesinin tüm geçerli/geçersiz geçişleri testle kapsanmıştır

---

## Faz 2 — Çözme + Kalibrasyon

**Amaç:** Ürünün çekirdeği: zorluk seçip çözme ve zorluğun veriyle öğrenilmesi.

### Görevler

- [ ] `practice.question_pool` projeksiyonu: `QuestionStatusChanged`, `QuestionVersionCreated`, `DifficultyUpdated`, `BucketsRecomputed` dinleyicileri + **yeniden kurma** komutu
- [ ] `practice`: oturum oluşturma, `QuestionSelector` arayüzü + `BucketRangeSelector` (`mimari.md §9`), wrap-around, `EXHAUSTED` + genişletme önerisi
- [ ] `practice`: `POST /attempts` (idempotent), `ContentApi.grade`, `CalibrationApi.predict`, `AttemptRecorded`
- [ ] `practice`: `user_question_state` güncelleme, yanlışlarım, favoriler, `GET /me/offline-pack`, `POST /attempts:batch`
- [ ] `calibration`: Elo güncellemesi (`mimari.md §8.3`) tüm istisnalarıyla, kilit sırası
- [ ] `calibration`: kova hesaplama (doğrusal + yüzdelik + histerezis), gece işi
- [ ] `calibration`: yükleyen yanlılığı (b_u) gece işi; yeni sorularda prior'a eklenmesi
- [ ] `calibration`: `ChallengeSelector` için `d*` hesabı; `practice`'te CHALLENGE modu ve p* adaptasyonu
- [ ] `calibration`: simülasyon testi (sentetik kullanıcı/soru, bilinen θ/d → tahmin hatası eşik altında)
- [ ] `calibration`: haftalık log-loss/AUC işi + metrik
- [ ] `stats`: `daily_activity`, `topic_stats`, `GET /me/stats`
- [ ] Mobil: S3 Ana sayfa, S4 Zorluk/konu seçimi, S5 Soru çözme, S6 Sonuç, S7 Oturum özeti, S10 Yanlışlarım/Favoriler (offline), S11 İstatistik
- [ ] Mobil: offline attempt kuyruğu ve senkronizasyon

### Kabul Kriterleri

- [ ] 10k soru ve 1M attempt'lik sentetik veride seçim sorgusu p95 < 50 ms (`EXPLAIN ANALYZE` PR'da)
- [ ] Aynı kullanıcıya PRACTICE modunda aynı soru tekrar gelmez; kendi sorusu gelmez
- [ ] Soru getirme yanıtında cevap anahtarı yoktur (sözleşme testi)
- [ ] Aynı `clientAttemptId` iki kez gönderildiğinde tek attempt oluşur, Elo bir kez güncellenir
- [ ] Simülasyon testinde 30 ilk denemeden sonra soru zorluğu ile gerçek d arasındaki korelasyon ≥ 0.8
- [ ] CHALLENGE modunda 50 soruluk simülasyonda gerçekleşen doğru oranı hedef p*'ın ±%10'u içinde
- [ ] Uçak modunda yanlışlarım açılır, çözülür; bağlantı gelince attempt'ler senkronlanır
- [ ] `question_pool` sıfırdan yeniden kurulabilir ve sonuç canlı projeksiyonla aynıdır

---

## Faz 3 — Güven

**Amaç:** Açık yüklemeyi güvenli hale getirmek. Bu faz bitmeden dış kullanıcıya açılmaz.

### Görevler

- [ ] `reputation`: defter, bakiye projeksiyonu, yeniden kurma komutu, `ReputationApi.hasPrivilege`
- [ ] `content`: PENDING yönlendirmesi itibara bağlanır (≥ 50 veya EDITOR → ACTIVE, değilse IN_REVIEW)
- [ ] `moderation`: `POST /questions/{id}/reports`, ağırlıklı şikayet skoru, FLAGGED eşiği
- [ ] `moderation`: `review_items` öncelikli kuyruk (NEW_SUBMISSION, FLAGGED, DUPLICATE_CANDIDATE, COPYRIGHT, KEY_SUSPECT)
- [ ] `moderation`: anahtar şüphesi gece işi (`mimari.md §10.2`)
- [ ] `moderation`: yaptırımlar (askıya alma, yükleme yasağı), `moderation_actions` denetim izi
- [ ] `content`: duplicate — birebir hash (REJECTED), pHash adayı (review_item)
- [ ] `content`: ÖSYM/yayınevi filtresi v1 (filigran ve anahtar ifade listesi; OCR'lı görseller için Lambda'da metin çıkarma)
- [ ] Rate limit: Bucket4j (Postgres), tüm yazma uç noktaları (`mimari.md §12`)
- [ ] `notification`: Expo Push; "sorun yayında / kaldırıldı / reddedildi" bildirimleri
- [ ] `identity`: `DELETE /me` (anonimleştirme olayı tüm modüllerde), `GET /me/export`
- [ ] Mobil: S13 Şikayet akışı, S12 Profil/Ayarlar (bildirimler, veri indir, hesap sil), S1 Onboarding (doğum yılı, aydınlatma)
- [ ] Admin: tam moderasyon paneli (kuyruk türleri, anahtar şüphesi kanıtları, duplicate karşılaştırma, yaptırımlar)
- [ ] Hukuki metin taslakları: kullanım şartları, aydınlatma metni, telif kaldırma süreci

### Kabul Kriterleri

- [ ] İtibarı 0 olan kullanıcının sorusu IN_REVIEW'a, 50 olanınki doğrudan ACTIVE'e gider
- [ ] Aynı metinli ikinci gönderim REJECTED olur; benzer görselli gönderim kuyruğa düşer
- [ ] 3 normal kullanıcı şikayeti soruyu FLAGGED yapar ve soru seçim havuzundan çıkar
- [ ] Bilerek yanlış anahtarlı sentetik soru, simüle edilmiş 30 ilk denemeden sonra KEY_SUSPECT olarak işaretlenir
- [ ] Rate limit aşımı 429 + `RATE_LIMITED` döner
- [ ] Hesap silindiğinde PII kolonları tüm modüllerde anonimleşir; attempt'ler anonim olarak kalır
- [ ] İtibar bakiyesi defterden sıfırdan yeniden kurulabilir ve aynı sonucu verir

---

## Faz 4 — Seed + Kapalı Beta

**Amaç:** Soğuk başlangıcı aşmak ve parametreleri gerçek veriyle ayarlamak.

### Görevler

- [ ] Editör hesapları; admin'de toplu soru girişi (aynı form, hızlı mod)
- [ ] Parametrik soru şablonu aracı (admin): aynı kalıp, farklı sayılar → çoklu özgün soru
- [ ] TYT Matematik: her kovada ≥ 100, toplam ≥ 1.500 özgün editör sorusu
- [ ] 20–50 kurucu yazar: davet, "Kurucu Yazar" rozeti, başlangıç itibarı (EDITOR değil, itibar ≥ 50)
- [ ] TestFlight + Play kapalı test; 1 okul/dershane grubu (hedef 100–300 öğrenci)
- [ ] Uygulama içi geri bildirim kanalı
- [ ] Kalibrasyon ayarı: log-loss/AUC'ye göre K, n0, s parametreleri; `calibration-log.md`
- [ ] Duplicate ve anahtar şüphesi eşiklerinin ilk 1.000 soruda ayarı
- [ ] Hukuki metinlerin avukat kontrolü; KVKK standart sözleşme imzası ve bildirimi
- [ ] Yük testi (k6): 200 eşzamanlı kullanıcı, çözme akışı

### Kabul Kriterleri

- [ ] Her kovada ≥ 100 aktif soru
- [ ] Kalibrasyon log-loss'u sabit %50 tahminine göre belirgin iyi ve haftadan haftaya kötüleşmiyor
- [ ] D7 retention ≥ %25, kullanıcı başına haftalık ≥ 30 çözüm (`proje.md §11`)
- [ ] Topluluk yüklemeleri toplamın ≥ %30'u
- [ ] Yük testinde p95 < 300 ms, hata oranı < %0.5
- [ ] Hukuki metinler onaylı, KVKK bildirimi yapılmış
- [ ] Kritik (P0/P1) açık hata yok

---

## Faz 5 — v1 Lansmanı

**Amaç:** Mağazalarda herkese açık yayın.

### Görevler

- [ ] Mağaza materyalleri (ekran görüntüleri, açıklama, gizlilik etiketleri, yaş derecelendirmesi)
- [ ] App Store / Play inceleme gereklilikleri: hesap silme, UGC şikayet/engelleme mekanizması, kullanım şartları kabulü
- [ ] Prod ortam: alarmlar (`mimari.md §18`), yedekten geri dönme tatbikatı
- [ ] Olay müdahale runbook'u (servis çöktü, telif bildirimi geldi, veri sızıntısı şüphesi)
- [ ] Kampanya/duyuru planı (tek sınav, tek ders hedef kitlesi)

### Kabul Kriterleri

- [ ] Her iki mağazada yayında
- [ ] RDS yedeğinden staging'e geri dönme tatbikatı başarılı, süre kayıtlı
- [ ] Tüm alarmlar test tetiklemesiyle doğrulandı
- [ ] Moderasyon kuyruğu yaşı ilk 2 hafta boyunca < 24 saat

---

## Faz 6 — v2

**Amaç:** Alışkanlık ve derinlik. Bu fazdaki her özellik `proje.md §6.2`'dedir ve şema değişikliği gerektirmemelidir (veri v1'den toplanıyor).

### Görevler (her biri ayrı PR dizisi; sıra veri ve geri bildirime göre belirlenir)

- [ ] Günlük seri ve hedef (`stats.daily_activity`)
- [ ] Zaman sınırlı mod (`SessionType.TIMED`, yeni `QuestionSelector`)
- [ ] Hata analizi (konu × çeldirici; `attempts.selected`)
- [ ] Çözüm sonrası zorluk oylaması (`calibration.difficulty_votes`) — oy, Elo'ya değil yükleyen yanlılığı analizine girer
- [ ] Aralıklı tekrar (`user_question_state.next_review_at`)
- [ ] Yükleyen profili ve takip
- [ ] Çözüm yorumları / alternatif çözüm (yeni modül → ADR)
- [ ] Sayısal cevap tipi (`mimari.md §20.2`)
- [ ] Anahtar doğrulama görevleri (itibar ≥ 200)
- [ ] Embedding tabanlı benzer soru tespiti (`EmbeddingPort` + Bedrock, pgvector HNSW)

### Kabul Kriterleri

- [ ] Yorumlar modülü dışında hiçbir v2 özelliği yeni tablo gerektirmedi (gerektirdiyse ADR ile gerekçelendi)
- [ ] Her özellik için kullanım metriği tanımlı ve dashboard'da

---

## Sonrası (planlanmadı, sıralanmadı)

Asenkron meydan okuma · Deneme sınavı oluşturucu · Öğretmen/sınıf hesapları · OCR ile fotoğraftan LaTeX · AI destekli çözüm doğrulama · Açık uçlu sorular · Diğer sınav ve dersler (AYT, KPSS) · Premium.

Kapsam dışı (`proje.md §6.4`): gerçek zamanlı düello, sohbet, sosyal akış.

---

## Değişiklik Kaydı

| Sürüm | Tarih | Değişiklik |
| --- | --- | --- |
| 1.0 | 2026-10-28 | İlk sürüm |
