import { config } from "../config";

/** Görseller her zaman CDN (CloudFront) adresinden okunur (kurallar.md §6, mimari.md §13). */
export function assetUrl(assetId: string): string {
  return `${config.assetBaseUrl}/public/${assetId}.webp`;
}
