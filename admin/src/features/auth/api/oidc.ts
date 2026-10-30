import { UserManager, WebStorageStateStore } from "oidc-client-ts";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

/** Logto (Authorization Code + PKCE). Token API resource'u için istenir; yalnızca sekme ömrü boyunca tutulur. */
export const userManager: UserManager | null =
  config.authMode === "oidc"
    ? new UserManager({
        authority: config.oidcIssuer,
        client_id: config.oidcClientId,
        redirect_uri: `${window.location.origin}/auth/callback`,
        post_logout_redirect_uri: `${window.location.origin}/login`,
        response_type: "code",
        scope: "openid offline_access",
        extraQueryParams: { resource: config.apiResource, prompt: "consent" },
        extraTokenParams: { resource: config.apiResource },
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
        automaticSilentRenew: false,
      })
    : null;

export async function currentOidcSession(): Promise<Session | null> {
  const user = await userManager?.getUser();
  if (!user || user.expired || !user.expires_at) return null;
  return { accessToken: user.access_token, expiresAt: user.expires_at * 1000 };
}
