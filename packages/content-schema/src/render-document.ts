import { KATEX_CSS } from "./generated/katex-css";
import { renderHtml, type RenderOptions } from "./render-html";
import type { Content } from "./schema";

export interface DocumentTheme {
  text: string;
  background: string;
  fontSizePx: number;
  /** Gövde iç boşluğu; mobil kenarla hizalı (0), admin önizlemesi nefes payı ister. */
  paddingPx?: number;
}

export interface DocumentOptions extends RenderOptions {
  theme?: DocumentTheme;
}

const DEFAULT_THEME: DocumentTheme = { text: "#1b1d21", background: "transparent", fontSizePx: 17 };
const COLOR = /^(#[0-9a-fA-F]{3,8}|transparent)$/;

// Betik yok, dış kaynak yok: yalnızca gömülü stil, gömülü font ve http(s) görsel.
// WebView'ın native olarak enjekte ettiği yükseklik ölçümü CSP'ye tabi değildir.
const CSP = "default-src 'none'; style-src 'unsafe-inline'; font-src data:; img-src https: http:";

function css(theme: DocumentTheme): string {
  if (!COLOR.test(theme.text) || !COLOR.test(theme.background)) throw new Error("Geçersiz tema rengi");
  const size = Math.min(Math.max(Math.round(theme.fontSizePx), 10), 40);
  const padding = Math.min(Math.max(Math.round(theme.paddingPx ?? 0), 0), 48);
  return (
    `html,body{margin:0;padding:0;background:${theme.background};color:${theme.text};}` +
    `body{padding:${padding}px;font:${size}px/1.5 -apple-system,system-ui,"Segoe UI",Roboto,sans-serif;-webkit-text-size-adjust:100%;}` +
    `.sh-content p{margin:0 0 .6em}.sh-content p:last-child{margin-bottom:0}` +
    `.sh-math-block{overflow-x:auto;overflow-y:hidden;margin:.4em 0}` +
    `.sh-image{margin:.6em 0}.sh-image img{max-width:100%;height:auto;display:block}`
  );
}

/**
 * Tek başına gösterilebilen, offline HTML belgesi: mobil QuestionBody (WebView) ve admin önizlemesi
 * (sandbox'lı iframe) birebir aynı belgeyi gösterir (kurallar.md A12, mimari.md §19).
 */
export function renderDocument(content: Content, options: DocumentOptions): string {
  const theme = options.theme ?? DEFAULT_THEME;
  return (
    `<!doctype html><html lang="tr"><head><meta charset="utf-8">` +
    `<meta http-equiv="Content-Security-Policy" content="${CSP}">` +
    `<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">` +
    `<style>${KATEX_CSS}${css(theme)}</style></head><body>${renderHtml(content, options)}</body></html>`
  );
}
