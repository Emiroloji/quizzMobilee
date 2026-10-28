// Expo'nun ürettiği expo-env.d.ts gitignore'dadır; temiz klonda ve CI'da tipler buradan gelir.
/// <reference types="expo/types" />

declare namespace NodeJS {
  interface ProcessEnv {
    readonly EXPO_PUBLIC_API_URL?: string;
    readonly EXPO_PUBLIC_AUTH_MODE?: string;
    readonly EXPO_PUBLIC_COGNITO_DOMAIN?: string;
    readonly EXPO_PUBLIC_COGNITO_CLIENT_ID?: string;
    readonly EXPO_PUBLIC_ASSET_BASE_URL?: string;
  }
}
