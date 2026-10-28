import { useAuthRequest } from "expo-auth-session";
import { useState } from "react";

import { Button } from "@/shared/ui";

import { discovery, exchangeCode, type IdentityProvider, redirectUri, SCOPES } from "../api/cognito";
import { useAuth } from "../hooks/AuthProvider";

interface Props {
  domain: string;
  clientId: string;
  provider: IdentityProvider;
  label: string;
  onError: () => void;
}

/** Cognito managed login (PKCE). `identity_provider` ile Apple/Google ekranına doğrudan gidilir. */
export function CognitoButton({ domain, clientId, provider, label, onError }: Props) {
  const { completeSignIn } = useAuth();
  const [busy, setBusy] = useState(false);
  const [request, , promptAsync] = useAuthRequest(
    {
      clientId,
      redirectUri,
      scopes: SCOPES,
      usePKCE: true,
      extraParams: provider === "COGNITO" ? {} : { identity_provider: provider },
    },
    discovery(domain),
  );

  const onPress = async () => {
    if (!request?.codeVerifier) return;
    setBusy(true);
    try {
      const result = await promptAsync();
      if (result.type === "success") await completeSignIn(await exchangeCode(result, request.codeVerifier));
    } catch {
      onError();
    } finally {
      setBusy(false);
    }
  };

  return <Button label={label} variant="secondary" onPress={() => void onPress()} loading={busy} disabled={!request} />;
}
