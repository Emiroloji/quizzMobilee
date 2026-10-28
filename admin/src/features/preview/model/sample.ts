import type { Content } from "@soruhavuzu/content-schema";

export const SAMPLE_CONTENT: Content = {
  schema: 1,
  blocks: [
    {
      t: "p",
      c: [
        { t: "text", v: "f(x) = " },
        { t: "math", v: "\\frac{x^2-1}{x-1}" },
        { t: "text", v: " ise " },
        { t: "math", v: "\\lim_{x\\to 1} f(x)" },
        { t: "text", v: " kaçtır?" },
      ],
    },
    { t: "math_block", v: "\\int_0^1 \\left( 3x^2 + \\sqrt{x} \\right) dx" },
  ],
};
