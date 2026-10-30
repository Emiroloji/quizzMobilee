# dist/ CI'da `pnpm --filter @soruhavuzu/admin build` ile üretilir (VITE_* değerleri derlemede gömülür).
# Root olmadan 8080'de çalışan resmi imaj (nginx:alpine + USER nginx cache dizini yüzünden açılmıyor).
FROM nginxinc/nginx-unprivileged:1.30.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY dist /usr/share/nginx/html
EXPOSE 8080
