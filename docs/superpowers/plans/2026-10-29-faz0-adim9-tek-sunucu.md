# Faz 0 / Adım 9 — Tek Sunucu (Dokploy) + Logto Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faz 0'ın kalan kabul kriterlerini kapatmak: `beta` sunucusunda Logto ile giriş + `GET /me`, `main` → `beta` otomatik deploy ve CI'da mimari testler. Bunun için kimlik doğrulamayı sağlayıcıdan bağımsız yapmak, Logto'yu lokal ve sunucu ortamına eklemek, imajları, compose tanımlarını, CI/CD'yi ve EAS profillerini kurmak gerekiyor.

**Architecture:** Tek VPS'te Dokploy:
- Bizim compose dosyamız: API, Logto, SeaweedFS, ElasticMQ, admin.
- Dokploy'un yönetilen Postgres'i.
- Traefik ile TLS.

İmajlar GitHub Actions'ta çok mimarili derlenip GHCR'a gönderilir. `main` merge'ü Dokploy webhook'uyla redeploy tetikler. Backend JWT doğrulaması yapılandırmayla belirlenir: issuer, JWKS, audience, izinli `client_id`'ler.

**Tech Stack:**
- Uygulama: Spring Boot 4.1.2 / Kotlin 2.3, Logto 1.45.0 (`svhd/logto:1.45.0`)
- Depolama ve kuyruk: SeaweedFS 4.48, ElasticMQ 1.7.1, Postgres 17 (`pgvector/pgvector:0.8.7-pg17`)
- İmajlar: eclipse-temurin `25.0.4.1_1-jre-noble`, nginx `1.30.5-alpine`
- İstemciler: expo-auth-session (SDK 57), oidc-client-ts 3.5.0, EAS CLI ≥ 25.0.0, expo-dev-client 57.0.20
- CI: GitHub Actions (SHA'ya sabitli)

**Spec:** `docs/superpowers/specs/2026-10-29-tek-sunucu-dokploy-design.md` · ADR 0023, ADR 0024

## Global Constraints

- **Altyapı:** AWS kullanılmaz. Tek VPS (Türkiye, 5 GB RAM), Dokploy. İnternete yalnızca Traefik 80/443 açık.
- **Bellek sınırları:**

  | Servis | Sınır |
  |---|---|
  | Postgres | 768 MB |
  | API | 1 GB |
  | Logto | 512 MB |
  | SeaweedFS | 256 MB |
  | ElasticMQ | 128 MB |
  | Admin | 32 MB |

- **Adresler:** `api.<BASE_HOST>`, `admin.<BASE_HOST>`, `auth.<BASE_HOST>`, `auth-admin.<BASE_HOST>`, `media.<BASE_HOST>`. Alan adı gelene kadar `BASE_HOST = <ip-tireli>.sslip.io`.
- **Ortam:** tek `beta` ortamı; uygulama profili `staging`.
- **Kimlik:** Logto issuer `https://auth.<BASE_HOST>/oidc`, API resource indicator `https://api.<BASE_HOST>`. Backend doğrulaması: imza (JWKS) + iss + exp/nbf + aud ∈ audiences + (yapılandırılmışsa) client_id ∈ izin listesi. `token_use` yok.
- **Sırlar:** Dokploy ortam değişkenlerinde. Repoda değer yok, yalnızca `.env.example`.
- **İmajlar:** `linux/amd64` + `linux/arm64`; GHCR etiketleri `:<sha>` ve `:beta`. Sunucuda derleme yapılmaz.
- **Sürümler:** ≥ 14 günlük ve kesin (ADR 0020, `minimumReleaseAge`); Actions commit SHA'sına sabitli.
- **Dil:** Kod İngilizce; kullanıcı metinleri Türkçe ve i18n anahtarıyla.
- **Kurallar:** kurallar.md tamamen geçerli; TDD (önce kırmızı test); Conventional Commits.
- **Onay:** Dışa dönük adımlar (sunucuya deploy, GitHub'a push, repo oluşturma) yalnızca kullanıcının açık onayıyla yapılır. Görev 1–9 yerelde doğrulanır; Görev 10 onaya bağlıdır.

---

## Dosya Yapısı

```
infra/local/postgres-init.sql            # lokal: logto veritabanı ve kullanıcısı
docker-compose.yml                       # + logto servisi (lokal)
deploy/
├── docker-compose.beta.yml              # Dokploy'a verilen compose
├── docker-compose.smoke.yml             # CI/lokal duman testi için ek (postgres, port, pull_policy)
├── elasticmq.conf                       # kalıcı kuyruk yapılandırması
├── postgres-init.sql                    # beta: logto veritabanı ve kullanıcısı (runbook'ta elle)
├── .env.example                         # Dokploy ortam değişkenleri (değersiz)
├── scripts/logto-token-claims.sh        # spike: client_credentials token'ının claim'leri
└── README.md                            # runbook
backend/Dockerfile  backend/.dockerignore
admin/Dockerfile  admin/nginx.conf  admin/.dockerignore
backend/src/main/kotlin/com/soruhavuzu/identity/internal/web/
├── IdentityProperties.kt                # audiences, jwkSetUri, jwsAlgorithms; clientIds opsiyonel
├── AccessTokenValidators.kt             # sağlayıcıdan bağımsız doğrulama
├── OidcJwtConfiguration.kt              # (CognitoJwtConfiguration yerine)
└── DevTokenController.kt                # aud ekler, token_use kaldırılır
mobile/src/features/auth/api/oidc.ts     # (cognito.ts yerine)
mobile/src/features/auth/components/OidcSignInButton.tsx   # (CognitoButton yerine)
admin/src/features/auth/api/oidc.ts      # Logto ayarları
.github/workflows/ci.yml  .github/actionlint.yaml
mobile/eas.json
```

---

### Task 1: Logto'yu lokal ortama ekle ve access token biçimini doğrula (spike)

**Files:**
- Create: `infra/local/postgres-init.sql`, `deploy/scripts/logto-token-claims.sh`
- Modify: `docker-compose.yml` (logto servisi, postgres init betiği), `Makefile` (`logto-claims` hedefi), `docs/adr/0024-logto-identity.md` (sonuç eki)

**Interfaces:**
- Produces:
  - Lokal Logto: `http://localhost:3001` (OIDC, issuer `http://localhost:3001/oidc`) ve `http://localhost:3002` (konsol).
  - ADR 0024'e "Spike sonucu" eki: gerçek `alg`, `iss`, `aud`, `client_id` claim'leri. Task 2'nin `jwsAlgorithms` varsayılanı buna göre kesinleşir.

- [ ] **Step 1: Postgres init betiğini yaz — `infra/local/postgres-init.sql`**

```sql
-- Yalnızca yeni (boş) volume'da çalışır. Logto kendi veritabanını ve kullanıcısını kullanır (ADR 0024).
CREATE USER logto WITH PASSWORD 'logto';
CREATE DATABASE logto OWNER logto;
```

- [ ] **Step 2: `docker-compose.yml`'a ekle**

`postgres` servisinin `volumes` listesine:
```yaml
      - ./infra/local/postgres-init.sql:/docker-entrypoint-initdb.d/10-logto.sql:ro
```
Yeni servis (`sqs` servisinden sonra):
```yaml
  logto:
    image: svhd/logto:1.45.0
    depends_on:
      postgres: { condition: service_healthy }
    entrypoint: ["sh", "-c", "npm run cli db seed -- --swe && npm start"]
    environment:
      TRUST_PROXY_HEADER: "1"
      DB_URL: postgres://logto:logto@postgres:5432/logto
      ENDPOINT: http://localhost:3001
      ADMIN_ENDPOINT: http://localhost:3002
    ports: ["127.0.0.1:3001:3001", "127.0.0.1:3002:3002"]
```

- [ ] **Step 3: Volume'u sıfırla ve Logto'nun açıldığını doğrula**

Run: `make reset && make infra && curl -s --retry 40 --retry-delay 3 --retry-all-errors --retry-connrefused http://localhost:3001/oidc/.well-known/openid-configuration | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['issuer'], d['jwks_uri'], d.get('id_token_signing_alg_values_supported'))"`
Expected: `http://localhost:3001/oidc http://localhost:3001/oidc/jwks [...]`

- [ ] **Step 4: Konsolda spike kaynaklarını oluştur (elle, yalnızca lokal)**

`http://localhost:3002` → yönetici hesabı oluştur ve şu adımları izle:
1. **API resources → Create:** ad `soruhavuzu-api`, indicator `http://localhost:8080`.
2. **Applications → Machine-to-machine → Create:** ad `spike`. Rolüne `soruhavuzu-api`'den en az bir izin (ör. `read:me`) ver. App ID ve App secret'ı not et.
3. **Applications → Native → Create:** ad `soruhavuzu-mobile-local`, redirect URI `soruhavuzu://auth/callback`.
4. **Applications → SPA → Create:** ad `soruhavuzu-admin-local`, redirect URI `http://localhost:5173/auth/callback`, CORS origin `http://localhost:5173`.

Mobil ve admin app ID'lerini `mobile/.env.local` ve `admin/.env.local` dosyalarında kullanacağız (Task 3–4); bunlar commit'lenmez.

- [ ] **Step 5: Claim betiğini yaz — `deploy/scripts/logto-token-claims.sh`**

```bash
#!/usr/bin/env bash
# Spike: Logto'dan client_credentials token'ı alıp başlığını ve claim'lerini yazdırır (ADR 0024).
# Kullanım: deploy/scripts/logto-token-claims.sh <issuer> <client_id> <client_secret> <resource>
set -euo pipefail
issuer="$1"; client_id="$2"; client_secret="$3"; resource="$4"
token=$(curl -sf -u "$client_id:$client_secret" \
  -d grant_type=client_credentials -d "resource=$resource" -d scope=read:me \
  "$issuer/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
python3 - "$token" <<'PY'
import base64, json, sys
def part(i):
    seg = sys.argv[1].split(".")[i]
    return json.loads(base64.urlsafe_b64decode(seg + "=" * (-len(seg) % 4)))
print(json.dumps({"header": part(0), "claims": part(1)}, indent=2))
PY
```
Ardından `chmod +x deploy/scripts/logto-token-claims.sh`.

`Makefile` (`.PHONY`'ye `logto-claims` ekle):
```make
logto-claims: ## Logto access token biçimini gösterir: make logto-claims ID=... SECRET=...
	deploy/scripts/logto-token-claims.sh http://localhost:3001/oidc "$(ID)" "$(SECRET)" http://localhost:8080
```

- [ ] **Step 6: Çalıştır ve sonucu kaydet**

Run: `make logto-claims ID=<spike app id> SECRET=<spike secret>`
Expected:
- `header.alg` (ör. `ES384` veya `RS256`)
- `claims.iss` = `http://localhost:3001/oidc`
- `claims.aud` = `http://localhost:8080`
- `claims.client_id` mevcut

`docs/adr/0024-logto-identity.md` sonuna ekle (gözlenen değerleri birebir yaz):
```markdown
## Ek: Spike sonucu (2026-10-29, Logto 1.45.0)
client_credentials ile alınan access token: alg=<gözlenen>, iss=http://localhost:3001/oidc, aud=http://localhost:8080, client_id=<mevcut/yok>, token_use yok. Backend `jws-algorithms` varsayılanı buna göre ayarlandı.
```

- [ ] **Step 7: Commit**

```bash
git add infra/local/postgres-init.sql docker-compose.yml deploy/scripts/logto-token-claims.sh Makefile docs/adr/0024-logto-identity.md
git commit -m "feat(identity): lokal Logto ve access token biçimi spike'ı"
```

---

### Task 2: Backend — sağlayıcıdan bağımsız token doğrulaması

**Files:**
- Modify: `backend/src/main/kotlin/com/soruhavuzu/identity/internal/web/IdentityProperties.kt`, `AccessTokenValidators.kt`, `DevTokenController.kt`
- Delete: `backend/src/main/kotlin/com/soruhavuzu/identity/internal/web/CognitoJwtConfiguration.kt`
- Create: `backend/src/main/kotlin/com/soruhavuzu/identity/internal/web/OidcJwtConfiguration.kt`
- Modify: `backend/src/main/resources/application.yml`, `application-local.yml`, `backend/src/test/resources/application-test.yml`
- Test: `backend/src/test/kotlin/com/soruhavuzu/identity/TokenValidationTests.kt`

**Interfaces:**
- Consumes: Task 1 spike sonucu (`alg`).
- Produces: Ortam değişkenleri:
  - `OIDC_ISSUER_URI`
  - `OIDC_JWK_SET_URI`
  - `OIDC_AUDIENCES` (virgülle ayrılmış)
  - `OIDC_CLIENT_IDS` (virgülle ayrılmış, boş olabilir)
  - `OIDC_JWS_ALGORITHMS` (varsayılan `ES384,RS256`)

  `auth_identities.provider` = `logto` (staging/prod), `local` (local/test).

- [ ] **Step 1: Başarısız testleri yaz — `TokenValidationTests.kt`'yi güncelle**

`token(...)` yardımcısını değiştir (`tokenUse` parametresi kalkar, `audience` eklenir):
```kotlin
    private fun token(
        encoder: JwtEncoder = this.encoder,
        issuer: String = ISSUER,
        audience: String? = AUDIENCE,
        clientId: String = "test-mobile",
        expiresAt: Instant = Instant.now().plus(Duration.ofMinutes(10)),
    ): String {
        val builder =
            JwtClaimsSet
                .builder()
                .issuer(issuer)
                .subject("dogrulama-${System.nanoTime()}")
                .issuedAt(expiresAt.minus(Duration.ofMinutes(20)))
                .expiresAt(expiresAt)
                .claim("client_id", clientId)
        audience?.let { builder.audience(listOf(it)) }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), builder.build())).tokenValue
    }
```
`id token reddedilir…` testini sil. Yerine ekle:
```kotlin
    @Test
    fun `token_use claim'i olmayan Logto biçimli access token kabul edilir`() {
        meStatus(token()) shouldBe OK
    }

    @Test
    fun `başka API için verilmiş (yanlış audience) token reddedilir`() {
        meStatus(token(audience = "https://baska-api.example")) shouldBe UNAUTHORIZED
    }

    @Test
    fun `audience'sız token reddedilir`() {
        meStatus(token(audience = null)) shouldBe UNAUTHORIZED
    }

    @Test
    fun `izin listesinde olmayan istemcinin token'ı reddedilir`() {
        meStatus(token(clientId = "yabanci-istemci")) shouldBe UNAUTHORIZED
    }
```
`companion object`'e ekle: `const val AUDIENCE = "https://api.test.soruhavuzu.invalid"`

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd backend && ./gradlew test --tests 'com.soruhavuzu.identity.TokenValidationTests' --console=plain`
Expected: FAIL — `token_use…kabul edilir` (401 döner, çünkü `token_use` zorunlu) ve `yanlış audience…` (200 döner, çünkü `aud` kontrol edilmiyor)

- [ ] **Step 3: `IdentityProperties.kt`'yi yaz**

```kotlin
package com.soruhavuzu.identity.internal.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("soruhavuzu.identity")
data class IdentityProperties(
    @field:Valid val token: Token,
) {
    data class Token(
        /** auth_identities.provider değeri: staging/prod'da `logto`, local/test'te `local`. */
        @field:NotBlank val provider: String,
        @field:NotBlank val issuerUri: String,
        /** JWKS adresi (Logto: <issuer>/jwks). local/test'te kullanılmaz. */
        val jwkSetUri: String? = null,
        /** Kabul edilen API resource indicator'ları; access token'daki `aud` bunlardan birini içermeli (ADR 0024). */
        @field:NotEmpty val audiences: Set<String>,
        /** Boş değilse yalnızca bu istemcilerin token'ı kabul edilir (`client_id`). */
        val clientIds: Set<String> = emptySet(),
        @field:NotEmpty val jwsAlgorithms: Set<String> = setOf("ES384", "RS256"),
    )
}
```

- [ ] **Step 4: `AccessTokenValidators.kt`'yi yaz**

```kotlin
package com.soruhavuzu.identity.internal.web

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import java.time.Clock

/** Sağlayıcıdan bağımsız access token doğrulaması: süre + issuer + audience + (varsa) istemci izin listesi (ADR 0024). */
object AccessTokenValidators {
    const val CLIENT_ID = "client_id"

    fun create(
        token: IdentityProperties.Token,
        clock: Clock,
    ): OAuth2TokenValidator<Jwt> {
        val validators =
            mutableListOf<OAuth2TokenValidator<Jwt>>(
                JwtTimestampValidator().apply { setClock(clock) },
                JwtIssuerValidator(token.issuerUri),
                JwtClaimValidator<Any?>(JwtClaimNames.AUD) { aud ->
                    (aud as? Collection<*>)?.any { it in token.audiences } == true
                },
            )
        if (token.clientIds.isNotEmpty()) {
            validators += JwtClaimValidator<String?>(CLIENT_ID) { it in token.clientIds }
        }
        return DelegatingOAuth2TokenValidator(validators)
    }
}
```

- [ ] **Step 5: `OidcJwtConfiguration.kt`'yi yaz, `CognitoJwtConfiguration.kt`'yi sil**

```kotlin
package com.soruhavuzu.identity.internal.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Clock

/** staging/prod: OIDC sağlayıcısının (Logto) JWKS'iyle imza doğrulaması; anahtarlar ilk istekte çekilir. */
@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
class OidcJwtConfiguration {
    @Bean
    fun jwtDecoder(
        properties: IdentityProperties,
        clock: Clock,
    ): JwtDecoder {
        val token = properties.token
        val jwkSetUri = requireNotNull(token.jwkSetUri) { "soruhavuzu.identity.token.jwk-set-uri zorunlu" }
        return NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri)
            .jwsAlgorithms { algorithms -> token.jwsAlgorithms.forEach { algorithms.add(SignatureAlgorithm.from(it)) } }
            .build()
            .apply { setJwtValidator(AccessTokenValidators.create(token, clock)) }
    }
}
```

- [ ] **Step 6: `DevTokenController.kt`: `aud` ekle, `token_use`'u kaldır**

`claims` oluşturucusunda `.claim(AccessTokenValidators.TOKEN_USE, AccessTokenValidators.ACCESS)` satırını sil, yerine şunu yaz:
```kotlin
                .audience(listOf(properties.token.audiences.first()))
```
`client_id` satırı aynen kalır: `request.clientId ?: properties.token.clientIds.first()`. Test ve local ayarlarında `clientIds` boş değildir.

- [ ] **Step 7: Yapılandırmaları güncelle**

`application.yml` → `soruhavuzu.identity` bloğu:
```yaml
  identity:
    token:
      provider: logto
      issuer-uri: ${OIDC_ISSUER_URI:}
      jwk-set-uri: ${OIDC_JWK_SET_URI:}
      audiences: ${OIDC_AUDIENCES:}
      client-ids: ${OIDC_CLIENT_IDS:}
      jws-algorithms: ${OIDC_JWS_ALGORITHMS:ES384,RS256}
```
`application-local.yml` → `soruhavuzu.identity.token`:
```yaml
      provider: local
      issuer-uri: https://auth.local.soruhavuzu.invalid
      audiences: http://localhost:8080
      client-ids: local-mobile,local-admin
```
`src/test/resources/application-test.yml` → `soruhavuzu.identity.token`:
```yaml
      provider: local
      issuer-uri: https://auth.test.soruhavuzu.invalid
      audiences: https://api.test.soruhavuzu.invalid
      client-ids: test-mobile,test-admin
```
Task 1 spike'ında `alg` yalnızca biri çıktıysa (ör. ES384) varsayılanı o değere daralt.

- [ ] **Step 8: Çalıştır, geçtiğini gör; tam kontrol**

Run: `cd backend && ./gradlew check --console=plain`
Expected: BUILD SUCCESSFUL. `TokenValidationTests` (8 test) ve tüm backend testleri PASS.

- [ ] **Step 9: Mutasyon kontrolü (testlerin gerçekten koruduğunu kanıtla)**

`AccessTokenValidators.kt`'de `aud` doğrulayıcısını geçici olarak `{ true }` yap. Çalıştır: `./gradlew test --tests 'com.soruhavuzu.identity.TokenValidationTests'`. Expected: `yanlış audience` ve `audience'sız` testleri FAIL. Değişikliği geri al, testleri yeniden çalıştır: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend
git commit -m "feat(identity): sağlayıcıdan bağımsız OIDC token doğrulaması (audience, JWKS, algoritmalar)"
```

---

### Task 3: Mobil — Logto (OIDC) girişi

**Files:**
- Create: `mobile/src/features/auth/api/oidc.ts`, `mobile/src/features/auth/api/oidc.test.ts`, `mobile/src/features/auth/components/OidcSignInButton.tsx`
- Delete: `mobile/src/features/auth/api/cognito.ts`, `mobile/src/features/auth/components/CognitoButton.tsx`
- Modify: `mobile/src/shared/config.ts`, `mobile/src/shared/config.test.ts`, `mobile/src/env.d.ts`, `mobile/.env.example`, `mobile/src/shared/i18n/tr.ts`, `mobile/src/features/auth/components/LoginForm.tsx`, `mobile/src/features/auth/hooks/AuthProvider.tsx`

**Interfaces:**
- Consumes: Task 2 (backend `aud` = `EXPO_PUBLIC_API_RESOURCE`).
- Produces:
  - `AppConfig` oidc kolu: `{ authMode: "oidc", apiUrl, assetBaseUrl, oidcIssuer, oidcClientId, apiResource }`
  - Ortam değişkenleri: `EXPO_PUBLIC_AUTH_MODE=oidc`, `EXPO_PUBLIC_OIDC_ISSUER`, `EXPO_PUBLIC_OIDC_CLIENT_ID`, `EXPO_PUBLIC_API_RESOURCE`
  - `oidc.ts` dışa aktarımları: `oidcSettings()`, `discovery(issuer)`, `toSession(token, previousRefreshToken?)`, `exchangeCode(result, codeVerifier)`, `refreshSession(session)`, `redirectUri`, `SCOPES`

- [ ] **Step 1: Başarısız testleri yaz**

`mobile/src/features/auth/api/oidc.test.ts`:
```ts
import { TokenResponse } from "expo-auth-session";

import { discovery, toSession } from "./oidc";

describe("Logto OIDC", () => {
  it("Logto uç noktalarını issuer'dan türetir", () => {
    expect(discovery("https://auth.example/oidc")).toEqual({
      authorizationEndpoint: "https://auth.example/oidc/auth",
      tokenEndpoint: "https://auth.example/oidc/token",
      revocationEndpoint: "https://auth.example/oidc/token/revocation",
      endSessionEndpoint: "https://auth.example/oidc/session/end",
    });
  });

  it("token yanıtını oturuma çevirir, yeni refresh token yoksa öncekini korur", () => {
    const token = new TokenResponse({ accessToken: "a", expiresIn: 3600, issuedAt: 1_000 });
    expect(toSession(token, "eski-refresh")).toEqual({
      accessToken: "a",
      expiresAt: 1_000 * 1000 + 3600 * 1000,
      refreshToken: "eski-refresh",
    });
  });
});
```
`mobile/src/shared/config.test.ts` dosyasındaki üçüncü testi (cognito) şununla değiştir:
```ts
  it("release build'de oidc yapılandırması eksiksiz verilirse kabul edilir", () => {
    const config = parseConfig(
      {
        EXPO_PUBLIC_AUTH_MODE: "oidc",
        EXPO_PUBLIC_API_URL: "https://api.example",
        EXPO_PUBLIC_ASSET_BASE_URL: "https://media.example",
        EXPO_PUBLIC_OIDC_ISSUER: "https://auth.example/oidc",
        EXPO_PUBLIC_OIDC_CLIENT_ID: "abc",
        EXPO_PUBLIC_API_RESOURCE: "https://api.example",
      },
      false,
    );
    expect(config.authMode).toBe("oidc");
  });
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `pnpm --filter @soruhavuzu/mobile test`
Expected: FAIL — `Cannot find module './oidc'` ve oidc yapılandırma testi (`parse` hata verir)

- [ ] **Step 3: `config.ts`'deki şemayı ve okuma kısmını güncelle**

`configSchema`:
```ts
const configSchema = z.discriminatedUnion("authMode", [
  z.object({ authMode: z.literal("dev"), ...common }),
  z.object({
    authMode: z.literal("oidc"),
    ...common,
    oidcIssuer: z.url(),
    oidcClientId: z.string().min(1),
    apiResource: z.url(),
  }),
]);
```
`parseConfig` gövdesinde cognito alanlarını şunlarla değiştir:
```ts
    oidcIssuer: env.EXPO_PUBLIC_OIDC_ISSUER,
    oidcClientId: env.EXPO_PUBLIC_OIDC_CLIENT_ID,
    apiResource: env.EXPO_PUBLIC_API_RESOURCE,
```
`config` sabitindeki env nesnesinde `EXPO_PUBLIC_COGNITO_*` satırlarını sil, şunları ekle:
```ts
    EXPO_PUBLIC_OIDC_ISSUER: process.env.EXPO_PUBLIC_OIDC_ISSUER,
    EXPO_PUBLIC_OIDC_CLIENT_ID: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID,
    EXPO_PUBLIC_API_RESOURCE: process.env.EXPO_PUBLIC_API_RESOURCE,
```
`src/env.d.ts`'de `EXPO_PUBLIC_COGNITO_DOMAIN` ve `EXPO_PUBLIC_COGNITO_CLIENT_ID` satırlarını şunlarla değiştir:
```ts
    readonly EXPO_PUBLIC_OIDC_ISSUER?: string;
    readonly EXPO_PUBLIC_OIDC_CLIENT_ID?: string;
    readonly EXPO_PUBLIC_API_RESOURCE?: string;
```
`.env.example`:
```
# dev: backend local profilindeki sahte JWT üretici · oidc: Logto (ADR 0024)
EXPO_PUBLIC_AUTH_MODE=dev
EXPO_PUBLIC_OIDC_ISSUER=http://localhost:3001/oidc
EXPO_PUBLIC_OIDC_CLIENT_ID=
EXPO_PUBLIC_API_RESOURCE=http://localhost:8080
```
(`EXPO_PUBLIC_COGNITO_*` satırları silinir.)

- [ ] **Step 4: `oidc.ts`'yi yaz (`cognito.ts`'yi sil)**

```ts
import {
  type AuthSessionResult,
  type DiscoveryDocument,
  exchangeCodeAsync,
  makeRedirectUri,
  refreshAsync,
  type TokenResponse,
} from "expo-auth-session";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

export const redirectUri = makeRedirectUri({ scheme: "soruhavuzu", path: "auth/callback" });
/** offline_access → refresh token; Logto bunun için consent ister (prompt=consent). */
export const SCOPES = ["openid", "offline_access"];

export function oidcSettings(): { issuer: string; clientId: string; resource: string } | null {
  return config.authMode === "oidc"
    ? { issuer: config.oidcIssuer, clientId: config.oidcClientId, resource: config.apiResource }
    : null;
}

/** Logto uç noktaları issuer (`https://auth.<host>/oidc`) altındadır. */
export function discovery(issuer: string): DiscoveryDocument {
  return {
    authorizationEndpoint: `${issuer}/auth`,
    tokenEndpoint: `${issuer}/token`,
    revocationEndpoint: `${issuer}/token/revocation`,
    endSessionEndpoint: `${issuer}/session/end`,
  };
}

export function toSession(token: TokenResponse, previousRefreshToken?: string): Session {
  const lifetimeSeconds = token.expiresIn ?? 3600;
  const refreshToken = token.refreshToken ?? previousRefreshToken;
  return {
    accessToken: token.accessToken,
    expiresAt: token.issuedAt * 1000 + lifetimeSeconds * 1000,
    ...(refreshToken ? { refreshToken } : {}),
  };
}

/** Yetkilendirme kodunu API resource'u için JWT access token'a çevirir (ADR 0024). */
export async function exchangeCode(result: AuthSessionResult, codeVerifier: string): Promise<Session> {
  const settings = oidcSettings();
  if (!settings || result.type !== "success") throw new Error("Giriş tamamlanmadı");
  const code = result.params.code;
  if (!code) throw new Error("Yetkilendirme kodu yok");
  const token = await exchangeCodeAsync(
    {
      clientId: settings.clientId,
      code,
      redirectUri,
      extraParams: { code_verifier: codeVerifier, resource: settings.resource },
    },
    discovery(settings.issuer),
  );
  return toSession(token);
}

export async function refreshSession(session: Session): Promise<Session | null> {
  const settings = oidcSettings();
  if (!settings || !session.refreshToken) return null;
  const token = await refreshAsync(
    { clientId: settings.clientId, refreshToken: session.refreshToken, extraParams: { resource: settings.resource } },
    discovery(settings.issuer),
  );
  return toSession(token, session.refreshToken);
}
```

- [ ] **Step 5: `OidcSignInButton.tsx`'yi yaz (`CognitoButton.tsx`'yi sil)**

```tsx
import { useAuthRequest } from "expo-auth-session";
import { useState } from "react";

import { Button } from "@/shared/ui";

import { discovery, exchangeCode, redirectUri, SCOPES } from "../api/oidc";
import { useAuth } from "../hooks/AuthProvider";

interface Props {
  issuer: string;
  clientId: string;
  resource: string;
  label: string;
  onError: () => void;
}

/** Logto'nun giriş/kayıt ekranı (PKCE); token API resource'u için istenir (ADR 0024). */
export function OidcSignInButton({ issuer, clientId, resource, label, onError }: Props) {
  const { completeSignIn } = useAuth();
  const [busy, setBusy] = useState(false);
  const [request, , promptAsync] = useAuthRequest(
    { clientId, redirectUri, scopes: SCOPES, usePKCE: true, extraParams: { resource, prompt: "consent" } },
    discovery(issuer),
  );

  const onPress = async () => {
    if (!request?.codeVerifier) return;
    setBusy(true);
    try {
      const result = await promptAsync();
      if (result.type === "success") await completeSignIn(await exchangeCode(result, request.codeVerifier));
    } catch {
      onError();
    } finally {
      setBusy(false);
    }
  };

  return <Button label={label} onPress={() => void onPress()} loading={busy} disabled={!request} />;
}
```

- [ ] **Step 6: `LoginForm.tsx`, `AuthProvider.tsx` ve `tr.ts`'yi güncelle**

`LoginForm.tsx`:
- `cognitoSettings` importu `import { oidcSettings } from "../api/oidc";` olur; `CognitoButton` importu `import { OidcSignInButton } from "./OidcSignInButton";` olur.
- `const cognito = cognitoSettings();` satırı `const oidc = oidcSettings();` olur.
- Üç `CognitoButton` satırının yerine tek satır gelir:
```tsx
      {oidc ? (
        <OidcSignInButton {...oidc} label={t("auth.continue")} onError={() => setFailed(true)} />
      ) : (
```
(`else` dalı, yani dev formu, aynen kalır.)

`AuthProvider.tsx`: `import { refreshSession } from "../api/cognito";` → `import { refreshSession } from "../api/oidc";`

`tr.ts` → `auth` içinde `withApple`, `withGoogle`, `withEmail` satırlarını sil, şunu ekle:
```ts
    continue: "Giriş yap veya kayıt ol",
```

- [ ] **Step 7: Çalıştır, geçtiğini gör**

Run: `pnpm --filter @soruhavuzu/mobile test && pnpm --filter @soruhavuzu/mobile typecheck && pnpm lint`
Expected: Tüm mobil testler PASS, typecheck ve lint temiz. `grep -rn -i cognito mobile/src mobile/app` çıktısı boş.

- [ ] **Step 8: Commit**

```bash
git add mobile
git commit -m "feat(mobile): Logto (OIDC, PKCE, API resource) ile giriş"
```

---

### Task 4: Admin — Logto girişi

**Files:**
- Modify: `admin/src/shared/config.ts`, `admin/src/shared/config.test.ts`, `admin/src/vite-env.d.ts`, `admin/.env.example`, `admin/src/features/auth/api/oidc.ts`, `admin/src/features/auth/hooks/AuthProvider.tsx`, `admin/src/features/auth/components/LoginPage.tsx`, `admin/src/shared/i18n/tr.ts`

**Interfaces:**
- Produces:
  - `AppConfig` oidc kolu: `{ authMode: "oidc", apiUrl, assetBaseUrl, oidcIssuer, oidcClientId, apiResource }`
  - Ortam değişkenleri: `VITE_AUTH_MODE=oidc`, `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`, `VITE_API_RESOURCE`
  - Yönlendirme: `<admin origin>/auth/callback`

- [ ] **Step 1: Başarısız testi yaz — `admin/src/shared/config.test.ts`'ye ekle**

```ts
  it("production build'de oidc yapılandırması eksiksiz verilirse kabul edilir", () => {
    const config = parseConfig(
      {
        VITE_AUTH_MODE: "oidc",
        VITE_API_URL: "https://api.example",
        VITE_ASSET_BASE_URL: "https://media.example",
        VITE_OIDC_ISSUER: "https://auth.example/oidc",
        VITE_OIDC_CLIENT_ID: "abc",
        VITE_API_RESOURCE: "https://api.example",
      },
      false,
    );
    expect(config.authMode).toBe("oidc");
  });
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `pnpm --filter @soruhavuzu/admin test`
Expected: FAIL — oidc kolu tanımlı değil

- [ ] **Step 3: `config.ts`, `vite-env.d.ts`, `.env.example`'ı güncelle**

`configSchema`:
```ts
const configSchema = z.discriminatedUnion("authMode", [
  z.object({ authMode: z.literal("dev"), ...common }),
  z.object({
    authMode: z.literal("oidc"),
    ...common,
    oidcIssuer: z.url(),
    oidcClientId: z.string().min(1),
    apiResource: z.url(),
  }),
]);
```
`parseConfig` içinde `cognitoAuthority`/`cognitoClientId` yerine:
```ts
    oidcIssuer: env.VITE_OIDC_ISSUER,
    oidcClientId: env.VITE_OIDC_CLIENT_ID,
    apiResource: env.VITE_API_RESOURCE,
```
`vite-env.d.ts`: `VITE_COGNITO_AUTHORITY` ve `VITE_COGNITO_CLIENT_ID` satırlarını şunlarla değiştir:
```ts
  readonly VITE_OIDC_ISSUER?: string;
  readonly VITE_OIDC_CLIENT_ID?: string;
  readonly VITE_API_RESOURCE?: string;
```
`.env.example`:
```
# dev: backend local profilindeki sahte JWT üretici · oidc: Logto (ADR 0024)
VITE_AUTH_MODE=dev
VITE_OIDC_ISSUER=http://localhost:3001/oidc
VITE_OIDC_CLIENT_ID=
VITE_API_RESOURCE=http://localhost:8080
```
(`VITE_COGNITO_*` satırları silinir.)

- [ ] **Step 4: `admin/src/features/auth/api/oidc.ts`'yi yaz**

```ts
import { UserManager, WebStorageStateStore } from "oidc-client-ts";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

/** Logto (Authorization Code + PKCE). Token API resource'u için istenir; yalnızca sekme ömrü boyunca tutulur. */
export const userManager: UserManager | null =
  config.authMode === "oidc"
    ? new UserManager({
        authority: config.oidcIssuer,
        client_id: config.oidcClientId,
        redirect_uri: `${window.location.origin}/auth/callback`,
        post_logout_redirect_uri: `${window.location.origin}/login`,
        response_type: "code",
        scope: "openid offline_access",
        extraQueryParams: { resource: config.apiResource, prompt: "consent" },
        extraTokenParams: { resource: config.apiResource },
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
        automaticSilentRenew: false,
      })
    : null;

export async function currentOidcSession(): Promise<Session | null> {
  const user = await userManager?.getUser();
  if (!user || user.expired || !user.expires_at) return null;
  return { accessToken: user.access_token, expiresAt: user.expires_at * 1000 };
}
```

- [ ] **Step 5: `AuthProvider.tsx`, `LoginPage.tsx`, `tr.ts`'deki adları değiştir**

- `AuthProvider.tsx`: `signInWithCognito` → `signInWithOidc`, `completeCognitoSignIn` → `completeOidcSignIn` (tanım, arayüz ve kullanımlar).
- `AuthCallback.tsx`: `completeCognitoSignIn` → `completeOidcSignIn`.
- `LoginPage.tsx`: `config.authMode === "cognito"` → `config.authMode === "oidc"`; `signInWithCognito` → `signInWithOidc`; `t("auth.cognito")` → `t("auth.oidc")`.
- `tr.ts`: `cognito: "Kurum hesabıyla giriş yap",` → `oidc: "Giriş yap",`

- [ ] **Step 6: Çalıştır, geçtiğini gör**

Run: `pnpm --filter @soruhavuzu/admin test && pnpm --filter @soruhavuzu/admin typecheck && pnpm lint && pnpm --filter @soruhavuzu/admin build`
Expected: PASS ve build başarılı. `grep -rn -i cognito admin/src` çıktısı boş.

- [ ] **Step 7: Lokal uçtan uca (Logto ile gerçek giriş)**

`backend/src/main/resources/application-local.yml`'ı değiştirmeden, Logto'ya karşı çalışan geçici bir profille doğrula:
```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=staging --spring.datasource.url=jdbc:postgresql://localhost:5432/soruhavuzu --spring.datasource.username=soruhavuzu --spring.datasource.password=soruhavuzu --soruhavuzu.identity.token.issuer-uri=http://localhost:3001/oidc --soruhavuzu.identity.token.jwk-set-uri=http://localhost:3001/oidc/jwks --soruhavuzu.identity.token.audiences=http://localhost:8080 --soruhavuzu.logging.user-id-hash-key=lokal --soruhavuzu.web.allowed-origins=http://localhost:5173'
```
`admin/.env.local`:
```
VITE_AUTH_MODE=oidc
VITE_OIDC_ISSUER=http://localhost:3001/oidc
VITE_OIDC_CLIENT_ID=<Task 1 SPA app id>
VITE_API_RESOURCE=http://localhost:8080
```
`make admin` → `http://localhost:5173` → "Giriş yap" → Logto'da kayıt ol → panelde "… olarak giriş yapıldı (USER)".
Expected: Giriş tamamlanır ve `GET /v1/me` 200 döner. `staging` profili `LocalProfileGuard`'ı etkilemez, çünkü local profil açık değildir.

- [ ] **Step 8: Commit**

```bash
git add admin
git commit -m "feat(admin): Logto (OIDC, PKCE, API resource) ile giriş"
```

---

### Task 5: Backend ve admin imajları (çok mimarili)

**Files:**
- Create: `backend/Dockerfile`, `backend/.dockerignore`, `admin/Dockerfile`, `admin/nginx.conf`, `admin/.dockerignore`, `backend/src/main/resources/application-staging.yml`
- Modify: `backend/build.gradle.kts` (bootJar adı), `backend/src/main/resources/application-prod.yml`, `Makefile` (`images` hedefi)

**Interfaces:**
- Produces:
  - `soruhavuzu-api` imajı: port 8080, sağlık `/actuator/health`. Ortam değişkenleri: `SPRING_PROFILES_ACTIVE`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `OIDC_*` (Task 2), `LOG_USER_ID_HASH_KEY`, `ALLOWED_ORIGINS`, `OTEL_TRACES_EXPORTER`.
  - `soruhavuzu-admin` imajı: port 8080, SPA.
  - `make images TAG=<t> OWNER=<o>` → `ghcr.io/<o>/soruhavuzu-api:<t>` ve `ghcr.io/<o>/soruhavuzu-admin:<t>` (yerel mimari).

- [ ] **Step 1: Başarısız doğrulamayı çalıştır**

Run: `make images TAG=smoke OWNER=local`
Expected: FAIL — `No rule to make target 'images'`

- [ ] **Step 2: Backend imajı**

`backend/.dockerignore`:
```
*
!build/libs/soruhavuzu-api.jar
!build/otel/opentelemetry-javaagent.jar
```
`backend/Dockerfile`:
```dockerfile
# Jar ve OTel agent'ı CI'da Gradle üretir (bootJar + otelAgentJar); imaj yalnızca çalışma ortamıdır.
FROM eclipse-temurin:25.0.4.1_1-jre-noble

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --chown=app:app build/libs/soruhavuzu-api.jar app.jar
COPY --chown=app:app build/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
USER app

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    OTEL_SERVICE_NAME=soruhavuzu-api \
    OTEL_TRACES_EXPORTER=none \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```
`backend/build.gradle.kts` sonuna:
```kotlin
tasks.bootJar {
    archiveFileName = "soruhavuzu-api.jar"
}
```
`application-staging.yml`:
```yaml
# Değerler Dokploy ortam değişkenlerinden gelir (ADR 0023)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

soruhavuzu:
  web:
    allowed-origins: ${ALLOWED_ORIGINS}
```
`application-prod.yml` (tam içerik):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

soruhavuzu:
  web:
    allowed-origins: ${ALLOWED_ORIGINS}

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

- [ ] **Step 3: Admin imajı**

`admin/.dockerignore`:
```
*
!dist
!nginx.conf
```
`admin/nginx.conf`:
```nginx
server {
  listen 8080;
  root /usr/share/nginx/html;
  server_tokens off;

  add_header X-Content-Type-Options nosniff always;
  add_header Referrer-Policy strict-origin-when-cross-origin always;
  add_header X-Frame-Options DENY always;

  location /assets/ {
    add_header Cache-Control "public, max-age=31536000, immutable";
    try_files $uri =404;
  }

  # SPA: bilinmeyen yollar index.html'e (React Router)
  location / {
    add_header Cache-Control "no-cache";
    try_files $uri /index.html;
  }
}
```
`admin/Dockerfile`:
```dockerfile
# dist/ CI'da `pnpm --filter @soruhavuzu/admin build` ile üretilir (VITE_* değerleri derlemede gömülür).
FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 8080
USER nginx
```
> nginx'in `USER nginx` ile 8080'de çalışabilmesi için `/var/cache/nginx` ve pid dosyası yazılabilir olmalı. Step 5'te imaj açılmazsa `USER nginx` satırını kaldır ve yerine resmi `nginxinc/nginx-unprivileged:1.30.5-alpine` imajını kullan. Bu imaj zaten 8080'de root olmadan çalışır.

- [ ] **Step 4: `Makefile` hedefi (`.PHONY`'ye `images` ekle)**

```make
images: ## Backend ve admin imajlarını yerel mimaride üretir: make images TAG=smoke OWNER=local
	$(GRADLE) bootJar otelAgentJar
	pnpm --filter @soruhavuzu/admin build
	docker build -t ghcr.io/$(OWNER)/soruhavuzu-api:$(TAG) backend
	docker build -t ghcr.io/$(OWNER)/soruhavuzu-admin:$(TAG) admin
```

- [ ] **Step 5: Çalıştır ve imajları doğrula**

Run:
```bash
make images TAG=smoke OWNER=local
docker run --rm --entrypoint id ghcr.io/local/soruhavuzu-api:smoke
docker run -d --name admin-smoke -p 18081:8080 ghcr.io/local/soruhavuzu-admin:smoke && curl -sf http://localhost:18081/preview | grep -q '<div id="root">' && docker rm -f admin-smoke
```
Expected:
- `id` çıktısında `uid` 0 değil.
- `/preview` (SPA yolu) index.html döner.
- API imajının çalışma doğrulaması Task 6'daki duman testindedir.

- [ ] **Step 6: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore backend/build.gradle.kts backend/src/main/resources/application-staging.yml backend/src/main/resources/application-prod.yml admin/Dockerfile admin/nginx.conf admin/.dockerignore Makefile
git commit -m "feat(shared): backend ve admin imajları, staging/prod profilleri"
```

---

### Task 6: `deploy/` — beta compose tanımı ve duman testi

**Files:**
- Create: `deploy/docker-compose.beta.yml`, `deploy/docker-compose.smoke.yml`, `deploy/elasticmq.conf`, `deploy/postgres-init.sql`, `deploy/.env.example`
- Modify: `Makefile` (`beta-smoke` hedefi), `.gitignore` (`deploy/.env`)

**Interfaces:**
- Consumes: Task 5 imajları.
- Produces: Dokploy compose ortam değişkenleri:
  - `BASE_HOST`, `GHCR_OWNER`, `IMAGE_TAG`
  - `DB_HOST`, `DB_PORT`, `DB_PASSWORD`, `LOGTO_DB_PASSWORD`
  - `OIDC_CLIENT_IDS`, `LOG_USER_ID_HASH_KEY`
  - `S3_ACCESS_KEY`, `S3_SECRET_KEY`

- [ ] **Step 1: Başarısız doğrulamayı çalıştır**

Run: `make beta-smoke`
Expected: FAIL — hedef yok

- [ ] **Step 2: `deploy/docker-compose.beta.yml`'ı yaz**

```yaml
# Dokploy "Compose" uygulaması (ADR 0023). Postgres, Dokploy'un yönetilen veritabanıdır (DB_HOST).
# Alan adları Dokploy arayüzünden servis başına eklenir (Traefik); burada port yayınlanmaz.
name: soruhavuzu-beta

x-limits: &limits
  restart: unless-stopped
  networks: [default, dokploy-network]

services:
  api:
    <<: *limits
    image: ghcr.io/${GHCR_OWNER}/soruhavuzu-api:${IMAGE_TAG:-beta}
    pull_policy: always
    environment:
      SPRING_PROFILES_ACTIVE: staging
      DB_HOST: ${DB_HOST}
      DB_PORT: ${DB_PORT:-5432}
      DB_NAME: soruhavuzu
      DB_USERNAME: soruhavuzu
      DB_PASSWORD: ${DB_PASSWORD}
      OIDC_ISSUER_URI: https://auth.${BASE_HOST}/oidc
      OIDC_JWK_SET_URI: http://logto:3001/oidc/jwks
      OIDC_AUDIENCES: https://api.${BASE_HOST}
      OIDC_CLIENT_IDS: ${OIDC_CLIENT_IDS}
      LOG_USER_ID_HASH_KEY: ${LOG_USER_ID_HASH_KEY}
      ALLOWED_ORIGINS: https://admin.${BASE_HOST}
    deploy: { resources: { limits: { memory: 1g } } }

  logto:
    <<: *limits
    image: svhd/logto:1.45.0
    entrypoint: ["sh", "-c", "npm run cli db seed -- --swe && npm start"]
    environment:
      TRUST_PROXY_HEADER: "1"
      DB_URL: postgres://logto:${LOGTO_DB_PASSWORD}@${DB_HOST}:${DB_PORT:-5432}/logto
      ENDPOINT: https://auth.${BASE_HOST}
      ADMIN_ENDPOINT: https://auth-admin.${BASE_HOST}
    deploy: { resources: { limits: { memory: 512m } } }

  s3:
    <<: *limits
    image: chrislusf/seaweedfs:4.48
    entrypoint: ["/bin/sh", "-c"]
    # Erişim anahtarları ortamdan gelir; yapılandırma dosyası açılışta üretilir (repoda sır yok)
    command:
      - >-
        printf '{"identities":[{"name":"api","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Admin","Read","List","Tagging","Write"]}]}'
        "$$S3_ACCESS_KEY" "$$S3_SECRET_KEY" > /tmp/s3.json &&
        exec weed server -dir=/data -s3 -s3.port=8333 -s3.config=/tmp/s3.json -ip.bind=0.0.0.0
    environment:
      S3_ACCESS_KEY: ${S3_ACCESS_KEY}
      S3_SECRET_KEY: ${S3_SECRET_KEY}
    volumes: [s3-data:/data]
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8333/status || exit 1"]
      interval: 5s
      retries: 30
    deploy: { resources: { limits: { memory: 256m } } }

  s3-init:
    image: chrislusf/seaweedfs:4.48
    depends_on:
      s3: { condition: service_healthy }
    entrypoint: ["/bin/sh", "-c"]
    command:
      - >-
        echo 's3.bucket.list' | weed shell -master=s3:9333 | grep -q 'soruhavuzu-media-beta' ||
        echo 's3.bucket.create -name soruhavuzu-media-beta' | weed shell -master=s3:9333
    restart: "no"

  sqs:
    <<: *limits
    image: softwaremill/elasticmq-native:1.7.1
    volumes:
      - ./elasticmq.conf:/opt/elasticmq.conf:ro
      - sqs-data:/data
    deploy: { resources: { limits: { memory: 128m } } }

  admin:
    <<: *limits
    image: ghcr.io/${GHCR_OWNER}/soruhavuzu-admin:${IMAGE_TAG:-beta}
    pull_policy: always
    deploy: { resources: { limits: { memory: 32m } } }

volumes:
  s3-data:
  sqs-data:

networks:
  dokploy-network:
    external: true
```

- [ ] **Step 3: `deploy/elasticmq.conf`, `deploy/postgres-init.sql`, `deploy/.env.example`**

`deploy/elasticmq.conf`:
```hocon
include classpath("application.conf")

node-address { protocol = http, host = sqs, port = 9324, context-path = "" }
rest-sqs { enabled = true, bind-port = 9324, bind-hostname = "0.0.0.0", sqs-limits = strict }

# Kalıcılık: kuyruk tanımları ve mesajlar volume'da (ADR 0023)
queues-storage { enabled = true, path = "/data/queues.conf" }
messages-storage { enabled = true, uri = "jdbc:h2:/data/elasticmq" }

queues {
  media-in { defaultVisibilityTimeout = 60 seconds, deadLettersQueue { name = "media-in-dlq", maxReceiveCount = 3 } }
  media-in-dlq {}
  media-results { defaultVisibilityTimeout = 30 seconds, deadLettersQueue { name = "media-results-dlq", maxReceiveCount = 5 } }
  media-results-dlq {}
}
```
`deploy/postgres-init.sql`:
```sql
-- Dokploy Postgres'inde bir kez, yönetici olarak çalıştırılır (runbook adım 3). Parolalar psql değişkeniyle verilir:
-- psql -v app_password=... -v logto_password=... -f postgres-init.sql
CREATE USER soruhavuzu WITH PASSWORD :'app_password';
CREATE DATABASE soruhavuzu OWNER soruhavuzu;
CREATE USER logto WITH PASSWORD :'logto_password';
CREATE DATABASE logto OWNER logto;
\connect soruhavuzu
CREATE EXTENSION IF NOT EXISTS ltree WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
```
`deploy/.env.example`:
```
# Dokploy → Compose → Environment. Değerleri yalnızca Dokploy'a gir; bu dosyaya gerçek değer yazma.
BASE_HOST=
GHCR_OWNER=
IMAGE_TAG=beta
DB_HOST=
DB_PORT=5432
DB_PASSWORD=
LOGTO_DB_PASSWORD=
OIDC_CLIENT_IDS=
LOG_USER_ID_HASH_KEY=
S3_ACCESS_KEY=
S3_SECRET_KEY=
```
`.gitignore` sonuna: `deploy/.env`

- [ ] **Step 4: Duman testi eki — `deploy/docker-compose.smoke.yml`**

```yaml
# Yalnızca CI/lokal duman testi: Dokploy ağı ve DB yerine geçici Postgres, yerel imajlar ve yayınlanan portlar.
services:
  postgres:
    image: pgvector/pgvector:0.8.7-pg17
    environment: { POSTGRES_USER: admin, POSTGRES_PASSWORD: admin, POSTGRES_DB: postgres }
    volumes: [./postgres-init.sql:/init/postgres-init.sql:ro]
    command: ["postgres"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 2s
      retries: 30
    networks: [default]
  db-init:
    image: pgvector/pgvector:0.8.7-pg17
    depends_on: { postgres: { condition: service_healthy } }
    environment: { PGPASSWORD: admin }
    command: ["psql", "-h", "postgres", "-U", "admin", "-d", "postgres", "-v", "app_password=smoke", "-v", "logto_password=smoke", "-f", "/init/postgres-init.sql"]
    volumes: [./postgres-init.sql:/init/postgres-init.sql:ro]
    networks: [default]
  # Ana dosyadaki [default, dokploy-network] listesi birleştirilmesin, değiştirilsin diye !override
  api:
    pull_policy: never
    networks: !override [default]
    depends_on: { db-init: { condition: service_completed_successfully } }
    ports: ["127.0.0.1:18080:8080"]
  logto:
    networks: !override [default]
    depends_on: { db-init: { condition: service_completed_successfully } }
    ports: ["127.0.0.1:13001:3001"]
  s3: { networks: !override [default], ports: ["127.0.0.1:18333:8333"] }
  sqs: { networks: !override [default], ports: ["127.0.0.1:19324:9324"] }
  admin: { pull_policy: never, networks: !override [default], ports: ["127.0.0.1:18082:8080"] }

networks:
  dokploy-network: !reset null
```
> `!override` ve `!reset` Docker Compose ≥ 2.24 ister (`docker compose version`).

`Makefile` (`.PHONY`'ye `beta-smoke` ekle):
```make
SMOKE := docker compose -f deploy/docker-compose.beta.yml -f deploy/docker-compose.smoke.yml --env-file deploy/smoke.env

beta-smoke: ## Beta compose'unu yerel imajlarla ayağa kaldırıp tüm servisleri doğrular
	printf 'BASE_HOST=smoke.localhost\nGHCR_OWNER=local\nIMAGE_TAG=smoke\nDB_HOST=postgres\nDB_PASSWORD=smoke\nLOGTO_DB_PASSWORD=smoke\nOIDC_CLIENT_IDS=\nLOG_USER_ID_HASH_KEY=smoke\nS3_ACCESS_KEY=smoke\nS3_SECRET_KEY=smoke-secret\n' > deploy/smoke.env
	$(SMOKE) up -d --wait --wait-timeout 300 postgres api logto s3 sqs admin
	curl -sf http://127.0.0.1:18080/actuator/health | grep -q '"UP"'
	test "$$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/v1/me)" = "401"
	curl -sf http://127.0.0.1:13001/oidc/.well-known/openid-configuration | grep -q '"issuer":"https://auth.smoke.localhost/oidc"'
	curl -sf http://127.0.0.1:18333/status >/dev/null
	curl -sf "http://127.0.0.1:19324/?Action=ListQueues" | grep -q media-in
	curl -sf http://127.0.0.1:18082/ | grep -q '<div id="root">'
	$(SMOKE) down -v
	rm -f deploy/smoke.env
	@echo "✓ beta compose: api, logto, s3, sqs, admin sağlıklı"
```
`.gitignore` sonuna: `deploy/smoke.env`

- [ ] **Step 5: Çalıştır, geçtiğini gör**

Run: `make images TAG=smoke OWNER=local && make beta-smoke`
Expected: `✓ beta compose: api, logto, s3, sqs, admin sağlıklı`.
- ElasticMQ native imajı H2 mesaj deposunu desteklemiyorsa (`sqs` açılmaz, log'da `messages-storage` hatası görünür): imajı `softwaremill/elasticmq:1.7.1` (JVM) yap, bellek sınırını 256m'e çıkar ve bunu ADR 0023'ün Sonuçlar bölümüne bir satır olarak ekle.
- Toplam bellek ölçümü: `docker stats --no-stream` çıktısında servis kullanımları, bellek tablosundaki sınırların altında olmalı.

- [ ] **Step 6: Commit**

```bash
git add deploy .gitignore Makefile
git commit -m "feat(shared): beta Dokploy compose tanımı ve uçtan uca duman testi"
```

---

### Task 7: GitHub Actions — CI, çok mimarili imaj, beta deploy

**Files:**
- Create: `.github/workflows/ci.yml`, `.github/actionlint.yaml`

**Interfaces:**
- Consumes: Task 5 (`make images`, Dockerfile'lar) ve Task 6 (`make beta-smoke`).
- GitHub'da tanımlanacaklar (Görev 10):
  - Repo değişkenleri: `BETA_BASE_HOST`, `BETA_OIDC_ADMIN_CLIENT_ID`
  - Gizli değişken: `DOKPLOY_DEPLOY_WEBHOOK`
  - Ortam: `beta`

- [ ] **Step 1: Başarısız doğrulamayı çalıştır**

Run: `ls .github/workflows/ci.yml`
Expected: FAIL — dosya yok

- [ ] **Step 2: `.github/workflows/ci.yml`'yi yaz**

```yaml
name: ci

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  backend:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - name: Lint, test, mimari testler (ktlint, detekt, Modulith, ArchUnit, Testcontainers)
        working-directory: backend
        run: ./gradlew check --console=plain
      - name: OpenAPI farkı
        run: git diff --exit-code -- packages/api-client/openapi.json

  js:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with: { node-version-file: .nvmrc, cache: pnpm }
      - run: pnpm install --frozen-lockfile
      - run: pnpm lint
      - run: pnpm typecheck
      - run: pnpm test
      - name: Üretilen dosyaların farkı
        run: |
          pnpm --filter @soruhavuzu/api-client generate
          pnpm --filter @soruhavuzu/content-schema generate
          git diff --exit-code -- packages/api-client/src/schema.ts packages/content-schema/schema.json

  gitleaks:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with: { fetch-depth: 0 }
      - uses: gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e # v3.0.0
        env: { GITHUB_TOKEN: "${{ secrets.GITHUB_TOKEN }}" }

  actionlint:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - run: docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.12 -color

  beta-smoke:
    runs-on: ubuntu-24.04
    needs: [backend, js]
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with: { node-version-file: .nvmrc, cache: pnpm }
      - run: pnpm install --frozen-lockfile
      - name: Beta compose duman testi (api, logto, s3, sqs, admin)
        run: make images TAG=smoke OWNER=local && make beta-smoke

  publish:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [backend, js, gitleaks, actionlint, beta-smoke]
    runs-on: ubuntu-24.04
    permissions:
      contents: read
      packages: write
    env:
      VITE_AUTH_MODE: oidc
      VITE_API_URL: https://api.${{ vars.BETA_BASE_HOST }}
      VITE_API_RESOURCE: https://api.${{ vars.BETA_BASE_HOST }}
      VITE_OIDC_ISSUER: https://auth.${{ vars.BETA_BASE_HOST }}/oidc
      VITE_OIDC_CLIENT_ID: ${{ vars.BETA_OIDC_ADMIN_CLIENT_ID }}
      VITE_ASSET_BASE_URL: https://media.${{ vars.BETA_BASE_HOST }}/soruhavuzu-media-beta
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with: { node-version-file: .nvmrc, cache: pnpm }
      - run: pnpm install --frozen-lockfile
      - name: Jar, OTel agent ve admin derlemesi
        run: |
          (cd backend && ./gradlew bootJar otelAgentJar --console=plain)
          pnpm --filter @soruhavuzu/admin build
      - uses: docker/setup-qemu-action@99012661954931238ded8c8b007157a8430204e1 # v4.4.0
      - uses: docker/setup-buildx-action@f87e5991a6d7451dcb8d9637bfbc97413f497069 # v4.4.1
      - uses: docker/login-action@dbcb813823bdd20940b903addbd779551569679f # v4.6.0
        with: { registry: ghcr.io, username: "${{ github.actor }}", password: "${{ secrets.GITHUB_TOKEN }}" }
      - name: API imajı (amd64 + arm64)
        uses: docker/build-push-action@c3c9e263c25d99ce0380d002d59b67737d91b0dc # v7.4.0
        with:
          context: backend
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/soruhavuzu-api:${{ github.sha }},ghcr.io/${{ github.repository_owner }}/soruhavuzu-api:beta
      - name: Admin imajı (amd64 + arm64)
        uses: docker/build-push-action@c3c9e263c25d99ce0380d002d59b67737d91b0dc # v7.4.0
        with:
          context: admin
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/soruhavuzu-admin:${{ github.sha }},ghcr.io/${{ github.repository_owner }}/soruhavuzu-admin:beta

  deploy-beta:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [publish]
    runs-on: ubuntu-24.04
    environment: beta
    steps:
      - name: Dokploy redeploy (compose :beta imajlarını çeker)
        env: { WEBHOOK: "${{ secrets.DOKPLOY_DEPLOY_WEBHOOK }}" }
        run: curl -fsS -X POST "$WEBHOOK" >/dev/null
      - name: Duman testi
        env: { API: "https://api.${{ vars.BETA_BASE_HOST }}" }
        run: |
          curl -sf --retry 40 --retry-delay 10 --retry-all-errors "$API/actuator/health" | grep -q '"UP"'
          test "$(curl -s -o /dev/null -w '%{http_code}' "$API/v1/me")" = "401"
```
`.github/actionlint.yaml`:
```yaml
config-variables:
  - BETA_BASE_HOST
  - BETA_OIDC_ADMIN_CLIENT_ID
```

- [ ] **Step 3: Çalıştır, geçtiğini gör**

Run: `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.12 -color && echo OK`
Expected: `OK`, uyarı yok.

- [ ] **Step 4: Commit**

```bash
git add .github
git commit -m "ci: lint/test/mimari/fark/gitleaks, beta duman testi, GHCR (amd64+arm64) ve Dokploy deploy"
```

---

### Task 8: EAS Build profilleri ve development client

**Files:**
- Create: `mobile/eas.json`
- Modify: `mobile/package.json` (`expo-dev-client: 57.0.20`), `mobile/app.json` (`plugins`), `pnpm-lock.yaml`

**Interfaces:**
- Produces: `development`, `preview` ve `production` profilleri. Değerler EAS sunucusundaki aynı adlı ortamlardan gelir (`eas env:create`, Görev 10); repoda değer yoktur.

- [ ] **Step 1: Başarısız doğrulamayı çalıştır**

Run: `ls mobile/eas.json`
Expected: FAIL — dosya yok

- [ ] **Step 2: Bağımlılık ve yapılandırma**

`mobile/package.json` → `dependencies` içine (alfabetik sırada): `"expo-dev-client": "57.0.20"`. Ardından `pnpm install`.
`mobile/app.json` → `plugins`: `["expo-router", "expo-secure-store", "expo-web-browser", "expo-dev-client"]`
`mobile/eas.json`:
```json
{
  "cli": { "version": ">= 25.0.0", "appVersionSource": "remote" },
  "build": {
    "development": { "developmentClient": true, "distribution": "internal", "environment": "development" },
    "preview": { "distribution": "internal", "environment": "preview", "android": { "buildType": "apk" } },
    "production": { "environment": "production", "autoIncrement": true }
  },
  "submit": { "production": {} }
}
```

- [ ] **Step 3: Doğrula**

Run: `cd mobile && pnpm dlx eas-cli@25.0.0 config --platform android --profile preview --non-interactive 2>&1 | tail -20`
Expected: `buildType: apk` ve `environment: preview` görünür, şema hatası yok. Oturum yoksa yalnızca "not logged in" uyarısı kabul edilir. Ayrıca `pnpm --filter @soruhavuzu/mobile typecheck && pnpm --filter @soruhavuzu/mobile test` PASS.

- [ ] **Step 4: Commit**

```bash
git add mobile/eas.json mobile/package.json mobile/app.json pnpm-lock.yaml
git commit -m "feat(mobile): EAS build profilleri ve development client"
```

---

### Task 9: Runbook ve README

**Files:**
- Create: `deploy/README.md`
- Modify: `README.md` ("Deploy" bölümü)

- [ ] **Step 1: `deploy/README.md`'yi yaz**

````markdown
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
   - Branch protection: `main`'e doğrudan push kapalı, `ci` zorunlu (kurallar.md §8).
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
````

- [ ] **Step 2: README'ye "Deploy" bölümü ekle (Hızlı başlangıç'tan sonra)**

```markdown
## Deploy

Beta, tek bir VPS'te Dokploy ile çalışır ([ADR 0023](docs/adr/0023-single-server-dokploy.md); kimlik: Logto, [ADR 0024](docs/adr/0024-logto-identity.md)).
- `main`'e merge → CI → GHCR imajları (amd64 + arm64) → Dokploy redeploy → duman testi.
- Kurulum adımları: [deploy/README.md](deploy/README.md). Yerel doğrulama: `make images TAG=smoke OWNER=local && make beta-smoke`.
```

- [ ] **Step 3: Commit**

```bash
git add deploy/README.md README.md
git commit -m "docs: beta sunucusu runbook'u ve README deploy bölümü"
```

---

### Task 10: (ONAYLI) İlk beta kurulumu ve Faz 0 kapanışı

- [ ] **Step 1: DUR — kullanıcıdan iste:**
  - Sunucu IP'si
  - GitHub `owner/repo` (ya da repo oluşturma onayı) ve push onayı
  - SMTP bilgisi (kullanıcı Logto konsoluna kendisi girer)
  - Runbook'taki Dokploy adımlarını kullanıcıyla birlikte yürütme onayı

  Parola ve token'lar sohbette paylaşılmaz; kullanıcı onları doğrudan Dokploy, GitHub ve Logto'ya girer.
- [ ] **Step 2 (onaydan sonra):** Runbook 1–9'u uygula. `main`'e ilk merge → CI yeşil → `deploy-beta` duman testi yeşil.
- [ ] **Step 3:** fazlar.md'de Faz 0 kabul kriterlerini kanıtla birlikte işaretle; Faz 0 → ✅ (fazlar.md v1.2 değişiklik kaydı). NotebookLM'e Faz 0 kapanış raporu yaz. Kalan cihaz testi (QuestionBody offline) kullanıcıdan alınır.
