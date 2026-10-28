import { useQuery } from "@tanstack/react-query";

import { api, ApiProblemError } from "@/shared/api";

export const meQueryKey = ["me"] as const;

/** GET /v1/me; ilk istekte kullanıcı backend'de otomatik oluşturulur (ADR 0018). */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: async () => {
      const { data, error } = await api.GET("/v1/me");
      if (error) throw new ApiProblemError(error);
      return data;
    },
  });
}
