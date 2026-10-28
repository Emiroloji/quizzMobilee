import { t } from ".";

describe("t", () => {
  it("anahtarın Türkçe metnini döner", () => {
    expect(t("auth.title")).toBe("Giriş yap");
  });

  it("yer tutucuları doldurur, bilinmeyenleri olduğu gibi bırakır", () => {
    expect(t("profile.greeting", { handle: "ayse" })).toBe("Merhaba, ayse");
    expect(t("profile.greeting")).toBe("Merhaba, {handle}");
  });
});
