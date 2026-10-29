import { z } from "zod";

const common = { apiUrl: z.url(), assetBaseUrl: z.url() };

const configSchema = z.discriminatedUnion("authMode", [
  z.object({ authMode: z.literal("dev"), ...common }),
  z.object({ authMode: z.literal("cognito"), ...common, cognitoAuthority: z.url(), cognitoClientId: z.string().min(1) }),
]);

export type AppConfig = z.infer<typeof configSchema>;

export const config: AppConfig = configSchema.parse({
  authMode: import.meta.env.VITE_AUTH_MODE ?? "dev",
  apiUrl: import.meta.env.VITE_API_URL ?? "http://localhost:8080",
  assetBaseUrl: import.meta.env.VITE_ASSET_BASE_URL ?? "http://localhost:8333/soruhavuzu-media-local",
  cognitoAuthority: import.meta.env.VITE_COGNITO_AUTHORITY,
  cognitoClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
});
