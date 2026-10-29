# ADR 0023 — Tek sunucu (Dokploy) dağıtımı; AWS kullanılmaz

- **Tarih:** 2026-10-29
- **Durum:** Kabul (proje sahibi kararı)
- **İlgili:** ADR 0010'un yerini kısmen alır (Lambda, Rekognition, CloudFront). ADR 0022'nin kapsamını sunucuya genişletir. Tasarım: `docs/superpowers/specs/2026-10-29-tek-sunucu-dokploy-design.md`

## Bağlam
mimari.md §2 ve §17, AWS üzerinde bir topoloji öngörüyor: ECS Fargate, RDS, S3 + CloudFront, SQS, Lambda, Rekognition, Cognito, Secrets Manager, CloudWatch/X-Ray. Proje sahibinin Türkiye'de, 5 GB RAM'li, Dokploy kurulu ve yalnızca bu projeye ayrılmış bir VPS'i var. AWS hesabı açılmayacak. Beta aşamasında maliyet ve operasyon yükü en aza indirilmeli.

## Karar
- Tüm sistem tek VPS'te, Dokploy (Docker + Traefik) ile çalışır. İnternete yalnızca Traefik (80/443) açıktır.
- Ortam: tek `beta` ortamı (uygulama profili `staging`). `prod` profili kodda kalır; prod sunucusu Faz 4–5'te belirlenir.

| AWS bileşeni | Yerine |
|---|---|
| RDS PostgreSQL | Dokploy'un yönetilen Postgres'i, imaj `pgvector/pgvector:0.8.7-pg17`, günlük yedek |
| ECS Fargate + ALB | Dokploy uygulaması (GHCR imajı) + Traefik |
| S3 + CloudFront OAC | SeaweedFS S3 ağ geçidi `media.<host>`; CDN yok (alan adı gelince Cloudflare) |
| SQS | ElasticMQ, kalıcı mesaj deposuyla |
| Lambda media-processor | Medya worker konteyneri (Faz 1) |
| Rekognition | Faz 1 kararı. O zamana kadar yeni kullanıcı soruları `IN_REVIEW`'da insan onayından geçer |
| Cognito | Logto (ADR 0024) |
| Secrets Manager | Dokploy ortam değişkenleri; repoda değer yok |
| CloudWatch / X-Ray | Dokploy log görüntüleyici (JSON log). Trace dışa aktarımı kapalı, agent traceId'yi loglara yazar |
| ACM / CloudFront TLS | Traefik + Let's Encrypt. Alan adı gelene kadar `*.<ip>.sslip.io` |
| CDK | Dokploy yapılandırması + repodaki `deploy/` compose tanımları ve runbook |
| ECR | GHCR (çok mimarili: amd64 + arm64) |

- İmajlar CI'da derlenir. Sunucu yalnızca imaj çeker ve sunucuda Gradle derlemesi yapılmaz.
- Her servisin bellek sınırı vardır. Toplam yaklaşık 3 GB'tır; 5 GB'ın içinde Dokploy ve işletim sistemine pay kalır.
- KVKK: Veri Türkiye'de kalır; yurt dışı aktarım yükümlülüğü yoktur (proje.md §10 güncellenir).

## Alternatifler
- **AWS (önceki plan):** Aylık sabit maliyet ve hesap yönetimi; beta için orantısız.
- **Başka bir PaaS (Fly.io, Railway):** Veri yurt dışında olur (KVKK) ve maliyet artar.

## Sonuçlar
- (+) Ek maliyet yok, veri Türkiye'de, lokal ortamla aynı bileşenler (ADR 0022).
- (+) Kod yalnızca yapılandırmayla taşınabilir: S3/SQS uç noktası, OIDC issuer. Gerekirse AWS'e geri dönüş mümkün.
- (−) Tek hata noktası. Sunucu dışı yedek, gerçek kullanıcılardan (Faz 4) önce zorunlu.
- (−) Yönetilen servislerin otomatik ölçeklemesi ve yüksek erişilebilirliği yok. Beta için kabul edildi; Faz 4 yük testinde ölçülür.
