import katex, { type KatexOptions } from "katex";

import allowlist from "../katex-allowlist.json";

export type LatexIssueCode =
  | "LATEX_FORBIDDEN_COMMAND"
  | "LATEX_UNKNOWN_COMMAND"
  | "LATEX_FORBIDDEN_ENVIRONMENT"
  | "LATEX_TOO_LONG"
  | "LATEX_PARSE_ERROR";

export interface LatexIssue {
  code: LatexIssueCode;
  message: string;
}

export const LATEX_MAX_SOURCE_LENGTH = allowlist.limits.maxSourceLength;

const COMMANDS = new Set(allowlist.commands);
const SYMBOLS = new Set(allowlist.symbols);
const ENVIRONMENTS = new Set(allowlist.environments);
const FORBIDDEN = new Set(allowlist.forbidden);

// \komut (harflerden oluşur) veya \tek-sembol (\{, \,, \\ …)
const COMMAND_PATTERN = /\\([a-zA-Z]+|[^a-zA-Z])/g;
const ENVIRONMENT_PATTERN = /\\(?:begin|end)\s*\{([^}]*)\}/g;

/** KaTeX her zaman güvensiz özellikler kapalı çalışır (mimari.md §7). */
export function katexOptions(displayMode: boolean, throwOnError: boolean): KatexOptions {
  return {
    displayMode,
    throwOnError,
    trust: false,
    strict: "ignore",
    maxSize: allowlist.limits.maxSize,
    maxExpand: allowlist.limits.maxExpand,
  };
}

/**
 * LaTeX kaynağını izin listesine göre denetler; boş dizi = geçerli.
 * Önce izin listesi, sonra KaTeX ayrıştırması: izinsiz bir komut asla ayrıştırıcıya ulaşmaz.
 */
export function findLatexIssues(source: string): LatexIssue[] {
  if (source.length > LATEX_MAX_SOURCE_LENGTH) {
    return [{ code: "LATEX_TOO_LONG", message: `LaTeX en fazla ${LATEX_MAX_SOURCE_LENGTH} karakter olabilir` }];
  }
  const issues = new Map<string, LatexIssue>();
  for (const match of source.matchAll(COMMAND_PATTERN)) {
    const name = match[1] ?? "";
    const isWord = /^[a-zA-Z]+$/.test(name);
    if (isWord && FORBIDDEN.has(name)) {
      issues.set(name, { code: "LATEX_FORBIDDEN_COMMAND", message: `\\${name} kullanılamaz` });
    } else if (isWord ? !COMMANDS.has(name) : !SYMBOLS.has(name)) {
      issues.set(name, { code: "LATEX_UNKNOWN_COMMAND", message: `\\${name} izin listesinde yok` });
    }
  }
  for (const match of source.matchAll(ENVIRONMENT_PATTERN)) {
    const env = (match[1] ?? "").trim();
    if (!ENVIRONMENTS.has(env)) {
      issues.set(`env:${env}`, { code: "LATEX_FORBIDDEN_ENVIRONMENT", message: `${env} ortamı kullanılamaz` });
    }
  }
  if (issues.size > 0) return [...issues.values()];
  try {
    katex.renderToString(source, katexOptions(false, true));
    return [];
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    return [{ code: "LATEX_PARSE_ERROR", message: `LaTeX ayrıştırılamadı: ${detail}` }];
  }
}
