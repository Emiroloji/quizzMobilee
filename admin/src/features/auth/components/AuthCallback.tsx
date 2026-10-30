import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";

import { t } from "@/shared/i18n";

import { useAuth } from "../hooks/AuthProvider";

/** Logto'dan dönüşte yetkilendirme kodunu token'a çevirir. */
export function AuthCallback() {
  const { completeOidcSignIn } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void completeOidcSignIn().finally(() => void navigate("/", { replace: true }));
  }, [completeOidcSignIn, navigate]);

  return <p className="muted">{t("common.loading")}</p>;
}
