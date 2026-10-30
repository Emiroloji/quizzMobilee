# Tek Sunucu (Dokploy) Dağıtım Tasarımı

- **Tarih:** 2026-10-29
- **Durum:** Onaylandı (proje sahibi: "AWS ile uğraşmayalım, her şeyi tekte yapalım"; kimlik için Logto seçildi)
- **Kapsam:** Faz 0 / Adım 9'un yerine geçer. AWS (CDK, Cognito, S3/CloudFront, SQS, Lambda, Rekognition, Secrets Manager, CloudWatch/X-Ray) kullanılmaz.
- **Kararlar:** ADR 0023 (tek sunucu topolojisi), ADR 0024 (Logto)

## 1. Girdiler

| Konu | Değer |
|---|---|
| Sunucu | VPS, 5 GB RAM, Dokploy kurulu, yalnızca bu projeye ait |
| Konum | Türkiye → KVKK yurt dışı aktarım yükümlülüğü yok |
| CPU mimarisi | Bilinmiyor → imajlar `linux/amd64` + `linux/arm64` |
| Alan adı | Yok → geçici olarak `*.<ip-tireli>.sslip.io` + Let's Encrypt |
| Ortam | Tek ortam `beta`. Uygulama profili `staging`; `prod` profili kodda kalır, sunucusu sonra |

## 2. Topoloji ve bellek

İnternete yalnızca Traefik (80/443) açıktır. Diğer servisler Dokploy'un iç ağındadır.

| Servis | İmaj | Dış adres | Bellek sınırı |
|---|---|---|---|
| Postgres 17 + pgvector + ltree | `pgvector/pgvector:0.8.7-pg17` (Dokploy Database) | — | 768 MB |
| API | `ghcr.io/<owner>/soruhavuzu-api:<sha>` | `api.<host>` | 1 GB |
| Logto | `svhd/logto:<sabit sürüm>` | `auth.<host>`, `auth-admin.<host>` | 512 MB |
| S3: SeaweedFS | `chrislusf/seaweedfs:4.48` | `media.<host>` | 256 MB |
| SQS: ElasticMQ (kalıcı) | `softwaremill/elasticmq-native:1.7.1` | — | 128 MB |
| Admin (statik nginx) | `ghcr.io/<owner>/soruhavuzu-admin:<sha>` | `admin.<host>` | 32 MB |
| Medya worker'ı (Faz 1) | — | — | 256 MB |

Toplam yaklaşık 3 GB. Dokploy, Traefik ve işletim sistemi için yaklaşık 1 GB pay kalır.

Postgres'te iki veritabanı vardır: `soruhavuzu` (uygulama; şema başına modül kuralı aynen geçerli) ve `logto`. Her birinin kendi kullanıcısı vardır.

## 3. Kimlik (Logto)

- Logto bir OIDC sağlayıcısıdır. Uygulama içinde iç `UserId` kullanılır ve `sub` yalnızca `identity` modülünde görünür (ADR 0009'daki ilke korunur; sağlayıcı değişir).
- Logto'da bir **API resource** tanımlanır (indicator = `https://api.<host>`). Access token JWT'dir ve `aud` = bu indicator'dır.
- **Backend doğrulaması** (ADR 0018'in yerine geçen kurallar): imza (JWKS: `<issuer>/jwks`), `iss` = `https://auth.<host>/oidc`, `exp`/`nbf`, ve `aud` yapılandırılan resource'u içermeli. Cognito'ya özel `token_use` kontrolü kaldırılır. `client_id` izin listesi korunur: Logto access token'ında `client_id` claim'i vardır; ilk deneme (spike) bunu doğrular, yoksa bu kontrol kaldırılır.
- **Rol:** Rol yine veritabanından okunur; token'daki rol kullanılmaz.
- **İstemciler:**
  - Mobil: "Native" uygulama, PKCE, yönlendirme `soruhavuzu://auth/callback`.
  - Admin: "SPA" uygulaması, PKCE, yönlendirme `https://admin.<host>/auth/callback`.
  - Her ikisi de token isteğinde `resource=https://api.<host>` gönderir.
- **Giriş yöntemleri:**
  - Şimdi: e-posta + doğrulama kodu. Logto'nun SMTP ayarı gerekir; SMTP yokken yalnızca konsoldan oluşturulan test kullanıcıları kullanılır.
  - Alan adı alınınca: Google ve Apple.
- **local/test profili:** Mevcut sahte JWT üretici aynen kalır, ama Logto biçiminde token üretir (`aud` dahil). `LocalProfileGuard` korunur.

## 4. Medya, kuyruk ve moderasyon

- **Yükleme:** Presigned PUT URL'leri `media.<host>` (SeaweedFS S3) üzerinden verilir. Okuma `media.<host>/soruhavuzu-media-beta/public/…` adresinden yapılır.
  - `uploads/` öneki için 24 saatlik temizlik: SeaweedFS'te lifecycle desteği sınırlı olduğu için Faz 1'de ShedLock'lu zamanlanmış bir işle yapılır.
- **Kuyruk:** ElasticMQ, kalıcı mesaj deposuyla çalışır (`messages-storage`, volume).
- **S3 olayı → SQS köprüsü ve medya işleme:**
  - Lambda yerine medya worker konteyneri kullanılır (Node/TS).
  - Medya işleme Faz 1'de kurulacağı için olay köprüsünün tasarımı Faz 1'e bırakılır. Seçenekler: SeaweedFS filer bildirimi ya da API'nin "yükleme tamamlandı" çağrısı.
- **Görsel moderasyonu:** Rekognition yok. Faz 1 kararıdır. Geçici önlem: itibarı 50'nin altındaki kullanıcıların her sorusu zaten `IN_REVIEW` kuyruğuna düşüyor (mimari §10), yani görseller insan onayından geçiyor.

## 5. Sırlar, TLS, izleme, yedek

- **Sırlar:** Dokploy ortam değişkenleri. Repoda değer yoktur (`.env.example` hariç). kurallar.md §9 buna göre güncellenir.
- **TLS:** Traefik + Let's Encrypt, sslip.io alan adlarıyla. Alan adı gelince adres değişir, mimari değişmez.
- **İzleme:**
  - JSON loglar (mimari §18 alanları) Dokploy'un log görüntüleyicisinde.
  - Trace dışa aktarımı kapalı (`OTEL_TRACES_EXPORTER=none`), ama agent kalır: traceId/spanId loglarda görünür.
  - Sağlık: `/actuator/health`.
  - Alarmlar (mimari §18) Faz 5'te ele alınır.
- **Yedek:**
  - Dokploy'un Postgres yedeklemesi günlük çalışır (`soruhavuzu` ve `logto` veritabanları); hedef S3 uyumlu bir depodur.
  - Gerçek kullanıcı gelmeden (Faz 4) önce **sunucu dışı** bir hedef zorunludur (ör. Cloudflare R2 veya Backblaze B2; AWS değil).
  - Geri dönme tatbikatı Faz 5 kabul kriterinde zaten var.

## 6. CI/CD

GitHub Actions, sırayla:
1. lint → test → mimari testler → OpenAPI/schema farkı → gitleaks → actionlint
2. Çok mimarili imajlar (API, admin) → GHCR
3. `main`'e merge → Dokploy API'si `compose.deploy` (beta); CI yayındaki SHA'yı `/actuator/info` ile doğrular
4. Duman testi: `GET https://api.<host>/actuator/health` = UP ve `GET /v1/me` = 401

Prod iş akışı (tag + elle onay) prod sunucusu belirlenince eklenir. İmajlar CI'da derlenir, sunucuda derleme yapılmaz (5 GB RAM, Gradle).

## 7. Değişmeyenler

Modüler monolit, şema başına modül, olaylar/outbox, blok-JSON ve KaTeX, mobil ve admin mimarisi, kurallar. Kod tarafında yalnızca kimlik doğrulama yapılandırması (bölüm 3) ve istemcilerin OIDC ayarları değişir.

## 8. Doküman ve rapor güncellemeleri

- **ADR'ler:**
  - ADR 0023: tek sunucu topolojisi.
  - ADR 0024: Logto. ADR 0009'un yerini alır; ADR 0018'in Cognito'ya özel kurallarını değiştirir.
  - ADR 0010: kısmen yerini aldı (Lambda ve Rekognition).
  - ADR 0022: kapsamı sunucuya genişler.
- **Dokümanlar** (v1.1 ve değişiklik kaydı):
  - mimari.md: §2, §3, §13, §14, §17, §18, §19, §21
  - proje.md: §10 (KVKK: veri Türkiye'de)
  - kurallar.md: §8 (main → beta), §9 (sırlar)
  - fazlar.md: Faz 0 Altyapı maddeleri ve kabul kriterleri
- **Plan:** Önceki Adım 9 planı (`2026-10-29-faz0-adim9-altyapi-cicd.md`, AWS/CDK) geçersizdir ve yerini yeni plan alır.
- **NotebookLM:**
  - AWS'den söz eden raporlara (#1, #9, #10) "güncel değil, bkz. #11" notu eklenir.
  - Rapor #11 yeni mimariyi anlatır.

## 9. Riskler

| Risk | Önlem |
|---|---|
| Tek sunucu = tek hata noktası | Beta için kabul. Sunucu dışı yedek ve geri dönme tatbikatı (Faz 4/5) |
| 5 GB RAM | Servis başına bellek sınırı; JVM `MaxRAMPercentage`; Faz 4 yük testinde ölçülür |
| Logto access token biçimi beklenenden farklı | Uygulama planının ilk görevi kısa bir spike: gerçek token'ı lokalde üretip claim'leri doğrulamak |
| sslip.io ile Apple/Google girişi yok | E-posta ile beta; alan adı alınınca açılır |
