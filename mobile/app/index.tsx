import { Link, Redirect } from "expo-router";

import { useAuth } from "@/features/auth";
import { MeCard } from "@/features/profile";
import { t } from "@/shared/i18n";
import { AppText, Button, Screen } from "@/shared/ui";

export default function HomeRoute() {
  const { state, signOut } = useAuth();
  if (state.status === "loading") {
    return (
      <Screen>
        <AppText>{t("common.loading")}</AppText>
      </Screen>
    );
  }
  if (state.status === "signedOut") return <Redirect href="/login" />;
  return (
    <Screen>
      <AppText variant="title">{t("common.appName")}</AppText>
      <MeCard />
      {__DEV__ ? (
        <Link href="/dev/question-body" asChild>
          <Button label={t("dev.sampleQuestion")} variant="secondary" onPress={() => undefined} />
        </Link>
      ) : null}
      <Button label={t("auth.signOut")} variant="secondary" onPress={() => void signOut()} />
    </Screen>
  );
}
