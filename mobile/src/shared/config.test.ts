import { parseConfig } from "./config";

describe("parseConfig", () => {
  it("geliştirmede eksik değerler için yerel varsayılanları kullanır", () => {
    expect(parseConfig({}, true)).toMatchObject({ authMode: "dev", apiUrl: "http://localhost:8080" });
  });

  it("release build'de eksik yapılandırma sessizce dev moduna düşmez, açılışta hata verir", () => {
    expect(() => parseConfig({}, false)).toThrow();
  });

  it("release build'de oidc yapılandırması eksiksiz verilirse kabul edilir", () => {
    const config = parseConfig(
      {
        EXPO_PUBLIC_AUTH_MODE: "oidc",
        EXPO_PUBLIC_API_URL: "https://api.example",
        EXPO_PUBLIC_ASSET_BASE_URL: "https://media.example",
        EXPO_PUBLIC_OIDC_ISSUER: "https://auth.example/oidc",
        EXPO_PUBLIC_OIDC_CLIENT_ID: "abc",
        EXPO_PUBLIC_API_RESOURCE: "https://api.example",
      },
      false,
    );
    expect(config.authMode).toBe("oidc");
  });
});
