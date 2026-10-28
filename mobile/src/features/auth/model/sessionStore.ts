import * as SecureStore from "expo-secure-store";

import { type Session, sessionSchema } from "./session";

const KEY = "soruhavuzu.session";

/** Token'lar yalnızca güvenli depoda tutulur; AsyncStorage'a yazılmaz (kurallar.md §6). */
export const sessionStore = {
  async load(): Promise<Session | null> {
    const raw = await SecureStore.getItemAsync(KEY);
    if (!raw) return null;
    try {
      const parsed = sessionSchema.safeParse(JSON.parse(raw));
      return parsed.success ? parsed.data : null;
    } catch {
      return null;
    }
  },
  save(session: Session): Promise<void> {
    return SecureStore.setItemAsync(KEY, JSON.stringify(session));
  },
  clear(): Promise<void> {
    return SecureStore.deleteItemAsync(KEY);
  },
};
