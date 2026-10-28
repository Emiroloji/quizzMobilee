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

export const config: AppConfig = configSchema.parse({
  authMode: process.env.EXPO_PUBLIC_AUTH_MODE ?? "dev",
  apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
  assetBaseUrl: process.env.EXPO_PUBLIC_ASSET_BASE_URL ?? "http://localhost:4566/soruhavuzu-media-local",
  cognitoDomain: process.env.EXPO_PUBLIC_COGNITO_DOMAIN,
  cognitoClientId: process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID,
});
