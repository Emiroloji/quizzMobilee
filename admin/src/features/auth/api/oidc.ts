import { UserManager, WebStorageStateStore } from "oidc-client-ts";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

/** Cognito managed login (Authorization Code + PKCE). Token yalnızca sekme ömrü boyunca tutulur. */
export const userManager: UserManager | null =
  config.authMode === "cognito"
    ? new UserManager({
        authority: config.cognitoAuthority,
        client_id: config.cognitoClientId,
        redirect_uri: `${window.location.origin}/auth/callback`,
        response_type: "code",
        scope: "openid email",
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
        automaticSilentRenew: false,
      })
    : null;

export async function currentOidcSession(): Promise<Session | null> {
  const user = await userManager?.getUser();
  if (!user || user.expired || !user.expires_at) return null;
  return { accessToken: user.access_token, expiresAt: user.expires_at * 1000 };
}
