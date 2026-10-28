import * as WebBrowser from "expo-web-browser";
import { useState } from "react";
import { View } from "react-native";

import { t } from "@/shared/i18n";
import { AppText, Button, spacing, TextField } from "@/shared/ui";

import { cognitoSettings } from "../api/cognito";
import { useAuth } from "../hooks/AuthProvider";
import { CognitoButton } from "./CognitoButton";

WebBrowser.maybeCompleteAuthSession();

export function LoginForm() {
  const { signInDev } = useAuth();
  const [subject, setSubject] = useState("gelistirici-1");
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const cognito = cognitoSettings();

  const onDevSignIn = async () => {
    setBusy(true);
    setFailed(false);
    try {
      await signInDev(subject.trim());
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={{ gap: spacing.md }}>
      <AppText variant="title">{t("auth.title")}</AppText>
      <AppText variant="small">{t("auth.subtitle")}</AppText>
      {cognito ? (
        <>
          <CognitoButton {...cognito} provider="SignInWithApple" label={t("auth.withApple")} onError={() => setFailed(true)} />
          <CognitoButton {...cognito} provider="Google" label={t("auth.withGoogle")} onError={() => setFailed(true)} />
          <CognitoButton {...cognito} provider="COGNITO" label={t("auth.withEmail")} onError={() => setFailed(true)} />
        </>
      ) : (
        <>
          <TextField label={t("auth.devSubjectLabel")} hint={t("auth.devSubjectHint")} value={subject} onChangeText={setSubject} />
          <Button label={t("auth.devSignIn")} onPress={() => void onDevSignIn()} loading={busy} disabled={subject.trim().length === 0} />
        </>
      )}
      {failed ? <AppText variant="error">{t("auth.failed")}</AppText> : null}
    </View>
  );
}
