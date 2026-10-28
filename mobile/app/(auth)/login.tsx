import { Redirect } from "expo-router";

import { LoginForm, useAuth } from "@/features/auth";
import { Screen } from "@/shared/ui";

export default function LoginRoute() {
  const { state } = useAuth();
  if (state.status === "signedIn") return <Redirect href="/" />;
  return (
    <Screen>
      <LoginForm />
    </Screen>
  );
}
