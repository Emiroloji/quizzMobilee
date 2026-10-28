import { View } from "react-native";

import { t } from "@/shared/i18n";
import { AppText, Button, spacing } from "@/shared/ui";

import { useMe } from "../api/useMe";

export function MeCard() {
  const me = useMe();
  if (me.isPending) return <AppText>{t("common.loading")}</AppText>;
  if (me.isError) {
    return (
      <View style={{ gap: spacing.sm }}>
        <AppText variant="error">{t("common.error")}</AppText>
        <Button label={t("common.retry")} variant="secondary" onPress={() => void me.refetch()} />
      </View>
    );
  }
  const memberSince = new Date(me.data.createdAt).toLocaleDateString("tr-TR");
  return (
    <View style={{ gap: spacing.xs }}>
      <AppText variant="subtitle">{t("profile.greeting", { handle: me.data.handle })}</AppText>
      <AppText variant="small">{t("profile.role", { role: me.data.role })}</AppText>
      <AppText variant="small">{t("profile.memberSince", { date: memberSince })}</AppText>
    </View>
  );
}
