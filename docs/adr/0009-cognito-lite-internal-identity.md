# ADR 0009 — Cognito Lite + IdP'den bağımsız iç kullanıcı kimliği

- **Tarih:** 2026-10-28
- **Durum:** Yerini aldı: 0024 (Cognito → Logto; iç kimlik ilkesi korunur)

## Bağlam
Apple (iOS'ta zorunlu), Google ve e-posta girişi gerekiyor. Kimlik sağlayıcı ileride değişebilir.

## Karar
Kimlik doğrulama **AWS Cognito (Lite plan)** ile yapılır. Backend JWT'yi Spring Security Resource Server ile doğrular. İlk istekte `(provider, subject)` → `identity.users` eşlemesi yapılır. Uygulama içinde her yerde iç `UserId` kullanılır; IdP `sub` yalnızca `identity` modülünde görünür (`auth_identities` tablosu). `local` profilde sahte JWT üretici kullanılır.

## Alternatifler
- **Auth0 / Clerk:** Daha iyi DX, ancak MAU başına maliyet ve AWS dışı bağımlılık.
- **Kendi auth'umuz:** Güvenlik riski ve bakım maliyeti.

## Sonuçlar
- (+) 10k MAU'ya kadar ücretsiz; AWS içinde.
- (+) IdP değişirse yalnızca `identity` modülü etkilenir.
