import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import { queryClient, setTokenProvider } from "@/shared/api";

import { refreshSession } from "../api/cognito";
import { requestDevSession } from "../api/devToken";
import { isExpired, type Session } from "../model/session";
import { sessionStore } from "../model/sessionStore";

export type AuthState = { status: "loading" } | { status: "signedOut" } | { status: "signedIn"; session: Session };

interface AuthContextValue {
  state: AuthState;
  signInDev: (subject: string) => Promise<void>;
  completeSignIn: (session: Session) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const sessionRef = useRef<Session | null>(null);

  const apply = useCallback(async (session: Session | null) => {
    sessionRef.current = session;
    if (session) {
      await sessionStore.save(session);
      setState({ status: "signedIn", session });
    } else {
      await sessionStore.clear();
      queryClient.clear();
      setState({ status: "signedOut" });
    }
  }, []);

  useEffect(() => {
    // API istemcisi token'ı buradan alır; süresi dolmuşsa önce yenilemeyi dener, olmazsa oturumu kapatır
    setTokenProvider(async () => {
      const current = sessionRef.current;
      if (!current) return null;
      if (!isExpired(current, Date.now())) return current.accessToken;
      const refreshed = await refreshSession(current).catch(() => null);
      await apply(refreshed);
      return refreshed?.accessToken ?? null;
    });
    void sessionStore.load().then((stored) => {
      sessionRef.current = stored;
      setState(stored ? { status: "signedIn", session: stored } : { status: "signedOut" });
    });
  }, [apply]);

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      signInDev: async (subject) => apply(await requestDevSession(subject)),
      completeSignIn: (session) => apply(session),
      signOut: () => apply(null),
    }),
    [state, apply],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth, AuthProvider içinde kullanılmalı");
  return value;
}
