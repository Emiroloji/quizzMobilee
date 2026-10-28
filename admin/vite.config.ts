import { fileURLToPath, URL } from "node:url";

import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: { port: 5173, strictPort: true },
  // Önizleme paketi gömülü KaTeX fontlarıyla bilinçli olarak büyük (~350 KB gzip) ve lazy yüklenir
  build: { chunkSizeWarningLimit: 700 },
  test: { environment: "jsdom" },
});
