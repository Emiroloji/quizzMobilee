import katex from "katex";

import { katexOptions } from "./latex";
import type { Block, Content, Inline } from "./schema";

export interface RenderOptions {
  /** Asset kimliğinden CloudFront URL'i üretir (kurallar.md §6). Yalnızca http(s) kabul edilir. */
  assetUrl: (assetId: string) => string;
}

const HTML_ESCAPES: Record<string, string> = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char] ?? char);
}

function safeUrl(url: string): string {
  if (!/^https?:\/\//i.test(url)) throw new Error(`Güvensiz görsel adresi: ${url.slice(0, 32)}`);
  return escapeHtml(url);
}

function renderInline(inline: Inline): string {
  return inline.t === "text"
    ? escapeHtml(inline.v)
    : katex.renderToString(inline.v, katexOptions(false, false));
}

function renderBlock(block: Block, options: RenderOptions): string {
  switch (block.t) {
    case "p":
      return `<p>${block.c.map(renderInline).join("")}</p>`;
    case "math_block":
      return `<div class="sh-math-block">${katex.renderToString(block.v, katexOptions(true, false))}</div>`;
    case "image":
      return (
        `<figure class="sh-image"><img src="${safeUrl(options.assetUrl(block.asset_id))}" ` +
        `alt="${escapeHtml(block.alt)}" loading="lazy" decoding="async"></figure>`
      );
  }
}

/**
 * Blok-JSON'u HTML'e çevirir. Admin önizlemesi ve mobil QuestionBody (WebView) aynı fonksiyonu kullanır;
 * böylece içerik her yerde aynı görünür (kurallar.md A12). Çıktı KaTeX CSS'i ile birlikte gösterilmelidir.
 * Kaydedilmiş içerik doğrulanmıştır; yine de render hataya düşmez, KaTeX hatalı ifadeyi kaynak olarak gösterir.
 */
export function renderHtml(content: Content, options: RenderOptions): string {
  return `<div class="sh-content">${content.blocks.map((block) => renderBlock(block, options)).join("")}</div>`;
}
