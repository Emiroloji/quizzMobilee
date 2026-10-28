import type { ReactNode } from "react";
import { Navigate } from "react-router";

import { t } from "@/shared/i18n";

import { useAuth } from "../hooks/AuthProvider";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  if (state.status === "loading") return <p className="muted">{t("common.loading")}</p>;
  if (state.status === "signedOut") return <Navigate to="/login" replace />;
  return children;
}
