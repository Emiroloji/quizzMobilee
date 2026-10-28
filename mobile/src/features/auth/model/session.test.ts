import { isExpired } from "./session";

describe("isExpired", () => {
  const session = { accessToken: "x", expiresAt: 1_000_000 };

  it("süre dolmadan bir dakika önce dolmuş sayılır (yenileme payı)", () => {
    expect(isExpired(session, 1_000_000 - 120_000)).toBe(false);
    expect(isExpired(session, 1_000_000 - 30_000)).toBe(true);
  });
});
