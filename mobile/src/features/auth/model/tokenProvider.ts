import { isExpired, type Session } from "./session";

export interface TokenProviderDeps {
  getSession: () => Promise<Session | null>;
  refresh: (session: Session) => Promise<Session | null>;
  onRefreshed: (session: Session) => Promise<void>;
  onSignedOut: () => Promise<void>;
  now: () => number;
}

/** OAuth hata kodlarından yalnızca bunlar oturumun gerçekten geçersiz olduğunu gösterir. */
const AUTH_FAILURE_CODES = new Set(["invalid_grant", "invalid_client", "unauthorized_client"]);

function isAuthFailure(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    typeof error.code === "string" &&
    AUTH_FAILURE_CODES.has(error.code)
  );
}

/**
 * API istemcisinin token kaynağı:
 * - Eşzamanlı istekler tek bir yenilemeyi paylaşır (refresh token rotasyonunda yarışı önler).
 * - Ağ hatasında oturum korunur (offline kullanım); yalnızca kimlik hatasında oturum kapanır.
 */
export function createTokenProvider(deps: TokenProviderDeps): () => Promise<string | null> {
  let inFlight: Promise<string | null> | null = null;

  const renew = async (session: Session): Promise<string | null> => {
    try {
      const next = await deps.refresh(session);
      if (!next) {
        await deps.onSignedOut();
        return null;
      }
      await deps.onRefreshed(next);
      return next.accessToken;
    } catch (error) {
      if (isAuthFailure(error)) await deps.onSignedOut();
      return null;
    }
  };

  return async () => {
    const session = await deps.getSession();
    if (!session) return null;
    if (!isExpired(session, deps.now())) return session.accessToken;
    inFlight ??= renew(session).finally(() => {
      inFlight = null;
    });
    return inFlight;
  };
}
