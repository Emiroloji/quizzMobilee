# ADR 0024 — Kimlik sağlayıcı: Logto (self-hosted OIDC)

- **Tarih:** 2026-10-29
- **Durum:** Kabul
- **İlgili:** ADR 0009'un yerini alır (Cognito → Logto). ADR 0018'in Cognito'ya özel doğrulama kurallarını değiştirir; ilk istekte kullanıcı oluşturma ve rolün DB'den okunması aynen kalır.

## Bağlam
ADR 0023 ile AWS kullanılmayacak, bu yüzden Cognito'nun yerine kendi sunucumuzda çalışan bir OIDC sağlayıcı gerekiyor. İhtiyaçlar:
- E-posta ile giriş, Apple ve Google ile giriş
- Mobil (PKCE) ve SPA istemcileri
- Hazır giriş ve kayıt ekranları
- Düşük bellek kullanımı
- Lisans politikamızla uyum

## Karar
- **Logto** (açık kaynak sürüm, MPL-2.0) ayrı bir servis olarak çalışır. Postgres'te kendi `logto` veritabanını ve kullanıcısını kullanır. Adresler: `auth.<host>` (OIDC ve giriş ekranları) ve `auth-admin.<host>` (yönetim konsolu).
- Logto'da API resource tanımlanır (indicator `https://api.<host>`). Access token JWT'dir.
- **Backend doğrulaması:**
  - İmza (JWKS)
  - `iss` = `https://auth.<host>/oidc`
  - `exp` ve `nbf`
  - `aud`, yapılandırılan resource indicator'ı içermeli
  - `client_id` izin listesi (claim varsa)
  
  Cognito'ya özel `token_use` kontrolü kaldırılır. Yapılandırma `soruhavuzu.identity.token.{issuer-uri, audiences, client-ids}` ile verilir; sağlayıcıya özel kod yazılmaz.
- **İstemciler:**
  - Mobil: Native uygulama, PKCE, `soruhavuzu://auth/callback`.
  - Admin: SPA, PKCE.
  - İkisi de `resource=https://api.<host>` ister.
- **Giriş yöntemleri:** Beta'da e-posta. Apple ve Google, alan adı alınınca Logto bağlayıcılarıyla eklenir.
- `sub` yalnızca `identity` modülünde görünür; uygulama içi kimlik `UserId`'dir (ADR 0009'daki ilke korunur). `auth_identities.provider` değeri `logto` olur.

## Alternatifler
- **Keycloak** (Apache-2.0, en olgun): Apple yerleşik değil, topluluk eklentisi gerekir; daha fazla bellek.
- **Zitadel:** AGPL; v4'ten beri ayrı bir giriş servisi.
- **Spring Authorization Server (kendi auth'umuz):** ADR 0009 güvenlik ve bakım riski gerekçesiyle reddetmişti.

## Sonuçlar
- (+) Sağlayıcıdan bağımsız doğrulama; alan adı gelince yalnızca adresler değişir.
- (+) Hazır giriş ekranları ve Türkçe arayüz.
- (−) Bir servis daha işletilecek (yaklaşık 512 MB).
- (−) Access token claim'leri ilk deneme (spike) ile doğrulanmalı; plan bunu ilk görev olarak içerir.
- (−) SMTP olmadan e-posta doğrulaması çalışmaz; beta'da SMTP sağlayıcısı gerekir.

## Ek: Spike sonucu (2026-10-29, Logto 1.45.0)
client_credentials ile alınan access token: alg=ES384 (typ=at+jwt), iss=http://localhost:3001/oidc, aud=http://localhost:8080, client_id=mevcut, token_use yok. Backend `jws-algorithms` varsayılanı buna göre ayarlandı.
