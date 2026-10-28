import {
  type AuthSessionResult,
  type DiscoveryDocument,
  exchangeCodeAsync,
  makeRedirectUri,
  refreshAsync,
  type TokenResponse,
} from "expo-auth-session";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

/** Cognito managed login'de doğrudan sağlayıcıya gitmek için `identity_provider` değeri. */
export type IdentityProvider = "SignInWithApple" | "Google" | "COGNITO";

export const redirectUri = makeRedirectUri({ scheme: "soruhavuzu", path: "auth/callback" });
export const SCOPES = ["openid", "email"];

export function cognitoSettings(): { domain: string; clientId: string } | null {
  return config.authMode === "cognito" ? { domain: config.cognitoDomain, clientId: config.cognitoClientId } : null;
}

export function discovery(domain: string): DiscoveryDocument {
  return {
    authorizationEndpoint: `${domain}/oauth2/authorize`,
    tokenEndpoint: `${domain}/oauth2/token`,
    revocationEndpoint: `${domain}/oauth2/revoke`,
  };
}

function toSession(token: TokenResponse, previousRefreshToken?: string): Session {
  const lifetimeSeconds = token.expiresIn ?? 3600;
  const refreshToken = token.refreshToken ?? previousRefreshToken;
  return {
    accessToken: token.accessToken,
    expiresAt: token.issuedAt * 1000 + lifetimeSeconds * 1000,
    ...(refreshToken ? { refreshToken } : {}),
  };
}

/** Yetkilendirme kodunu PKCE doğrulayıcısıyla access token'a çevirir (backend yalnızca access token kabul eder). */
export async function exchangeCode(result: AuthSessionResult, codeVerifier: string): Promise<Session> {
  const settings = cognitoSettings();
  if (!settings || result.type !== "success") throw new Error("Cognito girişi tamamlanmadı");
  const code = result.params.code;
  if (!code) throw new Error("Yetkilendirme kodu yok");
  const token = await exchangeCodeAsync(
    { clientId: settings.clientId, code, redirectUri, extraParams: { code_verifier: codeVerifier } },
    discovery(settings.domain),
  );
  return toSession(token);
}

export async function refreshSession(session: Session): Promise<Session | null> {
  const settings = cognitoSettings();
  if (!settings || !session.refreshToken) return null;
  const token = await refreshAsync(
    { clientId: settings.clientId, refreshToken: session.refreshToken, scopes: SCOPES },
    discovery(settings.domain),
  );
  return toSession(token, session.refreshToken);
}
