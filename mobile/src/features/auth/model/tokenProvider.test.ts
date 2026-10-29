import type { Session } from "./session";
import { createTokenProvider } from "./tokenProvider";

const NOW = 1_000_000_000;
const expired: Session = { accessToken: "eski", expiresAt: NOW - 1, refreshToken: "r" };
const fresh: Session = { accessToken: "yeni", expiresAt: NOW + 3_600_000, refreshToken: "r" };

function setup(refresh: (session: Session) => Promise<Session | null>, initial: Session | null = expired) {
  let current = initial;
  const signedOut = jest.fn();
  const provider = createTokenProvider({
    getSession: () => Promise.resolve(current),
    refresh,
    onRefreshed: (session) => {
      current = session;
      return Promise.resolve();
    },
    onSignedOut: () => {
      current = null;
      signedOut();
      return Promise.resolve();
    },
    now: () => NOW,
  });
  return { provider, signedOut };
}

describe("createTokenProvider", () => {
  it("süresi dolmamış token'ı yenilemeden döner", async () => {
    const refresh = jest.fn<Promise<Session | null>, [Session]>();
    const { provider } = setup(refresh, fresh);
    await expect(provider()).resolves.toBe("yeni");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("eşzamanlı istekler tek bir yenileme paylaşır", async () => {
    const refresh = jest.fn<Promise<Session | null>, [Session]>().mockResolvedValue(fresh);
    const { provider } = setup(refresh);
    const tokens = await Promise.all([provider(), provider(), provider()]);
    expect(tokens).toEqual(["yeni", "yeni", "yeni"]);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("ağ hatasında oturumu kapatmaz", async () => {
    const { provider, signedOut } = setup(() => Promise.reject(new TypeError("Network request failed")));
    await expect(provider()).resolves.toBeNull();
    expect(signedOut).not.toHaveBeenCalled();
  });

  it("refresh token geçersizse (invalid_grant) oturumu kapatır", async () => {
    const { provider, signedOut } = setup(() => Promise.reject(Object.assign(new Error("x"), { code: "invalid_grant" })));
    await expect(provider()).resolves.toBeNull();
    expect(signedOut).toHaveBeenCalledTimes(1);
  });

  it("yenilenemeyen (refresh token'sız) süresi dolmuş oturumu kapatır", async () => {
    const { provider, signedOut } = setup(() => Promise.resolve(null));
    await expect(provider()).resolves.toBeNull();
    expect(signedOut).toHaveBeenCalledTimes(1);
  });
});
