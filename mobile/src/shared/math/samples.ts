import type { Content } from "@soruhavuzu/content-schema";

/** Faz 0 kabul kriteri: integral, kesir ve limit içeren örnek soru (offline render doğrulaması). */
export const SAMPLE_QUESTION: Content = {
  schema: 1,
  blocks: [
    {
      t: "p",
      c: [
        { t: "text", v: "f(x) = " },
        { t: "math", v: "\\frac{x^2-1}{x-1}" },
        { t: "text", v: " olmak üzere " },
        { t: "math", v: "\\lim_{x\\to 1} f(x)" },
        { t: "text", v: " değeri ile aşağıdaki integralin toplamı kaçtır?" },
      ],
    },
    { t: "math_block", v: "\\int_0^1 \\left( 3x^2 + \\sqrt{x} \\right) dx" },
    { t: "math_block", v: "g(x)=\\begin{cases} x^2, & x<0 \\\\ 2x, & x\\geq 0 \\end{cases}" },
  ],
};
