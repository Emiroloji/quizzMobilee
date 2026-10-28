import { api, setTokenProvider } from "./client";

describe("api istemcisi", () => {
  afterEach(() => jest.restoreAllMocks());

  it("bağlanan token sağlayıcısından gelen token'ı Bearer olarak ekler", async () => {
    const fetchSpy = jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(JSON.stringify({ id: "1" }), { status: 200, headers: { "Content-Type": "application/json" } }));
    setTokenProvider(() => Promise.resolve("abc.def.ghi"));

    await api.GET("/v1/me");

    const request = fetchSpy.mock.calls[0]?.[0];
    expect(request).toBeInstanceOf(Request);
    if (!(request instanceof Request)) return;
    expect(request.headers.get("Authorization")).toBe("Bearer abc.def.ghi");
    expect(request.url).toBe("http://localhost:8080/v1/me");
  });
});
