# kurallar.md — Proje Kuralları

> Bu dosya onaylı v1.0 metnidir (NotebookLM kaynağından aktarıldı). Sonradan kabul edilen kararlar docs/adr/0013 ve sonrasındadır; çelişki halinde ADR geçerlidir.

**Durum:** Onaylı · **Sürüm:** 1.0 · **Tarih:** 2026-10-28

Kurallar bağlayıcıdır. "MUST" = istisnasız, "SHOULD" = gerekçesi PR'da yazılmadan çiğnenmez.

Bir kural CI ile otomatik kontrol ediliyorsa yanında **[CI]** yazar.

---

## 1. Doküman Kuralları

1. `proje.md`, `mimari.md`, `kurallar.md`, `fazlar.md` **tek doğruluk kaynağıdır**. Kod ile doküman çelişirse kod yanlıştır.
2. Mimariyi etkileyen her değişiklik (yeni modül, yeni bağımlılık, yeni teknoloji, şema prensibi, API kırılması) önce `docs/adr/NNNN-baslik.md` olarak yazılır, sonra kodlanır.
3. ADR şablonu: **Bağlam · Karar · Alternatifler · Sonuçlar · Durum** (Önerildi / Kabul / Yerini aldı: NNNN). Kabul edilmiş ADR silinmez, yalnızca yenisiyle geçersiz kılınır.
4. ADR kabul edilince ilgili ana dokümanın sürümü artırılır ve dokümanın en altındaki değişiklik kaydına bir satır eklenir.
5. Kalibrasyon parametresi değişiklikleri ADR istemez; `docs/calibration-log.md`'ye tarih, eski/yeni değer, gerekçe ve ölçüm yazılır.
6. Terimler `proje.md §4` sözlüğünden kullanılır. Yeni kavram önce sözlüğe eklenir.

## 2. Değişmez Mimari Kurallar (MUST)

| # | Kural | Kontrol |
| --- | --- | --- |
| A1 | Bir modül başka modülün `internal` paketini import edemez. | [CI] Modulith `verify()` |
| A2 | Modüller arası döngüsel bağımlılık yoktur. | [CI] Modulith |
| A3 | Bir modül yalnızca kendi şemasındaki tablolara erişir. Başka şemaya SQL yazılmaz. | [CI] ArchUnit + SQL lint (şema adı kontrolü) |
| A4 | Şemalar arası foreign key yoktur. | Migration review |
| A5 | `content.question_versions` ve `practice.attempts`, `moderation.moderation_actions`, `reputation.reputation_events` tablolarına UPDATE/DELETE yazılmaz. | [CI] ArchUnit (repository'de update metodu yok) + DB'de trigger |
| A6 | Soru içeriği değişikliği = yeni `QuestionVersion`. | Domain testi |
| A7 | Cevap anahtarı, cevaplanmamış soru yanıtında istemciye gönderilmez. | [CI] API contract testi |
| A8 | `domain` paketi Spring, JDBC, Jackson veya web sınıfı import etmez. | [CI] ArchUnit |
| A9 | Controller'da iş kuralı yoktur; controller yalnızca map eder ve use-case çağırır. | Review |
| A10 | Kimlik her yerde iç `UserId`'dir; IdP `sub` yalnızca `identity` modülünde görünür. | [CI] ArchUnit |
| A11 | Zorluk/itibar/rate limit eşikleri kodda sabit değildir, konfigürasyondadır. | Review |
| A12 | LaTeX yalnızca `QuestionBody` (mobil) ve `content-schema` render fonksiyonu (admin) ile render edilir. | Review |
| A13 | Seçim sorgularında `ORDER BY random()` ve `TABLESAMPLE` yasaktır. | [CI] grep |
| A14 | Durum geçişi yalnızca aggregate metodu (`transitionTo`) ile yapılır. | Review |

## 3. Kotlin / Backend Kuralları

### 3.1 Dil

- `!!` yasak. **[CI]** detekt
- `lateinit` yalnızca test sınıflarında.
- Kimlikler `@JvmInline value class` ile tiplenir: `UserId`, `QuestionId`, `VersionId`… Ham `UUID` modül sınırından geçmez.
- DTO ve olaylar değişmez `data class`; koleksiyonlar `List`/`Set` (mutable değil).
- Domain hataları `sealed interface` ile modellenir; beklenen iş hataları için exception fırlatılmaz, `Result`-benzeri dönüş kullanılır. Exception yalnızca beklenmeyen durumlar için.
- Zaman: yalnızca `Instant`; `LocalDateTime` yasak. Zaman `Clock` enjekte edilerek alınır (`Instant.now()` doğrudan çağrılmaz). **[CI]** detekt kuralı
- Rastgelelik `RandomSource` portu üzerinden (test edilebilirlik).
- Bağımlılık enjeksiyonu yalnızca constructor ile. Field injection yasak.
- `object` singleton ile durum tutulmaz.

### 3.2 Katmanlar ve Adlandırma

| Tür | Ad kalıbı | Örnek |
| --- | --- | --- |
| Modül API | `<Modul>Api` | `ContentApi` |
| Use-case | `<Fiil><Nesne>UseCase` veya `<Nesne>Service` | `SubmitQuestionUseCase` |
| Port (çıkış) | `<Nesne>Port` / `<Nesne>Repository` | `EmbeddingPort` |
| Adaptör | `<Teknoloji><Port>Adapter` | `BedrockEmbeddingAdapter` |
| Olay | `<Nesne><GeçmişZamanFiil>` | `QuestionSubmitted` |
| Controller | `<Nesne>Controller` | `PracticeController` |
| Konfigürasyon sınıfı | `<Modul>Properties` (`@ConfigurationProperties`) | `CalibrationProperties` |

- Paket adları küçük harf, tekil: `content.internal.domain`.
- Kod (sınıf, metod, değişken, DB kolonu) **İngilizce**; kullanıcıya görünen metinler **Türkçe** ve kaynak dosyalarda; yorumlar Türkçe olabilir.

### 3.3 Transaction ve Olaylar

- Transaction sınırı `application` katmanındaki use-case metodudur.
- Bir use-case tek aggregate'i değiştirir. Birden fazla aggregate/modül etkileniyorsa olay kullanılır.
- Olay dinleyicileri idempotent olmak **zorundadır** (doğal anahtar veya `UNIQUE` kısıt ile).
- Olay şeması değişikliği geriye uyumludur: alan eklenir, alan silinmez/yeniden adlandırılmaz.

### 3.4 Kalıcılık

- Spring Data JDBC repository'leri aggregate kökü başınadır.
- Karmaşık okuma sorguları `JdbcClient` ile `infrastructure/queries` altında, her biri test edilir.
- Optimistic locking (`lock_version`) değiştirilebilir aggregate'lerde zorunlu.
- N+1 sorgu yasak; liste uç noktaları sabit sayıda sorgu çalıştırır (test ile doğrulanır).

### 3.5 Konfigürasyon

- Her modülün ayarı `soruhavuzu.<modul>.*` altında, tipli `@ConfigurationProperties` + `@Validated`.
- Profiller: `local`, `test`, `staging`, `prod`. Profil içi `if` kod dallanması yasak; farklılık bean seçimiyle yapılır.

## 4. Veritabanı Kuralları

1. Her şema değişikliği Flyway migration'ıdır: `V<YYYYMMDDHHmm>__<modul>_<aciklama>.sql`. Uygulanmış migration **asla** düzenlenmez.
2. Migration'lar geriye uyumludur (expand → migrate → contract). Kolon silme/yeniden adlandırma en az bir sürüm sonra yapılır.
3. Tablo ve kolon adları `snake_case`, tablo adları çoğul.
4. Her tabloda PK zorunlu. ID: UUIDv7 (uygulamada üretilir).
5. Zaman kolonları `timestamptz`, adı `_at` ile biter.
6. Enum'lar `text` + `CHECK`; Postgres `ENUM` tipi kullanılmaz (değiştirmesi zor).
7. JSONB yalnızca: içerik (blok-JSON), cevap şeması, filtreler, olay/denetim payload'ı. Sorgulanan iş alanları kolon olur.
8. Yeni sorgu deseni = indeks gerekçesi PR'da `EXPLAIN ANALYZE` çıktısıyla.
9. Büyük tablolarda (`attempts`) indeks `CREATE INDEX CONCURRENTLY` ile ayrı migration'da.
10. Kişisel veri kolonları migration yorumunda `-- PII` ile işaretlenir; `UserDeleted` dinleyicisi bunları anonimleştirir.

## 5. API Kuralları

1. URL: `/v1/<kaynak-cogul>/{id}/<alt-kaynak>`, kebab-case. Eylem gerekiyorsa `POST /questions/{id}/withdraw`.
2. JSON alanları `camelCase`. Tarih ISO-8601 UTC.
3. Hata yanıtı RFC 9457 + `code`. Yeni `code` eklemek serbest, var olanı değiştirmek kırıcıdır.
4. Liste uç noktaları cursor sayfalıdır; `limit` max 100.
5. Yan etkili POST'lar idempotenttir.
6. Kırıcı değişiklik (alan silme, tip değiştirme, anlam değiştirme) yalnızca yeni sürümde (`/v2`).
7. Request/response modelleri domain nesnesi değildir; web katmanında ayrı tanımlanır.
8. OpenAPI'den `packages/api-client` üretilir; elle düzenleme yasak. **[CI]** üretim farkı = build hatası.
9. Her uç noktanın yetki kuralı OpenAPI açıklamasında yazar.

## 6. TypeScript / Mobil / Admin Kuralları

1. `strict: true`, `noUncheckedIndexedAccess: true`. `any` yasak, `as` cast'i gerekçe yorumu ister. **[CI]** eslint
2. Sunucu çağrıları yalnızca `@soruhavuzu/api-client` ile. Elle `fetch` yasak.
3. Sunucu durumu TanStack Query'de; Zustand yalnızca istemci UI durumu için.
4. Feature'lar birbirinin iç dosyasını import etmez (`eslint-plugin-boundaries`). **[CI]**
5. Kullanıcıya görünen her metin `i18n` anahtarıdır; JSX içinde düz Türkçe metin yasak. **[CI]**
6. Bileşenler fonksiyoneldir; dosya başına bir dışa aktarılan bileşen.
7. Form doğrulaması zod ile, şemalar `content-schema`'dan türetilir.
8. Erişilebilirlik: dokunulabilir öğelerde `accessibilityLabel`, minimum 44×44 dokunma alanı.
9. Görseller her zaman CloudFront URL'inden, `expo-image` ile ve cache'li.
10. Hassas veri yalnızca `expo-secure-store`'da; AsyncStorage'a token yazılmaz.

## 7. Test Kuralları

| Seviye | Araç | Zorunluluk |
| --- | --- | --- |
| Domain birim | JUnit 5 + Kotest assertions + MockK | Domain kodu satır kapsaması ≥ %80 **[CI]** |
| Modül entegrasyon | `@ApplicationModuleTest` + Testcontainers (Postgres) | Her use-case en az 1 senaryo |
| Mimari | Modulith `verify()` + ArchUnit | Her build **[CI]** |
| API sözleşme | MockMvc / RestAssured + OpenAPI şema doğrulama | Her uç nokta |
| Kalibrasyon | Simülasyon testi: bilinen θ/d ile üretilen sentetik veride tahmin hatası eşik altında | Kalibrasyon değişikliğinde |
| Mobil birim | Jest + React Native Testing Library | Hook ve iş mantığı |
| Mobil E2E | Maestro | Faz sonu kritik akışlar (çözme, yükleme) |
| Lambda | Vitest + örnek görseller | Her değişiklik |

- Testler birbirinden bağımsızdır; sıra bağımlılığı yok.
- Test adı davranış anlatır: `` `yazar kendi sorusunu çözerse zorluk güncellenmez` ``.
- Zaman ve rastgelelik testte sabitlenir (`Clock.fixed`, sabit seed).
- Hata düzeltmesi önce kırmızı test, sonra düzeltme.

## 8. Git ve Süreç Kuralları

1. **Trunk-based:** `main` her zaman deploy edilebilir. Dallar kısa ömürlü (≤ 3 gün): `feat/…`, `fix/…`, `chore/…`, `docs/…`, `adr/…`.
2. Commit: Conventional Commits, kapsam modül adıdır: `feat(practice): beni zorla modu seçici`.
3. `main`'e doğrudan push yasak; PR + yeşil CI zorunlu (tek geliştiricide de).
4. PR ≤ ~400 satır değişiklik (üretilen kod hariç). Büyük iş parçalara bölünür.
5. PR açıklaması: ne, neden, nasıl test edildi, ilgili faz maddesi, ADR (varsa), ekran görüntüsü (UI ise).
6. Sürümleme: backend ve mobil SemVer. Mobil sürümler EAS ile, OTA güncelleme yalnızca JS değişikliğinde.
7. `main`'e merge → otomatik `staging` deploy. `prod` deploy elle onaylı, tag ile.
8. Gizli bilgi commit edilirse: anahtar derhal döndürülür, geçmiş temizlenir, olay kaydı yazılır. **[CI]** gitleaks

## 9. Güvenlik Kuralları

1. Gizli bilgi yalnızca Secrets Manager'da; kodda, repoda, logda, hata mesajında yok.
2. Her uç nokta varsayılan olarak kimlik doğrulama ister; anonim uç nokta açıkça işaretlenir.
3. Sahiplik/yetki kontrolü use-case içinde yapılır; istemciden gelen `userId`'ye güvenilmez, token'dan alınır.
4. Kullanıcı girdisi her sınırda doğrulanır; içerik JSON Schema + KaTeX allowlist'ten geçmeden saklanmaz.
5. Loglarda PII, token, içerik gövdesi yok; `userId` hash'lenir.
6. Bağımlılık eklemek için: lisans uyumlu (MIT/Apache/BSD), aktif bakımda, bilinen kritik zafiyet yok. Yeni runtime bağımlılığı ADR ister.
7. Rate limit her yazma uç noktasında tanımlıdır.
8. S3 bucket'ları public değildir; okuma yalnızca CloudFront OAC.

## 10. İçerik ve Moderasyon Kuralları (ürün kuralları)

1. ÖSYM ve yayınevi soruları yasaktır. Yükleme formunda özgünlük beyanı zorunludur.
2. Çözüm açıklaması olmadan soru gönderilemez.
3. MCQ: 2–5 şık, tam olarak 1 doğru şık (v1). Şıklar boş veya birebir aynı olamaz.
4. Görsel: max 5 MB, jpeg/png/webp/heic, soru başına max 3.
5. Telif şikayetleri 48 saat içinde karara bağlanır.
6. Moderatör her kararda gerekçe girer; gerekçesiz karar kaydedilmez.
7. Kaldırılan sorunun yazarına sebebiyle bildirim gider.
8. Editör (seed) içeriği "Editör" etiketiyle görünür; sahte kullanıcı kimliğiyle içerik girilmez.
9. 18 yaş altı kullanıcıya pazarlama bildirimi gönderilmez, profilleme yapılmaz.

## 11. Yeni Özellik Ekleme Kontrol Listesi

Her yeni özellik PR'ında işaretlenir:

- [ ] `proje.md` kapsamında mı? Değilse önce `proje.md` güncellendi mi?
- [ ] Hangi modüle ait? Yeni modül gerekiyorsa ADR yazıldı mı? (`mimari.md §20`)
- [ ] Yeni veri: hangi şemada, migration geriye uyumlu mu?
- [ ] Modüller arası etkileşim olayla mı yapıldı? Olay kataloğu (`mimari.md §5.4`) güncellendi mi?
- [ ] Yeni eşik/parametre konfigürasyonda mı?
- [ ] API: OpenAPI güncel, istemci yeniden üretildi, yetki kuralı yazıldı mı?
- [ ] Rate limit tanımlandı mı?
- [ ] Testler: domain, modül entegrasyon, API sözleşme.
- [ ] Metinler i18n'de mi?
- [ ] Kişisel veri işleniyorsa: PII işareti, silme/anonimleştirme, aydınlatma metni etkisi.
- [ ] Gözlemlenebilirlik: gerekli metrik/log eklendi mi?
- [ ] `fazlar.md`'deki ilgili madde işaretlendi mi?

## 12. Definition of Done (her iş için)

- Kod `main`'de, CI yeşil (lint, detekt, test, Modulith/ArchUnit, OpenAPI farkı, gitleaks).
- `staging`'e deploy edildi ve el ile doğrulandı.
- Yeni davranışın testi var.
- İlgili doküman güncel.
- Bilinen eksik/teknik borç varsa GitHub Issue olarak açıldı ve PR'a bağlandı.

## 13. AI Asistanla Çalışma Kuralları

Bu repoda kod üreten her AI asistanı (Claude Code vb.) için:

1. İşe başlamadan önce `docs/proje.md`, `docs/mimari.md`, `docs/kurallar.md`, `docs/fazlar.md` okunur.
2. Yalnızca aktif fazdaki maddeler uygulanır; sonraki fazın işi "hazır olsun" diye yazılmaz.
3. Bu dokümanlarla çelişen bir çözüm gerekiyorsa kod yazılmaz; önce ADR taslağı önerilir.
4. Yeni bağımlılık eklenmez; gerekiyorsa gerekçesiyle önerilir.
5. Modül sınırları, değişmez tablolar ve terim sözlüğü korunur.
6. Üretilen kod bu dosyadaki kurallara uyar ve testiyle birlikte gelir.
7. Değişiklik sonunda hangi faz maddesinin karşılandığı ve hangi dokümanın güncellenmesi gerektiği belirtilir.

---

## Değişiklik Kaydı

| Sürüm | Tarih | Değişiklik |
| --- | --- | --- |
| 1.0 | 2026-10-28 | İlk sürüm |
