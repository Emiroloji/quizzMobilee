import { Link, Outlet } from "react-router";

import { RequireAuth, useAuth } from "@/features/auth";
import { t } from "@/shared/i18n";

export function Layout() {
  const { signOut } = useAuth();
  return (
    <RequireAuth>
      <div className="layout">
        <nav className="nav">
          <strong>{t("common.appName")}</strong>
          <Link to="/">{t("nav.home")}</Link>
          <Link to="/preview">{t("nav.preview")}</Link>
          <span className="spacer" />
          <button className="button secondary" onClick={() => void signOut()}>
            {t("auth.signOut")}
          </button>
        </nav>
        <Outlet />
      </div>
    </RequireAuth>
  );
}
