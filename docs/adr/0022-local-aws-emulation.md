# ADR 0022 — Lokal S3/SQS: LocalStack yerine SeaweedFS + ElasticMQ

- **Tarih:** 2026-10-29
- **Durum:** Kabul (kapsamı 0023 ile beta sunucusuna genişledi)

## Bağlam
mimari.md §17 ve fazlar.md Faz 0, lokal geliştirme için "docker-compose ile Postgres + LocalStack (S3/SQS)" öngörüyor. Faz 0'da doğrulandı: `localstack/localstack` imajı (2026.x) artık **auth token olmadan açılmıyor**: "License activation failed … set the LOCALSTACK_AUTH_TOKEN". Bu durumda:
- Her geliştiricinin LocalStack hesabı açması ve token yönetmesi gerekir.
- "Temiz makinede README ile ≤ 15 dk" kabul kriteri dış bir hesaba bağlanır.
- CI'da gizli bir token tutmak gerekir.

## Karar
- **S3:** SeaweedFS (Apache-2.0) tek konteynerde S3 ağ geçidiyle (`weed server -s3`), imaj `chrislusf/seaweedfs:4.48`.
- **SQS:** ElasticMQ (Apache-2.0), imaj `softwaremill/elasticmq-native:1.7.1`. Kuyruklar (`media-in`, `media-results`) yapılandırma dosyasıyla açılışta oluşur.
- Uygulama her iki servise standart AWS SDK ile, uç nokta (endpoint) ayarı değiştirilerek bağlanır. Kodda emülatöre özgü dal yoktur (kurallar.md §3.5).
- Bu araçlarda bulunmayan **S3 olay bildirimi → SQS** köprüsü, Faz 1'de medya işleme kurulurken lokal profile özgü bir adaptörle karşılanır (o fazın kararı).

## Alternatifler
- **LocalStack + kişisel token:** Dış hesap bağımlılığı (yukarıda).
- **MinIO:** 2025'ten beri topluluk sürümü için hazır imaj yayınlanmıyor.
- **Garage:** AGPL lisansı, bağımlılık politikamızla (MIT/Apache/BSD) uyumsuz.

## Sonuçlar
- (+) Hesapsız, lisans dostu, hızlı açılan lokal ortam.
- (−) S3 olay bildirimi yok; Faz 1'de lokal adaptör gerekecek.
- (−) LocalStack'in tek araçta sunduğu Lambda emülasyonu yok. media-processor Lambda'sı lokal olarak birim testleri ve doğrudan çağrıyla sınanır.
