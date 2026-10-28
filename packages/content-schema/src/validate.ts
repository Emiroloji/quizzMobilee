import type { z } from "zod";

import { type AnswerSpec, answerSpecSchema, type Content, contentSchema } from "./schema";

/** Yapısal (JSON Schema) ihlallerin ortak kodu; anlamsal ihlaller kendi kodunu taşır (LATEX_*, MCQ_*). */
export const SCHEMA_INVALID = "SCHEMA_INVALID";

export interface ContentIssue {
  /** JSON yolu, ör. `$.blocks.0.c.1.v` */
  path: string;
  code: string;
  message: string;
}

export type ValidationResult<T> = { ok: true; value: T } | { ok: false; issues: ContentIssue[] };

function toIssue(zodIssue: z.core.$ZodIssue): ContentIssue {
  const custom: unknown = zodIssue.code === "custom" ? zodIssue.params?.code : undefined;
  const code = typeof custom === "string" ? custom : SCHEMA_INVALID;
  return { path: ["$", ...zodIssue.path.map(String)].join("."), code, message: zodIssue.message };
}

function validate<T>(schema: z.ZodType<T>, input: unknown): ValidationResult<T> {
  const result = schema.safeParse(input);
  return result.success ? { ok: true, value: result.data } : { ok: false, issues: result.error.issues.map(toIssue) };
}

export const validateContent = (input: unknown): ValidationResult<Content> => validate(contentSchema, input);

export const validateAnswerSpec = (input: unknown): ValidationResult<AnswerSpec> => validate(answerSpecSchema, input);
