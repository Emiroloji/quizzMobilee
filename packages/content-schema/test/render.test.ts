import { describe, expect, it } from "vitest";

import { type Content, renderDocument, renderHtml, toPlainText } from "../src";

const ASSET = "0192f0c4-7a1b-7c3d-8e4f-a1b2c3d4e5f6";
const options = { assetUrl: (id: string) => `https://cdn.soruhavuzu.test/public/${id}.webp` };

const question: Content = {
  schema: 1,
  blocks: [
    { t: "p", c: [{ t: "text", v: "f(x) = " }, { t: "math", v: "\\frac{1}{x}" }, { t: "text", v: " ise?" }] },
    { t: "math_block", v: "\\int_0^1 x\\,dx" },
    { t: "image", asset_id: ASSET, alt: "Şekil 1" },
  ],
};

describe("renderHtml", () => {
  it("metni, satır içi ve blok matematiği, görseli render eder", () => {
    const html = renderHtml(question, options);
    expect(html).toMatch(/^<div class="sh-content"><p>f\(x\) = <span class="katex">/);
    expect(html).toContain('<div class="sh-math-block"><span class="katex-display">');
    expect(html).toContain(`<img src="https://cdn.soruhavuzu.test/public/${ASSET}.webp" alt="Şekil 1"`);
  });

  it("metindeki HTML kaçırılır (XSS)", () => {
    const html = renderHtml({ schema: 1, blocks: [{ t: "p", c: [{ t: "text", v: '<script>alert("x")</script>' }] }] }, options);
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;");
  });

  it("doğrulamayı atlayan \\href bile bağlantıya dönüşmez (trust kapalı)", () => {
    const html = renderHtml({ schema: 1, blocks: [{ t: "math_block", v: "\\href{javascript:alert(1)}{x}" }] }, options);
    // Kaynak, MathML <annotation> içinde kaçırılmış metin olarak kalabilir; tıklanabilir bağlantı oluşmamalı
    expect(html).not.toMatch(/<a[\s>]/);
    expect(html).not.toMatch(/\shref=/);
  });

  it("http(s) dışındaki görsel adresi reddedilir", () => {
    expect(() => renderHtml(question, { assetUrl: () => "javascript:alert(1)" })).toThrow(/Güvensiz/);
  });
});

describe("toPlainText", () => {
  it("LaTeX'i kaynak olarak bırakır, blokları satırlara ayırır", () => {
    expect(toPlainText(question)).toBe("f(x) = \\frac{1}{x} ise?\n\\int_0^1 x\\,dx\nŞekil 1");
  });
});

describe("renderDocument", () => {
  it("fontları gömülü, dış kaynaksız ve betiksiz bağımsız belge üretir", () => {
    const doc = renderDocument(question, options);
    expect(doc).toMatch(/^<!doctype html><html lang="tr">/);
    expect(doc).toContain("default-src 'none'");
    expect(doc).toContain("data:font/woff2;base64,");
    expect(doc).not.toContain("url(fonts/");
    expect(doc).not.toMatch(/<script/i);
    expect(doc).toContain('<div class="sh-content">');
  });

  it("tema rengine CSS enjeksiyonu yapılamaz", () => {
    expect(() =>
      renderDocument(question, { ...options, theme: { text: "red;}</style><script>", background: "#fff", fontSizePx: 16 } }),
    ).toThrow(/tema/);
  });
});
