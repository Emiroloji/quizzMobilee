import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { PreviewPage } from "./PreviewPage";

describe("PreviewPage", () => {
  afterEach(cleanup);

  it("geçerli içeriği betiksiz sandbox'lı iframe'de fontları gömülü belge olarak gösterir", () => {
    render(<PreviewPage />);
    const frame = screen.getByTitle("Önizleme");
    expect(frame.getAttribute("sandbox")).toBe("");
    const doc = frame.getAttribute("srcdoc") ?? "";
    expect(doc).toContain('class="katex"');
    expect(doc).toContain("data:font/woff2;base64,");
  });

  it("izinsiz LaTeX'i hata koduyla gösterir ve render etmez", () => {
    render(<PreviewPage />);
    const input = screen.getByLabelText("İçerik (blok-JSON)");
    fireEvent.change(input, {
      target: { value: JSON.stringify({ schema: 1, blocks: [{ t: "math_block", v: "\\href{x}{y}" }] }) },
    });
    expect(screen.getByText("LATEX_FORBIDDEN_COMMAND")).toBeTruthy();
    expect(screen.queryByTitle("Önizleme")).toBeNull();
  });

  it("bozuk JSON'da ayrıştırma hatası gösterir", () => {
    render(<PreviewPage />);
    fireEvent.change(screen.getByLabelText("İçerik (blok-JSON)"), { target: { value: "{" } });
    expect(screen.getByText("JSON ayrıştırılamadı.")).toBeTruthy();
  });
});
