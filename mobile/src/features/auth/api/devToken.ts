import { z } from "zod";

import { config } from "@/shared/config";

import type { Session } from "../model/session";

const responseSchema = z.object({ accessToken: z.string(), expiresAt: z.string() });

/**
 * Yalnızca `dev` modu: backend local profilindeki sahte JWT üreticisi (ADR 0018). Bu uç nokta bilerek
 * API sözleşmesinde (api-client) yoktur; bu yüzden kurallar.md §6'daki "elle fetch yok" kuralının tek istisnasıdır.
 */
export async function requestDevSession(subject: string): Promise<Session> {
  // eslint-disable-next-line no-restricted-globals -- dev token ucu sözleşme dışı (ADR 0018); yalnızca dev modda çağrılır
  const response = await fetch(`${config.apiUrl}/v1/dev/tokens`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subject }),
  });
  if (!response.ok) throw new Error(`Dev token alınamadı: ${response.status}`);
  const body = responseSchema.parse(await response.json());
  return { accessToken: body.accessToken, expiresAt: Date.parse(body.expiresAt) };
}
