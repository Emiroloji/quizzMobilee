import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import { queryClient, setTokenProvider } from "@/shared/api";

import { refreshSession } from "../api/oidc";
import { requestDevSession } from "../api/devToken";
import type { Session } from "../model/session";
import { sessionStore } from "../model/sessionStore";
import { createTokenProvider } from "../model/tokenProvider";

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
    // İlk istekler kalıcı oturum yüklenmeden token istemesin diye sağlayıcı yüklemeyi bekler
    const loading = sessionStore.load().then((stored) => {
      sessionRef.current = stored;
      setState(stored ? { status: "signedIn", session: stored } : { status: "signedOut" });
    });
    setTokenProvider(
      createTokenProvider({
        getSession: async () => {
          await loading;
          return sessionRef.current;
        },
        refresh: refreshSession,
        onRefreshed: (session) => apply(session),
        onSignedOut: () => apply(null),
        now: () => Date.now(),
      }),
    );
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
