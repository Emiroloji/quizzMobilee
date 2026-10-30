import { describe, expect, it } from "vitest";

import { parseConfig } from "./config";

describe("parseConfig", () => {
  it("geliştirmede eksik değerler için yerel varsayılanları kullanır", () => {
    expect(parseConfig({}, true)).toMatchObject({ authMode: "dev", apiUrl: "http://localhost:8080" });
  });

  it("production build'de eksik yapılandırma sessizce dev moduna düşmez, açılışta hata verir", () => {
    expect(() => parseConfig({}, false)).toThrow();
  });

  it("production build'de oidc yapılandırması eksiksiz verilirse kabul edilir", () => {
    const config = parseConfig(
      {
        VITE_AUTH_MODE: "oidc",
        VITE_API_URL: "https://api.example",
        VITE_ASSET_BASE_URL: "https://media.example",
        VITE_OIDC_ISSUER: "https://auth.example/oidc",
        VITE_OIDC_CLIENT_ID: "abc",
        VITE_API_RESOURCE: "https://api.example",
      },
      false,
    );
    expect(config.authMode).toBe("oidc");
  });
});
