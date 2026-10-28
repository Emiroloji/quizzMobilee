# ADR 0004 — React Native + Expo

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
iOS ve Android tek kod tabanıyla çıkmalı; admin paneliyle tip ve render paketi paylaşılmalı.

## Karar
Mobil uygulama **React Native + Expo** (TypeScript strict), yönlendirme **expo-router**, sunucu durumu **TanStack Query**, yerel UI durumu **Zustand**, offline DB **expo-sqlite + Drizzle**, formlar **react-hook-form + zod**. Build ve dağıtım **EAS**.

## Alternatifler
- **Flutter:** Dart; admin ile paylaşım yok.
- **Native (Swift/Kotlin):** İki kod tabanı, tek kişiye ağır.

## Sonuçlar
- (+) `content-schema` ve `api-client` paketleri mobil ve admin arasında paylaşılır.
- (+) OTA güncelleme (yalnızca JS değişikliklerinde).
- (−) Matematik render WebView üzerinden (bkz. ADR 0005).
