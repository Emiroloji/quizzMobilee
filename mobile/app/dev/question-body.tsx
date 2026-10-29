import { Redirect } from "expo-router";

import { t } from "@/shared/i18n";
import { QuestionBody, SAMPLE_QUESTION } from "@/shared/math";
import { AppText, Screen } from "@/shared/ui";

/** Faz 0 kabul kriteri: örnek soru iOS ve Android'de offline doğru render edilir. Yalnızca geliştirme build'inde. */
export default function SampleQuestionRoute() {
  if (!__DEV__) return <Redirect href="/" />;
  return (
    <Screen>
      <AppText variant="subtitle">{t("dev.sampleQuestion")}</AppText>
      <QuestionBody content={SAMPLE_QUESTION} />
    </Screen>
  );
}
