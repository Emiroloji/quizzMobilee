import { t } from "@/shared/i18n";

import { useMe } from "../api/useMe";

const MODERATION_ROLES = new Set(["MODERATOR", "ADMIN"]);

export function MeSummary() {
  const me = useMe();
  if (me.isPending) return <p className="muted">{t("common.loading")}</p>;
  if (me.isError) return <p className="error">{t("common.error")}</p>;
  return (
    <section className="stack">
      <p>{t("profile.signedInAs", { handle: me.data.handle, role: me.data.role })}</p>
      {MODERATION_ROLES.has(me.data.role) ? null : <p className="muted">{t("profile.notModerator")}</p>}
    </section>
  );
}
