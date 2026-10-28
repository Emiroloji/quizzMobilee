# ADR 0021 — Mobil ve admin araç zinciri seçimleri

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Faz 0'da mobil (Expo) ve admin (Vite) iskeletleri kuruldu. mimari.md §2'deki yığın genel çerçeveyi veriyor. Sürüm ve araç seçimleri ADR 0020 (en az 14 günlük sürümler) ve "sıkıcı teknoloji" ilkesiyle yapıldı.

## Karar
1. **Expo SDK 57** (RN 0.86.3, React 19.2.3). En az 14 günlük her SDK 58 yaması React Native **0.88.0-rc.4** (release candidate) kullanıyor. SDK 58'e, kararlı RN ile çıkan bir yama geldiğinde geçilir.
   Sonuç: Mağazadaki iOS Expo Go en son SDK'yı çalıştırır; iOS cihaz testi **development build** (EAS) ile yapılır. Android'de Expo CLI uyumlu Expo Go'yu kurabilir.
2. **Tek React sürümü** (19.2.3, SDK'nın sabitlediği). Admin'de React Router **7.18** kullanılır, çünkü RR 8, React ≥ 19.2.7 istiyor.
3. **TypeScript 5.9.3** tüm workspace'te.
4. pnpm **`auto-install-peers=false`**. Peer bağımlılıklar sessizce "en yeni" sürümle kurulmaz; Expo SDK'nın istediği sürümler açıkça yazılır (worklets, reanimated, gesture-handler, metro-config, jest-preset).
5. **Feature izolasyonu** `eslint-plugin-boundaries` yerine ESLint'in kendi `no-restricted-imports` kuralıyla zorlanır (kurallar.md §6). boundaries 7.x yapılandırma API'sini değiştirdi; aynı garanti ek eklentisiz ve öngörülebilir biçimde sağlanıyor. Kurallar:
   - Başka feature'a yalnızca `@/features/<ad>` (index) ile erişilir.
   - Feature dışına göreli import (`../../`) yapılmaz.
   - `shared`, feature'lara bağımlı olamaz.
6. **KaTeX belgesi:** `content-schema.renderDocument()`, fontları base64 gömülü, betiksiz ve CSP'li bağımsız HTML üretir. Mobil QuestionBody (WebView) ve admin önizlemesi (sandbox'lı iframe) aynı belgeyi gösterir. Gömülü fontlar gerekli, çünkü sandbox'lı iframe'in opak origin'i dış font isteklerini CORS'a takar ve offline çalışma şart.
7. **Kimlik:**
   - Mobilde `expo-auth-session` (PKCE, Cognito managed login; `identity_provider` ile doğrudan Apple/Google).
   - Admin'de `oidc-client-ts`. Token'lar mobilde `expo-secure-store`, admin'de `sessionStorage` (sekme ömrü) içinde tutulur.
   - `dev` modunda backend'in sahte JWT ucu kullanılır. Bu, sözleşme dışı olduğu için "elle fetch yok" kuralının tek istisnasıdır ve gerekçeli eslint-disable ile işaretlidir.
8. Backend'e **yapılandırılabilir CORS** eklendi (`soruhavuzu.web.allowed-origins`). Kimlik başlıkla taşındığı için credentials kapalı.

## Sonuçlar
- (+) Kararlı runtime'lar; tek React; peer sürümleri denetimli.
- (+) İçerik her yerde aynı görünür ve offline çalışır.
- (−) iOS cihaz testi için Apple Developer hesabı + EAS development build gerekir.
- (−) Önizleme paketi (gömülü fontlar) yaklaşık 350 KB gzip; route düzeyinde lazy yüklenir.
