import { z } from "zod";

import { answerSpecSchema, contentSchema } from "./schema";

export const JSON_SCHEMA_ID = "https://schemas.soruhavuzu.app/content/v1.json";

/**
 * schema.json'ın içeriği: zod tanımlarından üretilir (ADR 0019). Anlamsal kurallar (LATEX_*, MCQ_*)
 * JSON Schema'da ifade edilemez; backend onları fixtures/ korpusuyla doğrulanan kendi koduyla uygular.
 */
export interface ContentJsonSchema {
  $schema: string;
  $id: string;
  title: string;
  $defs: Record<string, unknown>;
}

export function buildJsonSchema(): ContentJsonSchema {
  const registry = z.registry<{ id: string }>();
  registry.add(contentSchema, { id: "Content" });
  registry.add(answerSpecSchema, { id: "AnswerSpec" });
  const { schemas } = z.toJSONSchema(registry, {
    target: "draft-2020-12",
    uri: (id) => `#/$defs/${id}`,
    unrepresentable: "throw",
  });
  const defs = Object.fromEntries(
    Object.entries(schemas).map(([id, schema]) => {
      // zod her tanıma "#/$defs/<id>" biçiminde $id ekler; spesifikasyon $id'de fragment'a izin vermez
      const { $schema: _schema, $id: _id, ...rest } = schema;
      return [id, rest];
    }),
  );
  return {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    $id: JSON_SCHEMA_ID,
    title: "SoruHavuzu blok-JSON içerik şeması v1",
    $defs: defs,
  };
}
