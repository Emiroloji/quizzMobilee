# mimari.md — Teknik Mimari

> Bu dosya onaylı v1.0 metnidir (NotebookLM kaynağından aktarıldı). Sonradan kabul edilen kararlar docs/adr/0013 ve sonrasındadır; çelişki halinde ADR geçerlidir.

**Durum:** Onaylı · **Sürüm:** 1.0 · **Tarih:** 2026-10-28

Bu dosya sistemin **nasıl** kurulduğunu tanımlar. Buradaki kararlar ancak `docs/adr/` altında yeni bir ADR ile değişir (`kurallar.md §1`).

---

## 1. Mimari İlkeler

1. **Modüler monolit, mikroservise hazır.** Tek deploy birimi; içeride sınırları derleme zamanında doğrulanan modüller. Her modül ileride ayrı servise kod değişmeden çıkarılabilecek şekilde tasarlanır.
2. **Modül = kendi verisinin sahibi.** Her modülün PostgreSQL'de kendi şeması vardır. Başka modülün tablosuna okuma/yazma yapılmaz; şemalar arası foreign key yoktur.
3. **Modüller arası iletişim:** Senkron ihtiyaçta modülün **public API arayüzü**, diğer her şeyde **domain olayları**.
4. **Değişmez içerik, eklenir kayıt.** Soru içeriği versiyonlanır, cevaplar append-only'dir. Geçmiş asla yeniden yazılmaz.
5. **Veriyle genişleme.** Yeni sınav, konu, soru tipi, mod; mümkün olduğunca kod değil kayıt/konfigürasyon ekleyerek gelir.
6. **Tek doğruluk kaynağı.** İçerik formatı tek bir JSON Schema'da, API sözleşmesi tek bir OpenAPI belgesinde tanımlanır; istemciler bundan üretilir.
7. **Sıkıcı teknoloji.** Olgun, iyi belgelenmiş araçlar. Her yeni bağımlılık ADR ister.

## 2. Teknoloji Yığını

| Katman | Seçim | Gerekçe |
| --- | --- | --- |
| Mobil | React Native + **Expo** (TypeScript, strict) | Mevcut React/TS bilgisi; iOS + Android; admin paneliyle kod/tip paylaşımı |
| Mobil yönlendirme | expo-router | Dosya tabanlı, deep link hazır |
| Mobil sunucu durumu | TanStack Query | Cache, retry, offline persist |
| Mobil yerel durum | Zustand | Minimal |
| Mobil offline DB | expo-sqlite + Drizzle | Tipli, migration'lı |
| Formlar | react-hook-form + zod | Şema tabanlı doğrulama |
| Matematik render | KaTeX (uygulamaya gömülü), tek WebView bileşeni | Olgun, offline çalışır |
| Admin paneli | React + Vite + TypeScript | Aynı tipler ve render paketi |
| Backend dili | **Kotlin 2.x** (JVM, Java 25 LTS) | Mevcut Java/Kotlin bilgisi, null-safety |
| Backend çatı | **Spring Boot 4.x** + **Spring Modulith 2.x** | Modül sınırı doğrulama, olay kaydı |
| Web | Spring MVC + virtual threads | Basit, bloklayan model; reaktif karmaşıklık yok |
| Kalıcılık | Spring Data JDBC + JdbcClient | Aggregate odaklı, lazy-loading sürprizi yok, SQL görünür |
| Migration | Flyway | Sürümlü, tekrar edilebilir |
| Veritabanı | PostgreSQL 17+ (AWS RDS) + `pgvector`, `ltree` | Tek veritabanı, şema başına modül |
| Rate limit | Bucket4j (v1: PostgreSQL backend, sonra Redis) | Port arkasında; backend değişimi kodu etkilemez |
| Zamanlanmış işler | Spring `@Scheduled` + ShedLock | Çoklu instance'ta tek çalışma |
| Kimlik | AWS Cognito (**Lite** plan) | Apple/Google/e-posta; 10k MAU ücretsiz |
| Dosya | S3 + CloudFront (OAC) | Pre-signed yükleme, CDN okuma |
| Asenkron medya işleme | S3 event → SQS → Lambda (Node/TS) | Görsel küçültme, EXIF, pHash, Rekognition |
| Görsel moderasyon | Amazon Rekognition `DetectModerationLabels` | Düşük maliyet |
| Embedding (v2) | Amazon Bedrock Titan Text Embeddings V2 (512 boyut) | `EmbeddingPort` arkasında |
| Gizli bilgi | AWS Secrets Manager | DB parolası, üçüncü taraf anahtarları |
| Push | Expo Push Service | APNs/FCM soyutlaması |
| Çalışma ortamı | ECS Fargate + ALB | Sunucu yönetimi yok |
| IaC | AWS CDK (TypeScript) | Tek dil (TS) altyapı + istemci |
| CI/CD | GitHub Actions → ECR → ECS; EAS Build (mobil) | |
| Gözlemlenebilirlik | OpenTelemetry Java agent → CloudWatch/X-Ray; yapılandırılmış JSON log | |
| Bölge | `eu-central-1` (Frankfurt) | Türkiye'ye yakın, tüm servisler mevcut |

Kesin sürümler Faz 0'da kilitlenir: backend `gradle/libs.versions.toml`, frontend `package.json` + `pnpm-lock.yaml`.

## 3. Sistem Görünümü

```text
┌──────────────┐        ┌───────────────┐
│ Mobil (Expo) │        │ Admin (Vite)  │
└──────┬───────┘        └──────┬────────┘
       │ HTTPS + JWT (Cognito) │
       ▼                       ▼
   ┌───────────────────────────────┐          ┌──────────────┐
   │  ALB → ECS Fargate            │◀────────▶│ RDS Postgres │
   │  soruhavuzu-api (Modulith)    │          │ (+pgvector)  │
   └───────┬───────────────▲───────┘          └──────────────┘
           │ pre-signed    │ SQS: media-results
           ▼ PUT URL       │
   ┌──────────────┐  event ┌─────────┐   ┌──────────────────────────┐
   │ S3 uploads/  │───────▶│ SQS     │──▶│ Lambda: media-processor  │
   └──────────────┘        │ media-in│   │ resize·EXIF·pHash·Rekog. │
                           └─────────┘   └──────────┬───────────────┘
   ┌──────────────┐                                 │ kopyalar
   │ S3 public/   │◀────────────────────────────────┘
   └──────┬───────┘
          ▼
     CloudFront (OAC) ──▶ istemciler görselleri buradan okur

Cognito (kimlik) · Secrets Manager · CloudWatch/X-Ray · Expo Push
```

## 4. Repo Yapısı (monorepo)

```text
soruhavuzu/
├── docs/
│   ├── proje.md  mimari.md  kurallar.md  fazlar.md
│   └── adr/                      # 0001-modular-monolith.md, ...
├── backend/                      # Kotlin, Gradle
├── mobile/                       # Expo
├── admin/                        # React + Vite
├── lambdas/
│   └── media-processor/          # Node/TS
├── packages/                     # pnpm workspace paketleri
│   ├── content-schema/           # Blok-JSON JSON Schema + zod + KaTeX makro listesi + HTML render
│   └── api-client/               # OpenAPI'den üretilen TS istemci (elle düzenlenmez)
├── infra/                        # AWS CDK (TS)
├── .github/workflows/
└── README.md
```

## 5. Backend Modülleri

### 5.1 Modül Listesi

| Modül | Şema | Sorumluluk | Sahip olduğu veri |
| --- | --- | --- | --- |
| `shared` | — | Ortak çekirdek: ID tipleri, `Clock`, hata tipleri, sayfalama. **İş mantığı yok.** | — |
| `identity` | `identity` | Kullanıcı, IdP eşleme, rol, cihaz/push token, hesap silme | `users`, `auth_identities`, `devices` |
| `taxonomy` | `taxonomy` | Sınav ve konu ağacı | `exams`, `topics` |
| `media` | `media` | Varlık kaydı, pre-signed URL, medya işleme sonuçları | `assets` |
| `content` | `content` | Soru, versiyon, durum, cevap değerlendirme, içerik doğrulama, duplicate | `questions`, `question_versions`, `question_topics` |
| `practice` | `practice` | Oturum, soru seçimi, cevap kaydı, yanlışlarım/favoriler, offline paket | `sessions`, `attempts`, `user_question_state`, `question_pool` |
| `calibration` | `calibration` | Elo, prior, kova hesaplama, yükleyen yanlılığı, zorluk oyları | `question_difficulty`, `user_skill`, `uploader_bias`, `difficulty_votes` |
| `moderation` | `moderation` | Şikayet, inceleme kuyruğu, anahtar şüphesi, yaptırım | `reports`, `review_items`, `key_suspects`, `moderation_actions`, `sanctions` |
| `reputation` | `reputation` | İtibar defteri ve bakiye, yetki kapıları | `reputation_events`, `reputation_balances` |
| `stats` | `stats` | Kullanıcı istatistikleri, günlük aktivite | `daily_activity`, `topic_stats` |
| `notification` | `notification` | Push gönderimi, bildirim kaydı | `notifications` |

### 5.2 İzinli Bağımlılık Yönü

Ok "çağırabilir / API'sini kullanabilir" demektir. Döngü yasaktır, Modulith testi doğrular.

```text
            identity ◀──────────────┬──────────────┬─────────────┐
            taxonomy ◀──────┐       │              │             │
            media    ◀──┐   │       │              │             │
                        │   │       │              │             │
                     content ◀──────┼──── moderation ──▶ reputation
                        ▲           │         │
                        │           │         ▼
                     practice ──▶ calibration
                        
stats, notification: sadece olay dinler, kimseyi çağırmaz (identity hariç)
```

| Modül | Senkron çağırabileceği modüller |
| --- | --- |
| `content` | identity, taxonomy, media, reputation (yetki kapısı sorgusu) |
| `practice` | content, calibration, taxonomy, identity |
| `calibration` | — (sadece olay dinler; `CalibrationApi` sunar) |
| `moderation` | content, identity, reputation |
| `reputation` | — |
| `stats` | taxonomy |
| `notification` | identity |

### 5.3 Modül İç Yapısı

Spring Modulith'te modülün kök paketi **public**, alt paketleri **internal**'dır.

```text
com.soruhavuzu.content/
├── ContentApi.kt                 # PUBLIC: diğer modüllerin çağırdığı arayüz
├── ContentDtos.kt                # PUBLIC: API'nin taşıdığı değişmez veri tipleri
├── ContentEvents.kt              # PUBLIC: bu modülün yayınladığı olaylar
├── package-info.kt / ModuleMetadata.kt  # @ApplicationModule(allowedDependencies = [...])
└── internal/
    ├── domain/                   # Aggregate, value object, domain servisleri. Spring bağımlılığı yok.
    ├── application/              # Use-case servisleri (transaction sınırı burada), ContentApi implementasyonu
    ├── infrastructure/           # Repository, JdbcClient sorguları, dış adaptörler
    └── web/                      # Controller, request/response modelleri, mapper
```

**Katman kuralları:**

- `domain` hiçbir şeye bağımlı değildir (Spring, JDBC, web yok).
- `application` → `domain`. Dışa açılan **port** arayüzlerini tanımlar; `infrastructure` bunları uygular.
- `web` → `application`. Controller'da iş kuralı yoktur.
- Başka modüle erişim yalnızca `application` katmanından, karşı modülün kök paketindeki API/DTO/olaylar üzerinden.

### 5.4 Olaylar

- Olaylar geçmiş zamanla adlandırılır, değişmez `data class`'tır, yalnızca ID ve gerekli skaler değerleri taşır (aggregate taşımaz).
- Yayın: `ApplicationEventPublisher`, aynı transaction içinde. Spring Modulith **Event Publication Registry** olayları `event_publication` tablosunda tutar (outbox); dinleyici başarısız olursa yeniden işlenir.
- Dinleyiciler `@ApplicationModuleListener` (commit sonrası, asenkron, kendi transaction'ı) ile yazılır ve **idempotent** olmak zorundadır.
- Bir modül servise ayrıldığında olay, Modulith externalization ile SQS'e yönlendirilir; iş kodu değişmez.

**Olay kataloğu (v1):**

| Olay | Yayınlayan | Dinleyenler |
| --- | --- | --- |
| `UserRegistered(userId)` | identity | reputation, stats |
| `UserDeleted(userId)` | identity | tümü (anonimleştirme) |
| `AssetProcessed(assetId, status, phash, labels)` | media | content |
| `QuestionSubmitted(questionId, versionId, authorId)` | content | moderation, calibration |
| `QuestionStatusChanged(questionId, from, to, reason)` | content | practice, reputation, notification, calibration |
| `QuestionVersionCreated(questionId, versionId, optionCount)` | content | practice, calibration |
| `AttemptRecorded(attemptId, userId, questionId, versionId, isCorrect, isFirst, durationMs, sessionType, selected, pExpected)` | practice | calibration, stats, moderation |
| `DifficultyUpdated(questionId, difficultyLogit, bucket)` | calibration | practice |
| `BucketsRecomputed(changes: List<...>)` | calibration | practice |
| `ReportFiled(reportId, questionId, reason, weight)` | moderation | — (iç) |
| `QuestionFlagged(questionId, reason)` | moderation | content (durum değiştirir) |
| `ReportResolved(reportId, reporterId, upheld)` | moderation | reputation |
| `KeySuspectDetected(questionId, evidence)` | moderation | notification |
| `ReputationChanged(userId, newBalance)` | reputation | — |

## 6. Veri Modeli

Genel kurallar: ID'ler uygulamada üretilen **UUIDv7**; zamanlar `timestamptz` (UTC); para/oran yok; enum'lar `text` + `CHECK`; şemalar arası FK yok (ID ile referans).

```sql
-- ============ identity ============
CREATE TABLE identity.users (
  id            uuid PRIMARY KEY,
  handle        text NOT NULL UNIQUE,              -- takma ad
  birth_year    smallint,                          -- 18 yaş altı kontrolü
  role          text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','EDITOR','MODERATOR','ADMIN')),
  status        text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
  target_exam_id uuid,
  created_at    timestamptz NOT NULL,
  deleted_at    timestamptz
);
-- IdP'den bağımsızlık: Cognito değişirse yalnızca bu tablo etkilenir
CREATE TABLE identity.auth_identities (
  provider  text NOT NULL,                         -- 'cognito'
  subject   text NOT NULL,                         -- IdP 'sub'
  user_id   uuid NOT NULL REFERENCES identity.users(id),
  PRIMARY KEY (provider, subject)
);
CREATE TABLE identity.devices (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES identity.users(id),
  push_token text NOT NULL UNIQUE, platform text NOT NULL, updated_at timestamptz NOT NULL
);

-- ============ taxonomy ============
CREATE TABLE taxonomy.exams (
  id uuid PRIMARY KEY, code text NOT NULL UNIQUE, name text NOT NULL, active boolean NOT NULL
);
CREATE TABLE taxonomy.topics (
  id uuid PRIMARY KEY,
  exam_id uuid NOT NULL REFERENCES taxonomy.exams(id),
  parent_id uuid REFERENCES taxonomy.topics(id),
  name text NOT NULL,
  path ltree NOT NULL,                             -- hızlı alt ağaç sorgusu
  sort_order int NOT NULL DEFAULT 0
);
CREATE INDEX ix_topics_path ON taxonomy.topics USING gist (path);

-- ============ media ============
CREATE TABLE media.assets (
  id uuid PRIMARY KEY, owner_id uuid NOT NULL,
  s3_key text NOT NULL, public_url text,
  status text NOT NULL CHECK (status IN ('UPLOADING','PROCESSING','READY','REJECTED')),
  mime text, width int, height int, bytes int,
  phash bit(64), moderation jsonb,
  created_at timestamptz NOT NULL
);

-- ============ content ============
CREATE TABLE content.questions (
  id                 uuid PRIMARY KEY,
  author_id          uuid NOT NULL,
  exam_id            uuid NOT NULL,
  primary_topic_id   uuid NOT NULL,
  status             text NOT NULL CHECK (status IN
                       ('DRAFT','PENDING','IN_REVIEW','ACTIVE','FLAGGED','REJECTED','REMOVED')),
  current_version_id uuid,
  uploader_rating    smallint NOT NULL CHECK (uploader_rating BETWEEN 1 AND 10),  -- ham, değişmez
  originality_declared_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  lock_version int NOT NULL DEFAULT 0              -- optimistic locking
);
CREATE INDEX ix_questions_author ON content.questions (author_id, created_at DESC);
CREATE INDEX ix_questions_status ON content.questions (status) WHERE status IN ('PENDING','IN_REVIEW','FLAGGED');

-- DEĞİŞMEZ: UPDATE/DELETE yasak (kurallar.md)
CREATE TABLE content.question_versions (
  id            uuid PRIMARY KEY,
  question_id   uuid NOT NULL REFERENCES content.questions(id),
  version       int NOT NULL,
  body          jsonb NOT NULL,                    -- blok-JSON (§7)
  answer_spec   jsonb NOT NULL,                    -- tip bazlı cevap şeması
  solution      jsonb NOT NULL,                    -- blok-JSON
  question_type text NOT NULL CHECK (question_type IN ('mcq','numeric','open')),
  option_count  smallint,                          -- mcq için; tahmin düzeltmesi
  text_hash     bytea NOT NULL,                    -- normalize metin SHA-256
  embedding     vector(512),                       -- v2
  schema_version smallint NOT NULL,
  created_by    uuid NOT NULL,
  created_at    timestamptz NOT NULL,
  UNIQUE (question_id, version)
);
CREATE INDEX ix_qv_hash ON content.question_versions (text_hash);

CREATE TABLE content.question_topics (
  topic_id uuid NOT NULL, question_id uuid NOT NULL REFERENCES content.questions(id),
  PRIMARY KEY (topic_id, question_id)
);
CREATE TABLE content.question_assets (
  question_version_id uuid NOT NULL REFERENCES content.question_versions(id),
  asset_id uuid NOT NULL, PRIMARY KEY (question_version_id, asset_id)
);

-- ============ practice ============
CREATE TABLE practice.sessions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL,
  session_type text NOT NULL CHECK (session_type IN ('PRACTICE','CHALLENGE','TIMED','REVIEW','OFFLINE_REVIEW')),
  filter jsonb NOT NULL,                           -- {examId, topicIds, bucketMin, bucketMax}
  target_p real,                                   -- CHALLENGE için
  started_at timestamptz NOT NULL, ended_at timestamptz
);

-- APPEND-ONLY: UPDATE/DELETE yasak. Aylık partition'a hazır.
CREATE TABLE practice.attempts (
  id                uuid NOT NULL,
  client_attempt_id uuid NOT NULL,                 -- idempotency
  user_id           uuid NOT NULL,
  question_id       uuid NOT NULL,
  version_id        uuid NOT NULL,
  session_id        uuid,
  session_type      text NOT NULL,
  selected          jsonb NOT NULL,                -- {"options":["B"]} | {"value":2.5}
  is_correct        boolean NOT NULL,
  is_first          boolean NOT NULL,
  duration_ms       int NOT NULL,
  theta_before      real, d_before real, p_expected real,  -- model değerlendirmesi için
  created_at        timestamptz NOT NULL,
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE UNIQUE INDEX ux_attempts_client ON practice.attempts (client_attempt_id, created_at);
CREATE INDEX ix_attempts_user ON practice.attempts (user_id, created_at DESC);
CREATE INDEX ix_attempts_question ON practice.attempts (question_id) WHERE is_first;

CREATE TABLE practice.user_question_state (
  user_id uuid NOT NULL, question_id uuid NOT NULL,
  solved boolean NOT NULL DEFAULT false,
  last_correct boolean, wrong_count int NOT NULL DEFAULT 0,
  favorite boolean NOT NULL DEFAULT false,
  next_review_at timestamptz,                      -- v2 aralıklı tekrar
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, question_id)
);
CREATE INDEX ix_uqs_mistakes ON practice.user_question_state (user_id, updated_at DESC) WHERE wrong_count > 0;
CREATE INDEX ix_uqs_fav ON practice.user_question_state (user_id) WHERE favorite;

-- PROJEKSİYON: content + calibration olaylarından beslenir. Yalnızca ACTIVE sorular.
CREATE TABLE practice.question_pool (
  question_id        uuid PRIMARY KEY,
  current_version_id uuid NOT NULL,
  author_id          uuid NOT NULL,
  exam_id            uuid NOT NULL,
  primary_topic_id   uuid NOT NULL,
  topic_path         ltree NOT NULL,
  difficulty_logit   real NOT NULL,
  difficulty_bucket  smallint NOT NULL,
  random_key         double precision NOT NULL DEFAULT random()
);
CREATE INDEX ix_pool_pick   ON practice.question_pool (exam_id, difficulty_bucket, random_key);
CREATE INDEX ix_pool_logit  ON practice.question_pool (exam_id, difficulty_logit);
CREATE INDEX ix_pool_topic  ON practice.question_pool USING gist (topic_path);

-- ============ calibration ============
CREATE TABLE calibration.question_difficulty (
  question_id      uuid PRIMARY KEY,
  option_count     smallint,                       -- null = tahmin düzeltmesi yok
  prior_logit      real NOT NULL,
  difficulty_logit real NOT NULL,
  n                int NOT NULL,                   -- n0 ile başlar
  first_attempts   int NOT NULL DEFAULT 0,
  first_correct    int NOT NULL DEFAULT 0,
  difficulty_bucket smallint NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE TABLE calibration.user_skill (
  user_id uuid NOT NULL,
  scope_id uuid NOT NULL,                          -- exam_id (global) veya topic_id
  scope_type text NOT NULL CHECK (scope_type IN ('EXAM','TOPIC')),
  theta real NOT NULL DEFAULT 0, n int NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, scope_id)
);
CREATE TABLE calibration.uploader_bias (
  user_id uuid PRIMARY KEY, bias real NOT NULL DEFAULT 0, m int NOT NULL DEFAULT 0
);
CREATE TABLE calibration.difficulty_votes (               -- v2 özelliği, tablo v1'de
  user_id uuid NOT NULL, question_id uuid NOT NULL, vote smallint NOT NULL CHECK (vote BETWEEN 1 AND 10),
  created_at timestamptz NOT NULL, PRIMARY KEY (user_id, question_id)
);

-- ============ moderation ============
CREATE TABLE moderation.reports (
  id uuid PRIMARY KEY, question_id uuid NOT NULL, version_id uuid NOT NULL, reporter_id uuid NOT NULL,
  reason text NOT NULL CHECK (reason IN ('WRONG_KEY','COPYRIGHT','INAPPROPRIATE','DUPLICATE','UNCLEAR','OTHER')),
  note text, weight smallint NOT NULL,
  status text NOT NULL CHECK (status IN ('OPEN','UPHELD','DISMISSED')),
  resolved_by uuid, resolved_at timestamptz, created_at timestamptz NOT NULL,
  UNIQUE (question_id, reporter_id)
);
CREATE TABLE moderation.review_items (
  id uuid PRIMARY KEY, question_id uuid NOT NULL,
  kind text NOT NULL CHECK (kind IN ('NEW_SUBMISSION','FLAGGED','KEY_SUSPECT','DUPLICATE_CANDIDATE','COPYRIGHT')),
  priority smallint NOT NULL, status text NOT NULL CHECK (status IN ('OPEN','DONE')),
  payload jsonb, assigned_to uuid, created_at timestamptz NOT NULL, closed_at timestamptz
);
CREATE INDEX ix_review_open ON moderation.review_items (priority DESC, created_at) WHERE status = 'OPEN';
CREATE TABLE moderation.moderation_actions (           -- denetim izi, append-only
  id uuid PRIMARY KEY, target_type text NOT NULL, target_id uuid NOT NULL, actor_id uuid NOT NULL,
  action text NOT NULL, from_status text, to_status text, reason text, created_at timestamptz NOT NULL
);
CREATE TABLE moderation.sanctions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, kind text NOT NULL, until timestamptz, reason text,
  created_by uuid NOT NULL, created_at timestamptz NOT NULL
);

-- ============ reputation ============
CREATE TABLE reputation.reputation_events (            -- defter, append-only
  id uuid PRIMARY KEY, user_id uuid NOT NULL, kind text NOT NULL, delta int NOT NULL,
  ref_id uuid, created_at timestamptz NOT NULL,
  UNIQUE (kind, ref_id, user_id)                       -- aynı olay iki kez yazılmaz (idempotency)
);
CREATE TABLE reputation.reputation_balances (           -- projeksiyon, defterden yeniden kurulabilir
  user_id uuid PRIMARY KEY, balance int NOT NULL, updated_at timestamptz NOT NULL
);

-- ============ stats ============
CREATE TABLE stats.daily_activity (
  user_id uuid NOT NULL, day date NOT NULL, solved int NOT NULL, correct int NOT NULL,
  PRIMARY KEY (user_id, day)
);
CREATE TABLE stats.topic_stats (
  user_id uuid NOT NULL, topic_id uuid NOT NULL, attempts int NOT NULL, correct int NOT NULL,
  PRIMARY KEY (user_id, topic_id)
);

-- ============ notification ============
CREATE TABLE notification.notifications (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, kind text NOT NULL, payload jsonb NOT NULL,
  sent_at timestamptz, read_at timestamptz, created_at timestamptz NOT NULL
);
```

## 7. İçerik Formatı (Blok-JSON)

Tek kaynak: `packages/content-schema/schema.json` (JSON Schema). Backend aynı dosyayla doğrular (networknt json-schema-validator), TS tarafı zod şemasını buradan türetir.

```jsonc
// body / solution / option.body
{
  "schema": 1,
  "blocks": [
    { "t": "p", "c": [
        { "t": "text", "v": "f(x) = " },
        { "t": "math", "v": "\\frac{x^2-1}{x-1}" },          // satır içi LaTeX
        { "t": "text", "v": " ise " },
        { "t": "math", "v": "\\lim_{x\\to1} f(x)" },
        { "t": "text", "v": " kaçtır?" } ] },
    { "t": "math_block", "v": "\\int_0^1 x\\,dx" },          // blok LaTeX
    { "t": "image", "asset_id": "0192f0c4-…", "alt": "Şekil 1" }
  ]
}
```

```jsonc
// answer_spec
{ "type": "mcq", "options": [ { "id": "A", "body": { /* blok-JSON */ } }, … ],
  "correct": ["B"], "shuffle": true }
{ "type": "numeric", "value": 2.5, "tolerance": 0.01 }       // v2
{ "type": "open", "rubric": { … } }                           // sonrası
```

**Kurallar:**

- LaTeX **kaynak** olarak saklanır, render edilmiş HTML/SVG saklanmaz.
- İzinli KaTeX makroları `packages/content-schema/katex-allowlist.json`'dadır. `\href`, `\url`, `\includegraphics`, `\htmlClass`, `\html*` ve trust gerektiren her şey reddedilir. Sunucu yükleme anında doğrular.
- Yeni blok tipi eklemek = şemaya tip ekle + render'a ekle + `schema` sürümünü artır. Eski sürümler render edilmeye devam eder.
- Cevap anahtarı (`answer_spec.correct`) soru getirme yanıtlarında **asla** istemciye gönderilmez; değerlendirmeyi sunucu yapar.

## 8. Zorluk Kalibrasyonu

Tüm parametreler `application.yml` altında `calibration.*` olarak konfigüre edilir, kodda sabit yoktur.

### 8.1 Model

```text
σ(x) = 1 / (1 + e^(−x))
k    = şık sayısı (numeric için k = ∞ → 1/k = 0)
P(doğru) = 1/k + (1 − 1/k) · σ(θ_eff − d)
θ_eff    = θ_exam + θ_topic(primary_topic)
```

### 8.2 Prior (yükleyen puanı)

```text
d0 = (r − 5.5) · s + b_u                  s = calibration.prior.scale = 0.6
b_u = Σ(d_i − d0_i) / (m + m0)            m0 = calibration.bias.shrink = 5   (az veride 0'a büzülür)
Başlangıç: d = d0, n = n0                  n0 = calibration.prior.virtualObservations = 5
```

### 8.3 Güncelleme (yalnızca uygun ilk denemelerde)

```text
c ∈ {0,1}
K_q(n) = a / (1 + b·n)                     a = 1.0, b = 0.05
K_u(n) = max(K_min, a / (1 + b·n))         K_min = 0.1   (öğrenme izlenebilsin)
d        ← d + K_q · (P − c)
θ_exam   ← θ_exam  + K_u · (c − P)
θ_topic  ← θ_topic + K_u · (c − P)
n ← n + 1
```

Güncelleme **yapılmaz** veya **azaltılır**:

| Durum | Etki |
| --- | --- |
| `is_first = false` | d güncellenmez; θ güncellenir |
| Yükleyen kendi sorusu | Hiçbiri güncellenmez |
| Kullanıcının toplam ilk denemesi < 10 | d güncellenmez (θ henüz belirsiz); θ güncellenir |
| `duration_ms < 3000` | K'lar yarıya iner |
| `OFFLINE_REVIEW` | K'lar × 0.25, d güncellenmez |

**Eşzamanlılık:** `calibration` her `AttemptRecorded` için tek transaction'da önce soru, sonra kullanıcı satırını `SELECT … FOR UPDATE` ile kilitler (sabit sıra → deadlock yok). Çekişme ölçülürse soru güncellemeleri kuyrukta batch'lenir; şema değişmez.

### 8.4 Kova (1–10)

- Aktif soru sayısı < `calibration.bucket.percentileThreshold` (500): `bucket = clamp(round(1 + 9·(d + 3)/6), 1, 10)`
- Üstünde: gece işi, sınav bazında aktif soruların d yüzdeliği p → `bucket = 1 + floor(9.999·p)`
- Histerezis: kova ancak yeni değer sınırı 0.3 kova aştığında değişir.
- Gece işi `BucketsRecomputed` yayınlar; `practice.question_pool` güncellenir.

### 8.5 Beni Zorla

```text
d* = θ_eff − logit( (p* − 1/k) / (1 − 1/k) )
p* başlangıç = 0.60 · 3 doğru üst üste → 0.50 · 3 yanlış üst üste → 0.75
Aday aralığı: d ∈ [d* − 0.3, d* + 0.3]; boşsa ±0.3 adımlarla genişlet (max ±1.2)
```

k soruya göre değiştiği için seçimde `k = 5` varsayılır; kesin P değerlendirmede hesaplanır.

### 8.6 Model Sağlığı

Haftalık iş: son 7 günün ilk denemelerinde `p_expected` ile `is_correct` arasından log-loss ve AUC hesaplanır, loglanır ve metrik olarak yayınlanır. Parametre değişikliği ADR gerektirmez, ama `docs/calibration-log.md`'ye gerekçesiyle yazılır.

## 9. Soru Seçimi

`practice` modülü yalnızca kendi şemasını sorgular.

```sql
-- PRACTICE: kova aralığı + konu + çözülmemiş + kendi sorusu değil
-- :r uygulamada üretilir (0..1)
SELECT p.question_id, p.current_version_id
FROM practice.question_pool p
WHERE p.exam_id = :examId
  AND p.difficulty_bucket BETWEEN :bucketMin AND :bucketMax
  AND (:topicPath IS NULL OR p.topic_path <@ :topicPath::ltree)
  AND p.author_id <> :userId
  AND p.random_key >= :r
  AND NOT EXISTS (
        SELECT 1 FROM practice.user_question_state s
        WHERE s.user_id = :userId AND s.question_id = p.question_id AND s.solved)
ORDER BY p.random_key
LIMIT :batch;            -- batch = 20
-- Sonuç < batch ise aynı sorgu `p.random_key < :r` ile tekrar (wrap-around).
-- İkisi de boşsa API "EXHAUSTED" + önerilen genişletilmiş aralık döner.
```

- `ORDER BY random()` ve `TABLESAMPLE` **yasaktır**.
- CHALLENGE modunda kova yerine `difficulty_logit BETWEEN :lo AND :hi` (`ix_pool_logit`).
- Ölçek eşiği: kullanıcı başına çözülen > 5.000 olduğunda anti-join yerine Redis'te "son çözülenler" seti; port arkasında, API değişmez.

## 10. Soru Durum Makinesi

```text
DRAFT ──submit──▶ PENDING
PENDING ──(oto. kontroller OK ∧ itibar ≥ 50 ∨ EDITOR)──▶ ACTIVE
PENDING ──(oto. kontroller OK ∧ itibar < 50)──▶ IN_REVIEW
PENDING ──(NSFW ∨ kesin duplicate ∨ ÖSYM eşleşmesi)──▶ REJECTED
IN_REVIEW ──moderatör onay──▶ ACTIVE
IN_REVIEW ──moderatör red──▶ REJECTED
ACTIVE ──(ağırlıklı şikayet ≥ 3 ∨ anahtar şüphesi ∨ moderatör)──▶ FLAGGED
FLAGGED ──moderatör düzeltme (yeni versiyon)──▶ ACTIVE
FLAGGED ──moderatör kaldır──▶ REMOVED
ACTIVE ──yazar düzenleme (yeni versiyon)──▶ PENDING
ACTIVE/IN_REVIEW ──yazar geri çeker──▶ REMOVED
```

- Geçişler yalnızca `content` aggregate'indeki `transitionTo(target, actor, reason)` ile yapılır; geçersiz geçiş domain hatasıdır.
- Her geçiş `QuestionStatusChanged` yayınlar ve `moderation_actions`'a denetim kaydı düşer.
- `REMOVED` soft-delete'tir; attempt'ler korunur.
- Duplicate adayı (kesin olmayan eşleşme) ve telif şikayeti, `review_items` içinde yüksek öncelikle açılır.

### 10.1 Otomatik Kontroller (PENDING)

| Kontrol | Yöntem | Sonuç |
| --- | --- | --- |
| İçerik şeması | JSON Schema + KaTeX allowlist | Senkron red (400) |
| Birebir kopya | normalize metin SHA-256 | REJECTED |
| Görsel kopya | pHash, Hamming ≤ 8 | review_item `DUPLICATE_CANDIDATE` |
| Görsel uygunsuz | Rekognition | Asset REJECTED → soru REJECTED |
| ÖSYM/yayınevi | Filigran/anahtar ifade listesi + (v2) embedding benzerliği | REJECTED veya `COPYRIGHT` review_item |
| Rate limit | Bucket4j | 429 |

### 10.2 Anahtar Şüphesi (gece işi, n_first ≥ 30)

Her şık j için `r_j = corr(θ_eff, 1[seçim = j])`.

- Kural 1: `r_key < 0` ∧ ∃ j≠key: `r_j > 0.15`
- Kural 2: θ üst çeyreğinde ∃ j≠key seçilme oranı > key'in seçilme oranı
- Kural 3: `d − prior > 2.5` ∧ ilk deneme doğru oranı ≤ 1/k + 0.05

Herhangi biri → `KeySuspectDetected` → soru FLAGGED + review_item `KEY_SUSPECT`.

## 11. İtibar

Defter (`reputation_events`) tek kaynaktır; bakiye projeksiyondur ve her zaman defterden yeniden kurulabilir.

| Olay | Delta |
| --- | --- |
| Sorun ACTIVE oldu | +5 |
| Sorun 20 ilk denemeye ulaştı (soru başına en fazla 5 kez) | +1 |
| Sorun REMOVED (telif / yanlış anahtar / uygunsuz) | −20 |
| Şikayetin haklı bulundu | +2 |
| Şikayetin haksız bulundu | −1 |

- Kapılar: `ReputationApi.hasPrivilege(userId, Privilege)` üzerinden sorulur; eşikler konfigürasyondadır (`proje.md §5`).
- Şikayet ağırlığı: itibar < 200 → 1, ≥ 200 → 2, MODERATOR → 3.

## 12. Rate Limit (v1 varsayılanları, konfigürasyonda)

| İşlem | Yeni (itibar < 50) | ≥ 50 | ≥ 200 |
| --- | --- | --- | --- |
| Soru gönderme / gün | 5 | 20 | 50 |
| Şikayet / gün | 10 | 20 | 50 |
| Attempt / dakika | 30 | 30 | 30 |
| Upload URL / saat | 20 | 60 | 120 |
| Hesap açma / IP / gün | 5 | — | — |

## 13. Medya Yükleme Akışı

```text
1. İstemci  POST /v1/uploads {mime, bytes}
2. media    asset(UPLOADING) oluşturur, 5 dk geçerli pre-signed PUT döner
            (key = uploads/{userId}/{assetId}, content-type ve content-length-range kısıtlı, max 5 MB,
             yalnızca image/jpeg|png|webp|heic)
3. İstemci  S3'e PUT
4. S3 event → SQS media-in → Lambda media-processor:
            decode → EXIF sil → max 1600px WebP → pHash → Rekognition
            → public/{assetId}.webp yazar → SQS media-results'a sonuç
5. media    sonucu tüketir → asset READY/REJECTED → AssetProcessed yayınlar
6. content  soru gönderiminde yalnızca READY ve sahibi kendisi olan asset'leri kabul eder
```

`uploads/` öneki 24 saatlik lifecycle ile temizlenir. `public/` yalnızca CloudFront OAC ile okunur; bucket public değildir.

## 14. Kimlik ve Yetkilendirme

- İstemci Cognito'dan token alır (Apple/Google/e-posta). Backend Spring Security Resource Server ile JWT'yi doğrular (issuer + audience + imza).
- İlk istekte `(provider='cognito', subject=sub)` → `identity.users` eşlemesi yapılır; yoksa kullanıcı oluşturulur (`UserRegistered`).
- Uygulama içinde **her yerde** iç `UserId` kullanılır; Cognito `sub` yalnızca `identity` modülünde görünür.
- Rol kontrolü: `@PreAuthorize` yalnızca web katmanında. Sahiplik kontrolü (ör. kendi sorusunu düzenleme) `application` katmanında domain kuralıdır.

## 15. API Sözleşmesi

- REST, JSON, `/v1` önekli. Kırıcı değişiklik → `/v2`; `/v1` en az 6 ay yaşar.
- OpenAPI belgesi springdoc ile koddan üretilir, CI'da `packages/api-client` yeniden üretilir; fark varsa build kırılır.
- Hatalar RFC 9457 `application/problem+json`, makine okunur `code` alanıyla (`QUESTION_NOT_FOUND`, `RATE_LIMITED`, `INVALID_TRANSITION` …).
- Sayfalama cursor tabanlıdır (`?cursor=&limit=`), offset yoktur.
- Yan etkili POST'lar idempotent: `Idempotency-Key` başlığı veya gövdede `clientAttemptId`.

| Modül | Uç nokta |
| --- | --- |
| identity | `GET /me` · `PATCH /me` · `DELETE /me` · `GET /me/export` · `PUT /me/devices` |
| taxonomy | `GET /exams` · `GET /exams/{id}/topics` |
| media | `POST /uploads` |
| content | `POST /questions` · `GET /questions/{id}` · `POST /questions/{id}/versions` · `POST /questions/{id}/withdraw` · `GET /me/questions` |
| practice | `POST /practice/sessions` · `GET /practice/sessions/{id}/next?count=` · `POST /attempts` · `POST /attempts:batch` · `POST /practice/sessions/{id}/end` · `GET /me/mistakes` · `PUT /me/favorites/{questionId}` · `DELETE /me/favorites/{questionId}` · `GET /me/favorites` · `GET /me/offline-pack?since=` |
| calibration | `POST /questions/{id}/difficulty-votes` (v2) |
| moderation | `POST /questions/{id}/reports` |
| stats | `GET /me/stats` |
| admin (`/v1/admin`, MODERATOR+) | `GET /review-items?kind=` · `POST /review-items/{id}/decision` · `POST /questions/{id}/transition` · `GET /key-suspects` · `POST /users/{id}/sanctions` · `PUT /taxonomy/...` |

`POST /attempts` yanıtı: `{ isCorrect, correct: [...], solution, thetaDelta }`.

## 16. Mobil Mimari

```text
mobile/
├── app/                          # expo-router rotaları (yalnızca ekran kompozisyonu)
│   ├── (auth)/ login.tsx onboarding.tsx
│   ├── (tabs)/ index.tsx upload.tsx review.tsx profile.tsx
│   └── practice/[sessionId].tsx
└── src/
    ├── features/                 # özellik başına dikey dilim
    │   ├── practice/  { api/ components/ hooks/ model/ }
    │   ├── upload/
    │   ├── review/              # yanlışlarım + favoriler
    │   ├── stats/
    │   ├── auth/
    │   └── profile/
    └── shared/
        ├── api/                 # @soruhavuzu/api-client sarmalayıcı, auth interceptor
        ├── ui/                  # tasarım sistemi bileşenleri
        ├── math/QuestionBody.tsx  # TEK render noktası (WebView + gömülü KaTeX)
        ├── db/                  # Drizzle şema + migration (offline)
        ├── i18n/                # tüm metinler (TR); kodda düz metin yok
        └── lib/
```

**Kurallar:**

- Bir feature başka feature'ın iç dosyasını import etmez; ortak ihtiyaç `shared`'e taşınır.
- Sunucu verisi yalnızca TanStack Query ile; global store'a kopyalanmaz.
- `QuestionBody` dışında hiçbir yer LaTeX render etmez. Liste önizlemelerinde `content-schema`'nın `toPlainText()` fonksiyonu kullanılır.
- Offline: yanlışlarım/favoriler versiyon JSON'u + görselleriyle SQLite'a yazılır. Offline cevaplar `clientAttemptId` ile kuyruğa alınır, bağlantı gelince `POST /attempts:batch` gönderilir.
- Token'lar `expo-secure-store`'da tutulur.

## 17. Altyapı

| Kaynak | v1 boyutu |
| --- | --- |
| RDS PostgreSQL | db.t4g.small, tek AZ, 7 gün otomatik yedek, şifreli |
| ECS Fargate | 0.5 vCPU / 1 GB × 1 görev (min 1, max 3) |
| Ağ | NAT Gateway yok; S3/SQS/Secrets Manager için VPC endpoint |
| S3 | `soruhavuzu-media-{env}`, versioning açık, public erişim kapalı |
| Ortamlar | `dev` (lokal docker-compose), `staging`, `prod` — hepsi CDK ile |
| Redis | v1'de yok (Bucket4j Postgres). İhtiyaçta ElastiCache t4g.micro |

Lokal geliştirme: `docker-compose` ile Postgres (+pgvector) + LocalStack (S3/SQS). Cognito yerine `local` profilinde sahte JWT üretici.

## 18. Gözlemlenebilirlik

- **Log:** JSON, alanlar `timestamp, level, traceId, spanId, module, event, userId(hash)`. Kişisel veri, token, içerik gövdesi loglanmaz.
- **Trace:** OpenTelemetry agent, HTTP + JDBC + olay dinleyicileri.
- **İş metrikleri:** `questions_submitted`, `review_queue_age_seconds`, `flagged_ratio`, `pool_bucket_size{exam,bucket}`, `attempts_total`, `calibration_logloss`, `event_publication_incomplete`.
- **Alarmlar:** 5xx oranı > %1 (5 dk), kuyruk yaşı > 24 saat, tamamlanmamış olay > 100, herhangi bir kovada aktif soru < 20.

## 19. Güvenlik

- Tüm trafik TLS. JWT doğrulaması her istekte.
- Gizli bilgiler yalnızca Secrets Manager'da; repoda `.env` örneği hariç gizli değer yok.
- Girdi doğrulama: DTO seviyesinde Bean Validation + içerik için JSON Schema + KaTeX allowlist.
- Admin panelinde LaTeX/HTML render'ı sandbox'lı iframe/WebView'de.
- S3 pre-signed URL'ler kısa ömürlü ve tür/boyut kısıtlı.
- Bağımlılık taraması: Dependabot + OWASP dependency-check / `pnpm audit` CI'da.
- Mobil: OWASP MASVS L1 hedefi; token secure-store'da, sertifika doğrulaması varsayılan.

## 20. Genişleme Rehberi

### 20.1 Yeni modül eklemek

1. `docs/adr/` altında ADR yaz (neden ayrı modül?).
2. `com.soruhavuzu.<modul>` paketi, `@ApplicationModule(allowedDependencies = …)`.
3. `<Modul>Api.kt`, `<Modul>Events.kt`, `internal/{domain,application,infrastructure,web}`.
4. Flyway: `V<n>__<modul>_init.sql` ile `CREATE SCHEMA <modul>`.
5. §5.1 ve §5.2 tablolarını, §5.4 olay kataloğunu güncelle.
6. `ModularityTests` geçmeli.

### 20.2 Yeni soru tipi eklemek

1. `content-schema`: `answer_spec` tipine ekle, `schema` sürümünü artır.
2. `content`: `AnswerGrader` arayüzüne yeni implementasyon (strateji deseni); `question_type` CHECK'ine ekle (migration).
3. `calibration`: k değeri belirle (tahmin düzeltmesi).
4. Mobil/admin: girdi ve sonuç bileşeni.

**Başka modül değişmez.**

### 20.3 Yeni çözme modu eklemek

1. `SessionType`'a değer ekle (migration CHECK).
2. `practice`: `QuestionSelector` arayüzüne yeni strateji.
3. Calibration etkisini (K çarpanı) konfigürasyonda tanımla.

### 20.4 Yeni sınav/konu eklemek

Kod değişmez: admin panelinden `taxonomy` kaydı.

### 20.5 Modülü servise ayırmak

1. Modülün olaylarını Modulith externalization ile SQS'e yönlendir.
2. `<Modul>Api` çağrılarını HTTP istemcisiyle değiştiren adaptör yaz (arayüz aynı kalır).
3. Şemayı ayrı veritabanına taşı (şemalar arası FK olmadığı için mümkün).

## 21. Başlangıç ADR Listesi

| ADR | Karar |
| --- | --- |
| 0001 | Modüler monolit (Spring Modulith), mikroservis değil |
| 0002 | Modül başına PostgreSQL şeması, şemalar arası FK yok |
| 0003 | Kotlin + Spring Boot + Spring Data JDBC |
| 0004 | React Native + Expo |
| 0005 | Blok-JSON içerik formatı, LaTeX kaynak olarak saklanır |
| 0006 | Değişmez soru versiyonları, append-only attempts |
| 0007 | Elo/Rasch zorluk modeli, yükleyen puanı prior |
| 0008 | Yüzdelik tabanlı 1–10 kova |
| 0009 | Cognito Lite + IdP'den bağımsız iç kullanıcı kimliği |
| 0010 | Medya işleme Lambda'da, S3 pre-signed yükleme |
| 0011 | UUIDv7 kimlikler |
| 0012 | ÖSYM/yayınevi içerik yasağı ve kaldırma süreci |
