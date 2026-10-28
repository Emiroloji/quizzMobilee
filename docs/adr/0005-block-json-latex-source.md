# ADR 0005 — Blok-JSON içerik formatı, LaTeX kaynak olarak saklanır

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Soru gövdesi metin, satır içi/blok matematik ve görsel içerir. İçerik hem mobilde hem admin'de aynı görünmeli, sunucuda doğrulanabilmeli ve güvenli olmalıdır.

## Karar
İçerik **blok-JSON** formatındadır; tek kaynak `packages/content-schema/schema.json` (JSON Schema). Backend aynı şemayla doğrular, TS tarafı zod şemasını buradan türetir. LaTeX **kaynak** olarak saklanır; render edilmiş HTML/SVG saklanmaz. İzinli makrolar `katex-allowlist.json` içindedir; `\href`, `\url`, `\includegraphics`, `\html*` ve trust gerektiren her şey reddedilir. Render yalnızca mobilde `QuestionBody` (WebView + gömülü KaTeX) ve admin'de `content-schema.renderHtml()` ile yapılır.

## Alternatifler
- **Markdown + LaTeX:** Doğrulaması ve güvenli render'ı zor, belirsiz sözdizimi.
- **HTML saklama:** XSS riski, platformlar arası tutarsızlık.

## Sonuçlar
- (+) Yeni blok tipi = şemaya ekle + render'a ekle + `schema` sürümünü artır; eski sürümler render edilmeye devam eder.
- (−) Editör deneyimi için özel LaTeX editörü + canlı önizleme gerekir.
