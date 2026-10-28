import { QueryClient } from "@tanstack/react-query";

/** Sunucu durumu yalnızca TanStack Query'de tutulur; global store'a kopyalanmaz (kurallar.md §6). */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 2, staleTime: 30_000 },
  },
});
