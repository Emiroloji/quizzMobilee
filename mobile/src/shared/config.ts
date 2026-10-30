import { z } from "zod";

// EXPO_PUBLIC_* değerleri derlemede uygulamaya gömülür; burada yalnızca açık (gizli olmayan) ayarlar olur.
const common = { apiUrl: z.url(), assetBaseUrl: z.url() };

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
    oidcIssuer: env.EXPO_PUBLIC_OIDC_ISSUER,
    oidcClientId: env.EXPO_PUBLIC_OIDC_CLIENT_ID,
    apiResource: env.EXPO_PUBLIC_API_RESOURCE,
  });
}

// Expo yalnızca process.env.EXPO_PUBLIC_X biçimindeki statik erişimleri gömer; bu yüzden tek tek okunur
export const config: AppConfig = parseConfig(
  {
    EXPO_PUBLIC_AUTH_MODE: process.env.EXPO_PUBLIC_AUTH_MODE,
    EXPO_PUBLIC_API_URL: process.env.EXPO_PUBLIC_API_URL,
    EXPO_PUBLIC_ASSET_BASE_URL: process.env.EXPO_PUBLIC_ASSET_BASE_URL,
    EXPO_PUBLIC_OIDC_ISSUER: process.env.EXPO_PUBLIC_OIDC_ISSUER,
    EXPO_PUBLIC_OIDC_CLIENT_ID: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID,
    EXPO_PUBLIC_API_RESOURCE: process.env.EXPO_PUBLIC_API_RESOURCE,
  },
  __DEV__,
);
