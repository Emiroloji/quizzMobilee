import { z } from "zod";

export const sessionSchema = z.object({
  accessToken: z.string().min(1),
  /** Epoch milisaniye */
  expiresAt: z.number(),
  refreshToken: z.string().min(1).optional(),
});

export type Session = z.infer<typeof sessionSchema>;

/** Süre dolmadan bu kadar önce yenilenir (saat kayması ve ağ gecikmesi payı). */
const EXPIRY_SKEW_MS = 60_000;

export function isExpired(session: Session, nowMs: number): boolean {
  return nowMs >= session.expiresAt - EXPIRY_SKEW_MS;
}
