// KaTeX CSS'ini fontlar base64 (woff2) gömülü olarak üretir: WebView ve sandbox'lı iframe
// dışarıdan font yükleyemez; içerik tamamen offline render edilir (fazlar.md Faz 0).
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const distDir = dirname(require.resolve("katex/dist/katex.min.css"));
const css = readFileSync(join(distDir, "katex.min.css"), "utf8");

let fonts = 0;
const inlined = css.replace(/src:url\(fonts\/([\w-]+)\.woff2\) format\("woff2"\)[^;}]*/g, (_, name) => {
  fonts += 1;
  const data = readFileSync(join(distDir, "fonts", `${name}.woff2`)).toString("base64");
  return `src:url(data:font/woff2;base64,${data}) format("woff2")`;
});
if (fonts === 0 || inlined.includes("url(fonts/")) throw new Error("KaTeX font tanımları beklenen biçimde değil");

const outDir = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "generated");
mkdirSync(outDir, { recursive: true });
writeFileSync(
  join(outDir, "katex-css.ts"),
  `// Otomatik üretildi (scripts/build-katex-css.mjs); elle düzenlenmez.\nexport const KATEX_CSS = ${JSON.stringify(inlined)};\n`,
);
console.log(`katex-css: ${fonts} font gömüldü (${Math.round(inlined.length / 1024)} KB)`);
