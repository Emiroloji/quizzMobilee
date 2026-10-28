export interface Session {
  accessToken: string;
  /** Epoch milisaniye */
  expiresAt: number;
}

const KEY = "soruhavuzu.admin.session";

export const isExpired = (session: Session, nowMs: number): boolean => nowMs >= session.expiresAt - 60_000;

/** Dev oturumu yalnızca sekme ömrü boyunca tutulur; localStorage kullanılmaz. */
export const devSessionStore = {
  load(): Session | null {
    const raw = window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    try {
      const parsed: unknown = JSON.parse(raw);
      return typeof parsed === "object" && parsed !== null && "accessToken" in parsed && "expiresAt" in parsed
        ? { accessToken: String(parsed.accessToken), expiresAt: Number(parsed.expiresAt) }
        : null;
    } catch {
      return null;
    }
  },
  save(session: Session): void {
    window.sessionStorage.setItem(KEY, JSON.stringify(session));
  },
  clear(): void {
    window.sessionStorage.removeItem(KEY);
  },
};
