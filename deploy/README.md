# Beta sunucusu kurulumu (Dokploy, ADR 0023)

Ön koşullar:
- Dokploy kurulu VPS; sunucu IP'si → `BASE_HOST=<ip-tireli>.sslip.io` (ör. `203-0-113-5.sslip.io`)
- GitHub reposu
- SMTP bilgisi (Logto e-posta doğrulaması için)

Sıra önemlidir. İmajlar ilk `main` push'unda üretilir; admin imajı Logto SPA uygulamasının ID'sini derlemede içine gömer. Bu yüzden adımları sırayla izle.

1. **GitHub ayarları (önce):**
   - Repository variables (Settings → Secrets and variables → Actions → Variables → **Repository variables**): `BETA_BASE_HOST` = `BASE_HOST` değeri.
   - `BETA_BASE_HOST` ve `BETA_OIDC_ADMIN_CLIENT_ID` ortam (environment) değişkeni değil, **Repository variable** olmalı. `publish` işinde `environment:` yok; ortam değişkeni orada boş okunur.
   - Environment: `beta` adlı ortamı oluştur (`deploy-beta` işi bunu kullanır).
   - Branch protection (`main`): PR zorunlu, doğrudan push kapalı (kurallar.md §8 madde 3). Zorunlu status check'ler: `backend`, `js`, `gitleaks`, `actionlint`, `beta-smoke`. `publish` ve `deploy-beta` zorunlu yapılmaz; PR'larda atlanırlar. Bir check en az bir kez çalışmadan GitHub listesinde görünmez; check'leri 2. adımdan sonra ekle.
2. **İlk `main` push'u:** PR'ı merge et. CI imajları GHCR'a yayınlar (`soruhavuzu-api:beta`, `soruhavuzu-admin:beta`).
   - `deploy-beta` bu ilk çalışmada **başarısız olur**: webhook henüz yok. Beklenen durumdur.
   - GHCR imajları private ise (6. adımdan önce): Dokploy → Registry → ghcr.io (GitHub PAT, `read:packages`).
3. **Postgres:** Dokploy → Create Database → PostgreSQL.
   - Image: `pgvector/pgvector:0.8.7-pg17`, bellek sınırı 768 MB.
   - İç adı `DB_HOST` olarak not et.
   - Backups: günlük. Gerçek kullanıcıdan önce sunucu dışı bir S3 uyumlu hedef ekle (ör. Cloudflare R2).
4. **Parolaları üret** (her biri ayrı, örnek: `openssl rand -hex 32`): `DB_PASSWORD`, `LOGTO_DB_PASSWORD`, `LOG_USER_ID_HASH_KEY`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`. Hex kullanılır; çünkü `LOGTO_DB_PASSWORD` bağlantı adresine (`postgres://logto:<parola>@...`) olduğu gibi girer ve base64'ün `/`, `+`, `=` karakterleri adresi bozar.
5. **Veritabanları:** Dokploy → Postgres → Terminal:
   `psql -U <admin> -d postgres -v ON_ERROR_STOP=1 -v app_password='<DB_PASSWORD>' -v logto_password='<LOGTO_DB_PASSWORD>' -f -`
   - `<admin>`, Dokploy'un Postgres sayfasında gösterdiği kullanıcıdır.
   - `deploy/postgres-init.sql` içeriğini yapıştır, Ctrl-D ile bitir.
6. **Compose:** Dokploy → Create Service → Compose → GitHub reposu.
   - Compose path: `deploy/docker-compose.beta.yml`.
   - Environment: `deploy/.env.example`'daki anahtarları doldur.
     - `BASE_HOST` = `<ip-tireli>.sslip.io` değeri.
     - `GHCR_OWNER` = GitHub kullanıcı adı, **küçük harfle** (CI imaj yolunu küçük harfle yayınlar).
     - `DB_HOST` = 3. adımdaki iç ad.
     - `DB_PORT=5432` ve `IMAGE_TAG=beta` varsayılan kalır.
     - Parolalar 4. adımdan.
     - `OIDC_CLIENT_IDS` şimdilik boş kalabilir; 8. adımda doldurulur.
   - Bucket `soruhavuzu-media-beta`, `s3-init` servisi tarafından otomatik oluşturulur.
7. **Domains** (Dokploy → Compose → Domains, HTTPS, Let's Encrypt):

   | Servis | Port | Host |
   |---|---|---|
   | api | 8080 | `api.$BASE_HOST` |
   | admin | 8080 | `admin.$BASE_HOST` |
   | logto | 3001 | `auth.$BASE_HOST` |
   | logto | 3002 | `auth-admin.$BASE_HOST` |
   | s3 | 8333 | `media.$BASE_HOST` |

   Domain'leri ekledikten sonra **Deploy**'a bas; `api` ve `logto` sağlıklı (healthy) olana kadar bekle.

8. **Logto** (`https://auth-admin.$BASE_HOST`):
   - Yönetici hesabını oluştur.
   - API resource: indicator `https://api.$BASE_HOST`.
   - SPA uygulaması oluştur: redirect `https://admin.$BASE_HOST/auth/callback`, CORS `https://admin.$BASE_HOST`.
   - Native uygulama oluştur: redirect `soruhavuzu://auth/callback`.
   - İki uygulamanın ID'sini virgülle Dokploy'da `OIDC_CLIENT_IDS`'e yaz ve compose'u redeploy et.
   - Connectors → Email: SMTP bilgilerini gir.
9. **CI'ı tamamlayan ayarlar:**
   - Repository variable `BETA_OIDC_ADMIN_CLIENT_ID` = **SPA uygulamasının ID'si** (Native uygulamanınki değil).
   - Secret `DOKPLOY_DEPLOY_WEBHOOK` = Dokploy → Compose → Deployments → Webhook URL (repository veya `beta` environment secret'ı olabilir).
10. **CI'ı yeniden çalıştır:** GitHub → Actions → `main` üzerindeki son `ci` çalışması → "Re-run all jobs". Değişkenler çalışma anında okunur, bu yüzden yeni değerler geçerli olur. (`workflow_dispatch` yoktur, Ruling 12.) Yeniden çalışma admin imajını istemci ID'siyle yeniden derler ve Dokploy'u redeploy eder.
11. **Mobil** (EAS):
    - `eas env:create --environment preview` ile şunları gir:
      - `EXPO_PUBLIC_AUTH_MODE=oidc`
      - `EXPO_PUBLIC_API_URL=https://api.$BASE_HOST`
      - `EXPO_PUBLIC_API_RESOURCE=https://api.$BASE_HOST`
      - `EXPO_PUBLIC_OIDC_ISSUER=https://auth.$BASE_HOST/oidc`
      - `EXPO_PUBLIC_OIDC_CLIENT_ID=<native app id>`
      - `EXPO_PUBLIC_ASSET_BASE_URL=https://media.$BASE_HOST/soruhavuzu-media-beta`
    - `eas build -p android --profile preview` ile build al.
12. **Doğrulama (Faz 0 kabul kriterleri):**
    - CI'daki `deploy-beta` duman testi yeşil. Bu iş `BETA_BASE_HOST` değişkenine ve `DOKPLOY_DEPLOY_WEBHOOK` secret'ına bağlıdır; ikisi de yoksa başarısız olur.
    - Admin'de Logto ile kayıt ve giriş → "… olarak giriş yapıldı (USER)".
    - Mobil preview build'de giriş → ana sayfada kullanıcı.
    - `docker stats` ile bellek kullanımını kontrol et.

Alan adı alınınca: DNS'te `api|admin|auth|auth-admin|media.<alanadı>` kayıtlarını aç, Dokploy domain'lerini, `BASE_HOST`'u ve Logto ENDPOINT'lerini güncelle. Ardından Logto'da Google ve Apple bağlayıcılarını ekle.
