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

export const redirectUri = makeRedirectUri({ scheme: "soruhavuzu", path: "auth/callback" });
/** offline_access → refresh token; Logto bunun için consent ister (prompt=consent). */
export const SCOPES = ["openid", "offline_access"];

export function oidcSettings(): { issuer: string; clientId: string; resource: string } | null {
  return config.authMode === "oidc"
    ? { issuer: config.oidcIssuer, clientId: config.oidcClientId, resource: config.apiResource }
    : null;
}

/** Logto uç noktaları issuer (`https://auth.<host>/oidc`) altındadır. */
export function discovery(issuer: string): DiscoveryDocument {
  return {
    authorizationEndpoint: `${issuer}/auth`,
    tokenEndpoint: `${issuer}/token`,
    revocationEndpoint: `${issuer}/token/revocation`,
    endSessionEndpoint: `${issuer}/session/end`,
  };
}

export function toSession(token: TokenResponse, previousRefreshToken?: string): Session {
  const lifetimeSeconds = token.expiresIn ?? 3600;
  const refreshToken = token.refreshToken ?? previousRefreshToken;
  return {
    accessToken: token.accessToken,
    expiresAt: token.issuedAt * 1000 + lifetimeSeconds * 1000,
    ...(refreshToken ? { refreshToken } : {}),
  };
}

/** Yetkilendirme kodunu API resource'u için JWT access token'a çevirir (ADR 0024). */
export async function exchangeCode(result: AuthSessionResult, codeVerifier: string): Promise<Session> {
  const settings = oidcSettings();
  if (!settings || result.type !== "success") throw new Error("Giriş tamamlanmadı");
  const code = result.params.code;
  if (!code) throw new Error("Yetkilendirme kodu yok");
  const token = await exchangeCodeAsync(
    {
      clientId: settings.clientId,
      code,
      redirectUri,
      extraParams: { code_verifier: codeVerifier, resource: settings.resource },
    },
    discovery(settings.issuer),
  );
  return toSession(token);
}

export async function refreshSession(session: Session): Promise<Session | null> {
  const settings = oidcSettings();
  if (!settings || !session.refreshToken) return null;
  const token = await refreshAsync(
    { clientId: settings.clientId, refreshToken: session.refreshToken, extraParams: { resource: settings.resource } },
    discovery(settings.issuer),
  );
  return toSession(token, session.refreshToken);
}
