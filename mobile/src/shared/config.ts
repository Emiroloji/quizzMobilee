import { z } from "zod";

// EXPO_PUBLIC_* değerleri derlemede uygulamaya gömülür; burada yalnızca açık (gizli olmayan) ayarlar olur.
const common = { apiUrl: z.url(), assetBaseUrl: z.url() };

const configSchema = z.discriminatedUnion("authMode", [
  z.object({ authMode: z.literal("dev"), ...common }),
  z.object({
    authMode: z.literal("cognito"),
    ...common,
    cognitoDomain: z.url(),
    cognitoClientId: z.string().min(1),
  }),
]);

export type AppConfig = z.infer<typeof configSchema>;

const DEV_DEFAULTS = {
  authMode: "dev",
  apiUrl: "http://localhost:8080",
  assetBaseUrl: "http://localhost:8333/soruhavuzu-media-local",
} as const;

/**
 * Yerel varsayılanlar yalnızca geliştirmede uygulanır; release build'de eksik ayar açılışta hata verir,
 * sessizce sahte girişe düşmez.
 */
export function parseConfig(env: Readonly<Record<string, string | undefined>>, isDev: boolean): AppConfig {
  const fallback: Partial<Record<keyof typeof DEV_DEFAULTS, string>> = isDev ? DEV_DEFAULTS : {};
  return configSchema.parse({
    authMode: env.EXPO_PUBLIC_AUTH_MODE ?? fallback.authMode,
    apiUrl: env.EXPO_PUBLIC_API_URL ?? fallback.apiUrl,
    assetBaseUrl: env.EXPO_PUBLIC_ASSET_BASE_URL ?? fallback.assetBaseUrl,
    cognitoDomain: env.EXPO_PUBLIC_COGNITO_DOMAIN,
    cognitoClientId: env.EXPO_PUBLIC_COGNITO_CLIENT_ID,
  });
}

// Expo yalnızca process.env.EXPO_PUBLIC_X biçimindeki statik erişimleri gömer; bu yüzden tek tek okunur
export const config: AppConfig = parseConfig(
  {
    EXPO_PUBLIC_AUTH_MODE: process.env.EXPO_PUBLIC_AUTH_MODE,
    EXPO_PUBLIC_API_URL: process.env.EXPO_PUBLIC_API_URL,
    EXPO_PUBLIC_ASSET_BASE_URL: process.env.EXPO_PUBLIC_ASSET_BASE_URL,
    EXPO_PUBLIC_COGNITO_DOMAIN: process.env.EXPO_PUBLIC_COGNITO_DOMAIN,
    EXPO_PUBLIC_COGNITO_CLIENT_ID: process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID,
  },
  __DEV__,
);
