import { useState } from "react";
import { Navigate } from "react-router";

import { config } from "@/shared/config";
import { t } from "@/shared/i18n";

import { useAuth } from "../hooks/AuthProvider";

export function LoginPage() {
  const { state, signInDev, signInWithCognito } = useAuth();
  const [subject, setSubject] = useState("moderator-1");
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  if (state.status === "signedIn") return <Navigate to="/" replace />;

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setFailed(false);
    try {
      await action();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="layout stack">
      <h1>{t("auth.title")}</h1>
      {config.authMode === "cognito" ? (
        <button className="button" disabled={busy} onClick={() => void run(signInWithCognito)}>
          {t("auth.cognito")}
        </button>
      ) : (
        <form
          className="stack"
          onSubmit={(event) => {
            event.preventDefault();
            void run(() => signInDev(subject.trim()));
          }}
        >
          <label className="field">
            {t("auth.devSubjectLabel")}
            <input value={subject} onChange={(event) => setSubject(event.target.value)} />
          </label>
          <button className="button" type="submit" disabled={busy || subject.trim().length === 0}>
            {t("auth.devSignIn")}
          </button>
        </form>
      )}
      {failed ? <p className="error">{t("auth.failed")}</p> : null}
    </main>
  );
}
