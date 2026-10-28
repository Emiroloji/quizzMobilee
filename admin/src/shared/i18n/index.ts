import { type Dictionary, tr } from "./tr";

type Leaves<T, Prefix extends string = ""> = {
  [K in keyof T & string]: T[K] extends string ? `${Prefix}${K}` : Leaves<T[K], `${Prefix}${K}.`>;
}[keyof T & string];

/** Sözlükteki her metnin derleme zamanında doğrulanan anahtarı, ör. "auth.title". */
export type TranslationKey = Leaves<Dictionary>;

function lookup(key: TranslationKey): string {
  let node: unknown = tr;
  for (const part of key.split(".")) {
    node = typeof node === "object" && node !== null ? (node as Record<string, unknown>)[part] : undefined; // eslint-disable-line @typescript-eslint/consistent-type-assertions -- sözlük ağacında gezinme; anahtar tipi derlemede doğrulandı
  }
  if (typeof node !== "string") throw new Error(`Çeviri bulunamadı: ${key}`);
  return node;
}

/** `{ad}` yer tutucularını params ile doldurur. */
export function t(key: TranslationKey, params?: Record<string, string | number>): string {
  const text = lookup(key);
  return params ? text.replace(/\{(\w+)\}/g, (match, name: string) => String(params[name] ?? match)) : text;
}
