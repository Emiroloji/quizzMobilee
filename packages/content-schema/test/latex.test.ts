import { describe, expect, it } from "vitest";

import { findLatexIssues } from "../src";

const codes = (source: string) => findLatexIssues(source).map((issue) => issue.code);

describe("LaTeX izin listesi", () => {
  it.each([
    "\\frac{x^2-1}{x-1}",
    "\\lim_{x\\to 1} f(x)",
    "\\sqrt[3]{27} + \\left( \\dfrac{1}{2} \\right)^{-1}",
    "\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}",
    "\\mathbb{R} \\setminus \\{0\\}",
    "a \\cdot b \\leq \\sum_{i=1}^{n} i",
    "\\text{çözüm kümesi}",
  ])("TYT ifadesi kabul edilir: %s", (source) => {
    expect(findLatexIssues(source)).toEqual([]);
  });

  it.each(["\\href{x}{y}", "\\url{x}", "\\includegraphics{x}", "\\htmlClass{x}{y}", "\\def\\a{b}", "\\newcommand{\\a}{b}"])(
    "güvensiz komut yasaklıdır: %s",
    (source) => {
      expect(codes(source)).toContain("LATEX_FORBIDDEN_COMMAND");
    },
  );

  it("listede olmayan komut reddedilir", () => {
    expect(codes("\\color{red}{x}")).toEqual(["LATEX_UNKNOWN_COMMAND"]);
  });

  it("listede olmayan ortam reddedilir", () => {
    expect(codes("\\begin{equation}x\\end{equation}")).toEqual(["LATEX_FORBIDDEN_ENVIRONMENT"]);
  });

  it("izinli ama bozuk ifade ayrıştırma hatası verir", () => {
    expect(codes("\\frac{1}{")).toEqual(["LATEX_PARSE_ERROR"]);
  });

  it("\\\\ satır sonudur; ardından gelen harfler komut sayılmaz", () => {
    expect(findLatexIssues("a \\\\href")).toEqual([]);
  });

  it("çok uzun kaynak ayrıştırılmadan reddedilir", () => {
    expect(codes("x".repeat(2001))).toEqual(["LATEX_TOO_LONG"]);
  });
});
