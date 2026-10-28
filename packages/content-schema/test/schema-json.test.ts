import { writeFileSync } from "node:fs";
import { join } from "node:path";

import { expect, it } from "vitest";

import { buildJsonSchema } from "../src";

// schema.json zod'dan üretilir ve commit'lenir; CI fark görürse build kırılır (ADR 0019).
it("schema.json zod tanımlarından üretilir", () => {
  const schema = buildJsonSchema();
  expect(Object.keys(schema.$defs as object).sort()).toEqual(["AnswerSpec", "Content"]);
  writeFileSync(join(import.meta.dirname, "..", "schema.json"), `${JSON.stringify(schema, null, 2)}\n`);
});
