// TS/JS iş akışlarının ortak lint kuralları (kurallar.md §6). Backend Kotlin'dir ve detekt/ktlint kullanır.
import comments from "@eslint-community/eslint-plugin-eslint-comments/configs";
import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import i18next from "eslint-plugin-i18next";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

const APP_SOURCES = ["mobile/app/**/*.{ts,tsx}", "mobile/src/**/*.{ts,tsx}", "admin/src/**/*.{ts,tsx}"];
const TESTS = ["**/*.test.{ts,tsx}", "**/test/**"];

export default defineConfig(
  {
    ignores: [
      "**/node_modules/**", "**/dist/**", "**/build/**", "**/coverage/**", "**/.expo/**",
      "backend/**", "infra/cdk.out/**", "**/src/generated/**",
      "packages/api-client/src/schema.ts", // OpenAPI'den üretilir, elle düzenlenmez
      "mobile/expo-env.d.ts",
    ],
  },
  js.configs.recommended,
  {
    files: ["**/*.{js,mjs}"],
    languageOptions: { globals: globals.node },
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [tseslint.configs.recommendedTypeChecked],
    languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname } },
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      // `as` yasaktır; zorunluysa satır başında gerekçeli eslint-disable yazılır (`as const` serbest)
      "@typescript-eslint/consistent-type-assertions": ["error", { assertionStyle: "never" }],
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
    },
  },
  comments.recommended,
  {
    rules: { "@eslint-community/eslint-comments/require-description": ["error", { ignore: [] }] },
  },
  {
    files: APP_SOURCES,
    plugins: { "react-hooks": reactHooks },
    rules: reactHooks.configs.recommended.rules,
  },
  {
    files: APP_SOURCES,
    ignores: TESTS,
    plugins: { i18next },
    rules: {
      // Kullanıcıya görünen metin yalnızca i18n anahtarıyla gelir
      "i18next/no-literal-string": ["error", { mode: "jsx-text-only" }],
      "no-restricted-globals": [
        "error",
        { name: "fetch", message: "Sunucu çağrıları yalnızca @soruhavuzu/api-client ile yapılır (kurallar.md §6)." },
      ],
    },
  },
  {
    files: ["mobile/app/**", "mobile/src/features/**", "admin/src/app/**", "admin/src/features/**"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            { group: ["@/features/*/*"], message: "Başka bir feature'a yalnızca index'i üzerinden erişilir (kurallar.md §6)." },
            { group: ["../../*"], message: "Feature dışına göreli import yapılmaz; @/ takma adı ve feature index'i kullanılır." },
          ],
        },
      ],
    },
  },
  {
    files: ["mobile/src/shared/**", "admin/src/shared/**"],
    rules: {
      "no-restricted-imports": [
        "error",
        { patterns: [{ group: ["@/features", "@/features/*", "**/features/**", "@/app/*"], message: "shared katmanı feature'lara ve app'e bağımlı olamaz." }] },
      ],
    },
  },
);
