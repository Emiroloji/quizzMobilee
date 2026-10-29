# ADR 0018 — Cognito access token doğrulaması ve ilk istekte kullanıcı oluşturma

- **Tarih:** 2026-10-28
- **Durum:** Kısmen yerini aldı: 0024 (Cognito'ya özel token kuralları; ilk istekte kullanıcı oluşturma ve DB rolü geçerli)

## Bağlam
mimari.md §14, JWT'nin "issuer + audience + imza" ile doğrulanmasını ve ilk istekte iç kullanıcının oluşturulmasını istiyor. Cognito'da iki tür token var:
- **ID token:** `aud` alanı vardır, ancak kimlik bilgisi taşımak için tasarlanmıştır, API yetkilendirmesi için değil.
- **Access token:** API için tasarlanmıştır, ama `aud` alanı yoktur; bunun yerine `client_id` ve `token_use=access` alanları vardır.

Ayrıca kurallar.md A10, IdP `sub` değerinin yalnızca `identity` modülünde görünmesini istiyor. Diğer modüllerin controller'larının da mevcut kullanıcıyı bilmesi gerekiyor.

## Karar
- Backend yalnızca **access token** kabul eder. "Audience" kontrolü, `client_id` alanının izinli istemci listesinde olması ve `token_use == "access"` olmasıyla yapılır. Ayrıca issuer, imza (JWKS) ve süre doğrulanır.
- Doğrulanan `Jwt`, `identity` modülündeki dönüştürücüyle **`AuthenticatedUser(userId)`** principal'ına çevrilir (`shared` içinde). Diğer modüller `@AuthenticationPrincipal AuthenticatedUser` kullanır; `Jwt` tipini görmez (ArchUnit).
- `(provider, subject)` eşlemesi yoksa kullanıcı oluşturulur (takma ad geçici olarak `kullanici_xxxxxxxx` biçiminde üretilir; onboarding'de değiştirilir) ve `UserRegistered` olayı yayınlanır. Aynı kişinin eşzamanlı ilk istekleri birincil anahtar çakışmasıyla tek kullanıcıya iner.
- Rol, token'dan değil **veritabanından** okunur (`ROLE_<role>`). `ACTIVE` olmayan kullanıcı 403 `ACCOUNT_INACTIVE` alır.
- `local` ve `test` profillerinde, açılışta üretilen RSA anahtarıyla imzalı token veren **sahte JWT üretici** (`POST /v1/dev/tokens`) ve buna bağlı decoder kullanılır. Doğrulama kuralları (`client_id`, `token_use`) Cognito ile aynıdır. Seçim bean profiliyle yapılır, kod içinde dallanma yoktur (kurallar.md §3.5).

## Alternatifler
- **ID token kabul etmek:** `aud` kontrolü kolaylaşır ama ID token API yetkilendirmesi için önerilmez ve daha fazla kişisel veri taşır.
- **Rolü Cognito grubundan okumak:** Rolün iki kaynağı olur; moderatör ataması Cognito konsoluna bağımlı hale gelir.

## Sonuçlar
- (+) `sub` değeri identity dışına sızmaz; IdP değişimi tek modülü etkiler.
- (−) Her istekte kullanıcı satırı için indeksli bir okuma yapılır. Gerekirse ileride kısa süreli önbellek eklenir (ADR gerekir).
