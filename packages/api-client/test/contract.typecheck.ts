// Derleme zamanı sözleşme testi: `pnpm typecheck` bu dosyayı derler; çalıştırılmaz.
import { createApiClient, type Problem, type Schemas } from "../src";

const api = createApiClient({ baseUrl: "http://localhost:8080", getToken: () => null });

export async function contract(): Promise<void> {
  const { data, error } = await api.GET("/v1/me");
  if (data) {
    const me: Schemas["MeResponse"] = data;
    const id: string = me.id;
    const role: "USER" | "EDITOR" | "MODERATOR" | "ADMIN" = me.role;
    const birthYear: number | null | undefined = me.birthYear;
    void [id, role, birthYear];
  }
  if (error) {
    const problem: Problem = error;
    const code: string = problem.code;
    void code;
  }
  // @ts-expect-error — sözleşmede olmayan yol derlenmez
  await api.GET("/v1/olmayan");
}
