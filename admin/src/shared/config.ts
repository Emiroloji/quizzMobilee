import { z } from "zod";

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

/** Yerel varsayılanlar yalnızca geliştirmede; production build'de eksik ayar açılışta hata verir. */
export function parseConfig(env: Readonly<Record<string, string | undefined>>, isDev: boolean): AppConfig {
  const fallback: Partial<Record<keyof typeof DEV_DEFAULTS, string>> = isDev ? DEV_DEFAULTS : {};
  return configSchema.parse({
    authMode: env.VITE_AUTH_MODE ?? fallback.authMode,
    apiUrl: env.VITE_API_URL ?? fallback.apiUrl,
    assetBaseUrl: env.VITE_ASSET_BASE_URL ?? fallback.assetBaseUrl,
    oidcIssuer: env.VITE_OIDC_ISSUER,
    oidcClientId: env.VITE_OIDC_CLIENT_ID,
    apiResource: env.VITE_API_RESOURCE,
  });
}

export const config: AppConfig = parseConfig(import.meta.env, import.meta.env.DEV);
