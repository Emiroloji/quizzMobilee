# SoruHavuzu — lokal geliştirme komutları. Ayrıntı: README.md
.DEFAULT_GOAL := help
SHELL := /bin/bash

COMPOSE := docker compose
SMOKE := docker compose -f deploy/docker-compose.beta.yml -f deploy/docker-compose.smoke.yml --env-file deploy/smoke.env
GRADLE := cd backend && ./gradlew --console=plain

.PHONY: help images doctor install infra dev backend backend-otel admin mobile check test lint typecheck api down reset logto-claims beta-smoke

help: ## Komutları listeler
	@grep -hE '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-14s %s\n", $$1, $$2}'

doctor: ## Gerekli araçları ve sürümleri kontrol eder
	@node -v | grep -qE '^v24\.' || { echo "✗ Node 24 gerekli (bulunan: $$(node -v 2>/dev/null || echo yok)). Bkz. .nvmrc"; exit 1; }
	@pnpm -v >/dev/null 2>&1 || { echo "✗ pnpm yok: corepack enable pnpm"; exit 1; }
	@java -version >/dev/null 2>&1 || { echo "✗ JDK 21+ gerekli (Java 25'i Gradle kendisi indirir)"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "✗ Docker çalışmıyor; Docker Desktop'ı açın"; exit 1; }
	@echo "✓ node $$(node -v) · pnpm $$(pnpm -v) · java $$(java -version 2>&1 | head -1 | cut -d'"' -f2) · docker çalışıyor"

install: ## JS bağımlılıklarını kilit dosyasına göre kurar
	pnpm install --frozen-lockfile

infra: ## Postgres, S3 (SeaweedFS) ve SQS (ElasticMQ) konteynerlerini başlatır
	$(COMPOSE) up -d --wait

dev: doctor install infra ## Her şeyi kurar ve backend + admin'i birlikte çalıştırır (Ctrl-C durdurur)
	@echo "→ API: http://localhost:8080  ·  Admin: http://localhost:5173  ·  Swagger: http://localhost:8080/swagger-ui.html"
	@$(MAKE) --no-print-directory -j2 backend admin

backend: ## Backend'i local profille çalıştırır (sahte JWT üretici açık)
	$(GRADLE) bootRun --args='--spring.profiles.active=local'

backend-otel: ## Backend'i OpenTelemetry agent'ı ve JSON log ile çalıştırır
	$(GRADLE) bootRun -Potel --args='--spring.profiles.active=local --logging.structured.format.console=com.soruhavuzu.shared.logging.JsonLogFormatter'

admin: ## Admin panelini çalıştırır (Vite)
	pnpm --filter @soruhavuzu/admin dev

mobile: ## Expo geliştirme sunucusunu başlatır (QR kod / a: Android / i: iOS)
	pnpm --filter @soruhavuzu/mobile start

api: ## OpenAPI belgesini ve TS istemcisini yeniden üretir
	pnpm api:generate

lint: ## ESLint + ktlint + detekt
	pnpm lint
	$(GRADLE) ktlintCheck detekt detektMain

typecheck: ## Tüm TS paketlerinde tip kontrolü
	pnpm typecheck

test: ## Tüm testler (backend + TS)
	$(GRADLE) test
	pnpm test

check: lint typecheck test ## CI ile aynı kontroller

down: ## Konteynerleri durdurur (veri kalır)
	$(COMPOSE) down

reset: ## Konteynerleri ve lokal veriyi siler
	$(COMPOSE) down -v

logto-claims: ## Logto access token biçimini gösterir: make logto-claims ID=... SECRET=...
	deploy/scripts/logto-token-claims.sh http://localhost:3001/oidc "$(ID)" "$(SECRET)" http://localhost:8080

images: ## Backend ve admin imajlarını yerel mimaride üretir: make images TAG=smoke OWNER=local
	$(GRADLE) bootJar otelAgentJar
	pnpm --filter @soruhavuzu/admin build
	docker build -t ghcr.io/$(OWNER)/soruhavuzu-api:$(TAG) backend
	docker build -t ghcr.io/$(OWNER)/soruhavuzu-admin:$(TAG) admin

beta-smoke: ## Beta compose'unu yerel imajlarla ayağa kaldırıp tüm servisleri doğrular
	@set -euo pipefail; \
	printf 'BASE_HOST=smoke.localhost\nGHCR_OWNER=local\nIMAGE_TAG=smoke\nDB_HOST=postgres\nDB_PASSWORD=smoke\nLOGTO_DB_PASSWORD=smoke\nOIDC_CLIENT_IDS=\nLOG_USER_ID_HASH_KEY=smoke\nS3_ACCESS_KEY=smoke\nS3_SECRET_KEY=smoke-secret\n' > deploy/smoke.env; \
	trap 'rc=$$?; if [ $$rc -ne 0 ]; then echo "✗ duman testi başarısız; son günlükler:"; $(SMOKE) logs --tail 50 || true; fi; $(SMOKE) down -v --remove-orphans || true; rm -f deploy/smoke.env; exit $$rc' EXIT; \
	$(SMOKE) up -d --wait --wait-timeout 300 postgres api logto s3 sqs admin; \
	curl -sf http://127.0.0.1:18080/actuator/health | grep -q '"UP"'; \
	test "$$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/v1/me)" = "401"; \
	curl -sf http://127.0.0.1:13001/oidc/.well-known/openid-configuration | grep -q '"issuer":"https://auth.smoke.localhost/oidc"'; \
	curl -sf http://127.0.0.1:18333/status >/dev/null; \
	curl -sf "http://127.0.0.1:19324/?Action=ListQueues" | grep -q media-in; \
	curl -sf http://127.0.0.1:18082/ | grep -q '<div id="root">'; \
	$(SMOKE) run --rm s3-init; \
	$(SMOKE) run --rm s3-init; \
	$(SMOKE) exec -T s3 sh -c "echo s3.bucket.list | weed shell -master=127.0.0.1:9333" | grep -q soruhavuzu-media-beta; \
	echo "✓ beta compose: api, logto, s3 (kova dahil), sqs, admin sağlıklı"
