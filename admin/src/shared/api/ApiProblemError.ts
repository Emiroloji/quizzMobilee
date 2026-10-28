import type { Problem } from "@soruhavuzu/api-client";

/** Sunucunun RFC 9457 hata gövdesini taşır; `code` ile dallanılır (ör. ACCOUNT_INACTIVE). */
export class ApiProblemError extends Error {
  constructor(readonly problem: Problem) {
    super(`${problem.code}: ${problem.detail ?? problem.title}`);
    this.name = "ApiProblemError";
  }
}
