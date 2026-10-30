import { TokenResponse } from "expo-auth-session";

import { discovery, toSession } from "./oidc";

// Jest ortamında expo-constants manifest'i yok; makeRedirectUri şema çözemez.
jest.mock("expo-auth-session", () => ({
  ...jest.requireActual<object>("expo-auth-session"),
  makeRedirectUri: () => "soruhavuzu://auth/callback",
}));

describe("Logto OIDC", () => {
  it("Logto uç noktalarını issuer'dan türetir", () => {
    expect(discovery("https://auth.example/oidc")).toEqual({
      authorizationEndpoint: "https://auth.example/oidc/auth",
      tokenEndpoint: "https://auth.example/oidc/token",
      revocationEndpoint: "https://auth.example/oidc/token/revocation",
      endSessionEndpoint: "https://auth.example/oidc/session/end",
    });
  });

  it("token yanıtını oturuma çevirir, yeni refresh token yoksa öncekini korur", () => {
    const token = new TokenResponse({ accessToken: "a", expiresIn: 3600, issuedAt: 1_000 });
    expect(toSession(token, "eski-refresh")).toEqual({
      accessToken: "a",
      expiresAt: 1_000 * 1000 + 3600 * 1000,
      refreshToken: "eski-refresh",
    });
  });
});
