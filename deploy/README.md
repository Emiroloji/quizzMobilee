# Beta sunucusu kurulumu (Dokploy, ADR 0023)

Ön koşullar:
- Dokploy kurulu VPS; sunucu IP'si → `BASE_HOST=<ip-tireli>.sslip.io` (ör. `203-0-113-5.sslip.io`)
- GitHub reposu
- SMTP bilgisi (Logto e-posta doğrulaması için)

1. **Postgres:** Dokploy → Create Database → PostgreSQL.
   - Image: `pgvector/pgvector:0.8.7-pg17`, bellek sınırı 768 MB.
   - İç adı `DB_HOST` olarak not et.
   - Backups: günlük. Gerçek kullanıcıdan önce sunucu dışı bir S3 uyumlu hedef ekle (ör. Cloudflare R2).
2. **Parolaları üret** (her biri ayrı, örnek: `openssl rand -base64 32`): `DB_PASSWORD`, `LOGTO_DB_PASSWORD`, `LOG_USER_ID_HASH_KEY`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`.
3. **Veritabanları:** Dokploy → Postgres → Terminal:
   `psql -U <admin> -v app_password='<DB_PASSWORD>' -v logto_password='<LOGTO_DB_PASSWORD>' -f -`
   ve `deploy/postgres-init.sql` içeriğini yapıştır.
4. **Compose:** Dokploy → Create Service → Compose → GitHub reposu.
   - Compose path: `deploy/docker-compose.beta.yml`.
   - Environment: `deploy/.env.example`'daki anahtarları doldur. `GHCR_OWNER` = GitHub kullanıcı adı; `IMAGE_TAG=beta`; `OIDC_CLIENT_IDS` 6. adımdan sonra.
   - GHCR imajları private ise: Dokploy → Registry → ghcr.io (GitHub PAT, `read:packages`).
5. **Domains** (Dokploy → Compose → Domains, HTTPS, Let's Encrypt):

   | Servis | Port | Host |
   |---|---|---|
   | api | 8080 | `api.$BASE_HOST` |
   | admin | 8080 | `admin.$BASE_HOST` |
   | logto | 3001 | `auth.$BASE_HOST` |
   | logto | 3002 | `auth-admin.$BASE_HOST` |
   | s3 | 8333 | `media.$BASE_HOST` |

6. **Logto** (`https://auth-admin.$BASE_HOST`):
   - Yönetici hesabını oluştur.
   - API resource: indicator `https://api.$BASE_HOST`.
   - Native uygulama (redirect `soruhavuzu://auth/callback`) ve SPA (redirect `https://admin.$BASE_HOST/auth/callback`, CORS `https://admin.$BASE_HOST`) oluştur.
   - Bu iki uygulamanın ID'lerini virgülle `OIDC_CLIENT_IDS`'e yaz ve compose'u redeploy et.
   - Connectors → Email: SMTP bilgilerini gir.
7. **GitHub ayarları:**
   - Variables: `BETA_BASE_HOST`, `BETA_OIDC_ADMIN_CLIENT_ID`.
   - Environment: `beta`.
   - Secret: `DOKPLOY_DEPLOY_WEBHOOK` (Dokploy → Compose → Deployments → Webhook URL).
   - Branch protection: `main`'e doğrudan push kapalı, `ci` iş akışının işleri zorunlu (kurallar.md §8).
8. **Mobil** (EAS):
   - `eas env:create --environment preview` ile şunları gir:
     - `EXPO_PUBLIC_AUTH_MODE=oidc`
     - `EXPO_PUBLIC_API_URL=https://api.$BASE_HOST`
     - `EXPO_PUBLIC_API_RESOURCE=https://api.$BASE_HOST`
     - `EXPO_PUBLIC_OIDC_ISSUER=https://auth.$BASE_HOST/oidc`
     - `EXPO_PUBLIC_OIDC_CLIENT_ID=<native app id>`
     - `EXPO_PUBLIC_ASSET_BASE_URL=https://media.$BASE_HOST/soruhavuzu-media-beta`
   - `eas build -p android --profile preview` ile build al.
9. **Doğrulama (Faz 0 kabul kriterleri):**
   - CI'daki `deploy-beta` duman testi yeşil.
   - Admin'de Logto ile kayıt ve giriş → "… olarak giriş yapıldı (USER)".
   - Mobil preview build'de giriş → ana sayfada kullanıcı.
   - `docker stats` ile bellek kullanımını kontrol et.

Alan adı alınınca: DNS'te `api|admin|auth|auth-admin|media.<alanadı>` kayıtlarını aç, Dokploy domain'lerini, `BASE_HOST`'u ve Logto ENDPOINT'lerini güncelle. Ardından Logto'da Google ve Apple bağlayıcılarını ekle.
