import { useQuery } from "@tanstack/react-query";

import { api, ApiProblemError } from "@/shared/api";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: async () => {
      const { data, error } = await api.GET("/v1/me");
      if (error) throw new ApiProblemError(error);
      return data;
    },
  });
}
