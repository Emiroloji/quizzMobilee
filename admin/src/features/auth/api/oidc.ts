import { UserManager, type UserManagerSettings, WebStorageStateStore } from "oidc-client-ts";

import { type AppConfig, config } from "@/shared/config";

import type { Session } from "../model/session";

type OidcConfig = Extract<AppConfig, { authMode: "oidc" }>;

/**
 * Logto (Authorization Code + PKCE). Token API resource'u için istenir; yalnızca sekme ömrü boyunca tutulur.
 * Admin'de sessiz yenileme yok; bu yüzden refresh token (`offline_access`) ve `prompt=consent` istenmez.
 */
export function oidcSettings(cfg: OidcConfig, origin: string): UserManagerSettings {
  return {
    authority: cfg.oidcIssuer,
    client_id: cfg.oidcClientId,
    redirect_uri: `${origin}/auth/callback`,
    // Logto SPA uygulamasında "Post sign-out redirect URI" olarak kayıtlı olmalı (deploy/README.md)
    post_logout_redirect_uri: `${origin}/login`,
    response_type: "code",
    scope: "openid",
    extraQueryParams: { resource: cfg.apiResource },
    extraTokenParams: { resource: cfg.apiResource },
    automaticSilentRenew: false,
  };
}

export const userManager: UserManager | null =
  config.authMode === "oidc"
    ? new UserManager({
        ...oidcSettings(config, window.location.origin),
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
      })
    : null;

export async function currentOidcSession(): Promise<Session | null> {
  const user = await userManager?.getUser();
  if (!user || user.expired || !user.expires_at) return null;
  return { accessToken: user.access_token, expiresAt: user.expires_at * 1000 };
}
