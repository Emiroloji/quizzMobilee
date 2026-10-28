import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

export interface Fixture {
  name: string;
  expect: string;
  value: unknown;
}

const ROOT = join(import.meta.dirname, "..", "fixtures");

export function loadFixtures(kind: "content" | "answer-spec"): Fixture[] {
  const dir = join(ROOT, kind);
  return readdirSync(dir)
    .filter((file) => file.endsWith(".json"))
    .sort()
    .map((file) => {
      const parsed = JSON.parse(readFileSync(join(dir, file), "utf8")) as { expect: string; value: unknown };
      return { name: file.replace(/\.json$/, ""), ...parsed };
    });
}
