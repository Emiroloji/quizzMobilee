import { describe, expect, it } from "vitest";

import { parseConfig } from "./config";

describe("parseConfig", () => {
  it("geliştirmede eksik değerler için yerel varsayılanları kullanır", () => {
    expect(parseConfig({}, true)).toMatchObject({ authMode: "dev", apiUrl: "http://localhost:8080" });
  });

  it("production build'de eksik yapılandırma sessizce dev moduna düşmez, açılışta hata verir", () => {
    expect(() => parseConfig({}, false)).toThrow();
  });
});
