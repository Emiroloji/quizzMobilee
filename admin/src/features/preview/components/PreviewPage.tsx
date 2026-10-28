import { type ContentIssue, type DocumentTheme, renderDocument, validateContent } from "@soruhavuzu/content-schema";
import { useMemo, useState } from "react";

import { config } from "@/shared/config";
import { t } from "@/shared/i18n";

import { SAMPLE_CONTENT } from "../model/sample";

type Outcome = { kind: "document"; html: string } | { kind: "issues"; issues: ContentIssue[] } | { kind: "invalidJson" };

const assetUrl = (assetId: string) => `${config.assetBaseUrl}/public/${assetId}.webp`;

// Önizleme panelin renk şemasını izler (styles.css ile aynı değerler)
const prefersDark = () => window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
const theme = (): DocumentTheme =>
  prefersDark()
    ? { text: "#eef0f3", background: "#0f1115", fontSizePx: 17, paddingPx: 12 }
    : { text: "#1b1d21", background: "#ffffff", fontSizePx: 17, paddingPx: 12 };

function evaluate(source: string): Outcome {
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch {
    return { kind: "invalidJson" };
  }
  const result = validateContent(parsed);
  return result.ok ? { kind: "document", html: renderDocument(result.value, { assetUrl, theme: theme() }) } : { kind: "issues", issues: result.issues };
}

/**
 * Sunucunun kullandığı aynı şema ve aynı render ile önizleme. Belge betiksiz, sandbox'lı iframe'de
 * gösterilir (mimari.md §19); mobil QuestionBody ile birebir aynı HTML'dir (kurallar.md A12).
 */
export function PreviewPage() {
  const [source, setSource] = useState(() => JSON.stringify(SAMPLE_CONTENT, null, 2));
  const outcome = useMemo(() => evaluate(source), [source]);

  return (
    <section className="stack">
      <h1>{t("preview.title")}</h1>
      <div className="split">
        <label className="field">
          {t("preview.inputLabel")}
          <textarea value={source} spellCheck={false} onChange={(event) => setSource(event.target.value)} />
        </label>
        <div>
          {outcome.kind === "document" ? (
            <iframe className="preview-frame" title={t("preview.frameTitle")} sandbox="" srcDoc={outcome.html} />
          ) : outcome.kind === "invalidJson" ? (
            <p className="error">{t("preview.invalidJson")}</p>
          ) : (
            <div className="stack">
              <h2>{t("preview.issuesTitle")}</h2>
              <ul>
                {outcome.issues.map((issue) => (
                  <li key={`${issue.path}:${issue.code}`}>
                    <code>{issue.code}</code> <span className="muted">{issue.path}</span> {issue.message}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
