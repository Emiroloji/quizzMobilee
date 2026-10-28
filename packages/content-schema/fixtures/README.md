# İçerik doğrulama korpusu

Her dosya `{ "expect": ..., "value": ... }` biçimindedir. `expect` değeri:
- `"valid"`: geçerli içerik
- `"SCHEMA_INVALID"`: yapısal ihlal; `schema.json` (JSON Schema) da reddeder
- `LATEX_*` / `MCQ_*`: anlamsal ihlal; JSON Schema **kabul eder**, anlamsal katman reddeder

`content/` altındakiler `$defs/Content`, `answer-spec/` altındakiler `$defs/AnswerSpec` ile doğrulanır.
TS (zod) ve backend (Kotlin) aynı korpusta aynı kararı vermek zorundadır (ADR 0019).
