import { Ajv2020 } from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { describe, expect, it } from "vitest";

import { buildJsonSchema, SCHEMA_INVALID, validateAnswerSpec, validateContent } from "../src";
import { loadFixtures } from "./fixtures";

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats.default(ajv);
ajv.addSchema(buildJsonSchema());
const ajvContent = ajv.getSchema("https://schemas.soruhavuzu.app/content/v1.json#/$defs/Content");
const ajvAnswerSpec = ajv.getSchema("https://schemas.soruhavuzu.app/content/v1.json#/$defs/AnswerSpec");

const suites = [
  { kind: "content" as const, zod: validateContent, jsonSchema: ajvContent },
  { kind: "answer-spec" as const, zod: validateAnswerSpec, jsonSchema: ajvAnswerSpec },
];

describe.each(suites)("$kind korpusu", ({ kind, zod, jsonSchema }) => {
  const fixtures = loadFixtures(kind);

  it("korpus boş değildir", () => {
    expect(fixtures.length).toBeGreaterThan(5);
    expect(jsonSchema).toBeDefined();
  });

  it.each(fixtures)("zod doğrulaması beklenen kararı verir: $name", ({ expect: expected, value }) => {
    const result = zod(value);
    if (expected === "valid") {
      expect(result).toMatchObject({ ok: true });
    } else {
      expect(result.ok).toBe(false);
      const codes = result.ok ? [] : result.issues.map((issue) => issue.code);
      expect(codes).toContain(expected);
    }
  });

  // Yapısal ihlali JSON Schema da yakalar; anlamsal ihlali yakalayamaz, onu backend kendi koduyla uygular.
  it.each(fixtures)("schema.json yapısal kararda zod ile aynı fikirdedir: $name", ({ expect: expected, value }) => {
    expect(jsonSchema?.(value)).toBe(expected !== SCHEMA_INVALID);
  });
});
