import { parseConfig } from "./config";

describe("parseConfig", () => {
  it("geliştirmede eksik değerler için yerel varsayılanları kullanır", () => {
    expect(parseConfig({}, true)).toMatchObject({ authMode: "dev", apiUrl: "http://localhost:8080" });
  });

  it("release build'de eksik yapılandırma sessizce dev moduna düşmez, açılışta hata verir", () => {
    expect(() => parseConfig({}, false)).toThrow();
  });

  it("release build'de cognito yapılandırması eksiksiz verilirse kabul edilir", () => {
    const config = parseConfig(
      {
        EXPO_PUBLIC_AUTH_MODE: "cognito",
        EXPO_PUBLIC_API_URL: "https://api.example",
        EXPO_PUBLIC_ASSET_BASE_URL: "https://cdn.example",
        EXPO_PUBLIC_COGNITO_DOMAIN: "https://auth.example",
        EXPO_PUBLIC_COGNITO_CLIENT_ID: "abc",
      },
      false,
    );
    expect(config.authMode).toBe("cognito");
  });
});
