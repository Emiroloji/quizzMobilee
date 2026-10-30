import { useAuthRequest } from "expo-auth-session";
import { useState } from "react";

import { Button } from "@/shared/ui";

import { discovery, exchangeCode, redirectUri, SCOPES } from "../api/oidc";
import { useAuth } from "../hooks/AuthProvider";

interface Props {
  issuer: string;
  clientId: string;
  resource: string;
  label: string;
  onError: () => void;
}

/** Logto'nun giriş/kayıt ekranı (PKCE); token API resource'u için istenir (ADR 0024). */
export function OidcSignInButton({ issuer, clientId, resource, label, onError }: Props) {
  const { completeSignIn } = useAuth();
  const [busy, setBusy] = useState(false);
  const [request, , promptAsync] = useAuthRequest(
    { clientId, redirectUri, scopes: SCOPES, usePKCE: true, extraParams: { resource, prompt: "consent" } },
    discovery(issuer),
  );

  const onPress = async () => {
    if (!request?.codeVerifier) return;
    setBusy(true);
    try {
      const result = await promptAsync();
      if (result.type === "success") await completeSignIn(await exchangeCode(result, request.codeVerifier));
      // Vazgeçme (cancel/dismiss) hata değildir; Logto'nun döndürdüğü hata ise kullanıcıya bildirilir
      else if (result.type === "error") onError();
    } catch {
      onError();
    } finally {
      setBusy(false);
    }
  };

  return <Button label={label} onPress={() => void onPress()} loading={busy} disabled={!request} />;
}
