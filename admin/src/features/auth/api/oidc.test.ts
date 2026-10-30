import { describe, expect, it } from "vitest";

import { oidcSettings } from "./oidc";

const cfg = {
  authMode: "oidc",
  apiUrl: "https://api.example",
  assetBaseUrl: "https://media.example",
  oidcIssuer: "https://auth.example/oidc",
  oidcClientId: "spa",
  apiResource: "https://api.example",
} as const;

describe("admin OIDC ayarları", () => {
  it("sessiz yenileme olmadığı için refresh token (offline_access) ve consent istemez", () => {
    const settings = oidcSettings(cfg, "https://admin.example");
    expect(settings.scope).toBe("openid");
    expect(settings.extraQueryParams).toEqual({ resource: "https://api.example" });
    expect(settings.automaticSilentRenew).toBe(false);
  });

  it("çıkış sonrası giriş sayfasına döner", () => {
    expect(oidcSettings(cfg, "https://admin.example").post_logout_redirect_uri).toBe("https://admin.example/login");
  });
});
