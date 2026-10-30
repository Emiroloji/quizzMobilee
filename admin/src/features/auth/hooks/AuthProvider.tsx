import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import { queryClient, setTokenProvider } from "@/shared/api";

import { requestDevSession } from "../api/devToken";
import { currentOidcSession, userManager } from "../api/oidc";
import { devSessionStore, isExpired, type Session } from "../model/session";

export type AuthState = { status: "loading" } | { status: "signedOut" } | { status: "signedIn"; session: Session };

interface AuthContextValue {
  state: AuthState;
  signInDev: (subject: string) => Promise<void>;
  signInWithOidc: () => Promise<void>;
  completeOidcSignIn: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const sessionRef = useRef<Session | null>(null);

  const apply = useCallback((session: Session | null) => {
    sessionRef.current = session;
    if (!session) queryClient.clear();
    setState(session ? { status: "signedIn", session } : { status: "signedOut" });
  }, []);

  useEffect(() => {
    setTokenProvider(() => {
      const current = sessionRef.current;
      return Promise.resolve(current && !isExpired(current, Date.now()) ? current.accessToken : null);
    });
    void (userManager ? currentOidcSession() : Promise.resolve(devSessionStore.load())).then(apply);
  }, [apply]);

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      signInDev: async (subject) => {
        const session = await requestDevSession(subject);
        devSessionStore.save(session);
        apply(session);
      },
      signInWithOidc: async () => {
        await userManager?.signinRedirect();
      },
      completeOidcSignIn: async () => {
        await userManager?.signinRedirectCallback();
        apply(await currentOidcSession());
      },
      signOut: async () => {
        devSessionStore.clear();
        await userManager?.removeUser();
        apply(null);
      },
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
