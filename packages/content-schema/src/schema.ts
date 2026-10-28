import { z } from "zod";

import { findLatexIssues, LATEX_MAX_SOURCE_LENGTH } from "./latex";

/**
 * Blok-JSON içerik şeması v1 (mimari.md §7). Yazım kaynağı burasıdır; schema.json buradan üretilir (ADR 0019).
 * Yeni blok tipi = buraya ekle + render'a ekle + CONTENT_SCHEMA_VERSION'ı artır.
 */
export const CONTENT_SCHEMA_VERSION = 1;

const MAX_TEXT_LENGTH = 5000;
const MAX_INLINES = 200;
const MAX_BLOCKS = 200;
const MAX_ALT_LENGTH = 300;

/** Anlamsal kuralların hata kodu `params.code` içinde taşınır (validate.ts). */
function issue(ctx: z.RefinementCtx, code: string, message: string, path: (string | number)[] = []) {
  ctx.addIssue({ code: "custom", message, path, params: { code } });
}

const latexSource = z
  .string()
  .min(1)
  .max(LATEX_MAX_SOURCE_LENGTH)
  .superRefine((value, ctx) => {
    if (value.length > LATEX_MAX_SOURCE_LENGTH) return; // yapısal kural zaten raporladı
    for (const latex of findLatexIssues(value)) issue(ctx, latex.code, latex.message);
  });

export const textInlineSchema = z.strictObject({ t: z.literal("text"), v: z.string().min(1).max(MAX_TEXT_LENGTH) });
export const mathInlineSchema = z.strictObject({ t: z.literal("math"), v: latexSource });
export const inlineSchema = z.discriminatedUnion("t", [textInlineSchema, mathInlineSchema]);

export const paragraphBlockSchema = z.strictObject({ t: z.literal("p"), c: z.array(inlineSchema).min(1).max(MAX_INLINES) });
export const mathBlockSchema = z.strictObject({ t: z.literal("math_block"), v: latexSource });
export const imageBlockSchema = z.strictObject({
  t: z.literal("image"),
  asset_id: z.uuid(),
  alt: z.string().min(1).max(MAX_ALT_LENGTH),
});
export const blockSchema = z.discriminatedUnion("t", [paragraphBlockSchema, mathBlockSchema, imageBlockSchema]);

/** Soru gövdesi, çözüm ve şık gövdesi bu yapıdadır. */
export const contentSchema = z.strictObject({
  schema: z.literal(CONTENT_SCHEMA_VERSION),
  blocks: z.array(blockSchema).min(1).max(MAX_BLOCKS),
});

export const OPTION_IDS = ["A", "B", "C", "D", "E"] as const;
const optionIdSchema = z.enum(OPTION_IDS);

export const mcqOptionSchema = z.strictObject({ id: optionIdSchema, body: contentSchema });

/** Çoktan seçmeli (v1): 2–5 şık, tam olarak 1 doğru şık (kurallar.md §10). */
export const mcqAnswerSpecSchema = z
  .strictObject({
    type: z.literal("mcq"),
    options: z.array(mcqOptionSchema).min(2).max(5),
    correct: z.array(optionIdSchema).length(1),
    shuffle: z.boolean(),
  })
  .superRefine((spec, ctx) => {
    const ids = spec.options.map((option) => option.id);
    if (new Set(ids).size !== ids.length) {
      issue(ctx, "MCQ_DUPLICATE_OPTION_ID", "Şık kimlikleri tekil olmalı", ["options"]);
    } else if (ids.some((id, index) => id !== OPTION_IDS[index])) {
      issue(ctx, "MCQ_OPTION_IDS_NOT_SEQUENTIAL", "Şıklar A'dan başlayıp sırayla gitmeli", ["options"]);
    }
    spec.correct.forEach((id, index) => {
      if (!ids.includes(id)) issue(ctx, "MCQ_CORRECT_NOT_AN_OPTION", `${id} şıklardan biri değil`, ["correct", index]);
    });
    const bodies = spec.options.map((option) => JSON.stringify(option.body));
    if (new Set(bodies).size !== bodies.length) {
      issue(ctx, "MCQ_DUPLICATE_OPTION_BODY", "Şıklar birebir aynı olamaz", ["options"]);
    }
  });

/** Soru tipine göre cevap şeması. v1'de yalnızca `mcq`; `numeric` v2'de eklenir (mimari.md §20.2). */
export const answerSpecSchema = z.discriminatedUnion("type", [mcqAnswerSpecSchema]);

export type TextInline = z.infer<typeof textInlineSchema>;
export type MathInline = z.infer<typeof mathInlineSchema>;
export type Inline = z.infer<typeof inlineSchema>;
export type Block = z.infer<typeof blockSchema>;
export type Content = z.infer<typeof contentSchema>;
export type McqOption = z.infer<typeof mcqOptionSchema>;
export type McqAnswerSpec = z.infer<typeof mcqAnswerSpecSchema>;
export type AnswerSpec = z.infer<typeof answerSpecSchema>;
