import createClient, { type Middleware } from "openapi-fetch";

import type { components, paths } from "./schema";

export type { components, paths };

/** Sunucunun döndürdüğü tüm şemalar; ör. `Schemas["MeResponse"]`. */
export type Schemas = components["schemas"];

/** RFC 9457 hata gövdesi; `code` makine okunur hata kodudur (ör. UNAUTHORIZED). */
export type Problem = Schemas["Problem"];

export interface ApiClientOptions {
  baseUrl: string;
  /** Her istekte çağrılır; token yoksa null döner ve istek kimliksiz gider. */
  getToken: () => Promise<string | null> | string | null;
  fetch?: typeof globalThis.fetch;
}

/**
 * Tipli API istemcisi. Sunucu çağrıları yalnızca bu istemciyle yapılır (kurallar.md §6).
 * Yol, gövde ve yanıt tipleri OpenAPI belgesinden gelir.
 */
export function createApiClient(options: ApiClientOptions) {
  const client = createClient<paths>({
    baseUrl: options.baseUrl,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
  const auth: Middleware = {
    async onRequest({ request }) {
      const token = await options.getToken();
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
      return request;
    },
  };
  client.use(auth);
  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;
