import { type ApiClient, createApiClient } from "@soruhavuzu/api-client";

import { config } from "../config";

type TokenProvider = () => Promise<string | null>;

let tokenProvider: TokenProvider = () => Promise.resolve(null);

/** Oturum yönetimi (auth feature) token kaynağını buraya bağlar. */
export function setTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

/** Sunucu çağrıları yalnızca bu istemciyle yapılır (kurallar.md §6). */
export const api: ApiClient = createApiClient({
  baseUrl: config.apiUrl,
  getToken: () => tokenProvider(),
  // fetch çağrı anında çözülür; test ve polyfill'ler istemci oluşturulduktan sonra değiştirebilir
  fetch: (request) => globalThis.fetch(request),
});
