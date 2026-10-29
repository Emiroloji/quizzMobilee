# Faz 0 / Adım 9 — AWS Altyapısı, Konteyner, CI/CD ve EAS Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faz 0'ın kalan üç kabul kriterini (staging'e giriş + `GET /me`, `main` → staging otomatik deploy, temiz makinede kurulum) karşılayacak AWS altyapısını (CDK), backend konteynerini, GitHub Actions boru hattını ve EAS profillerini kurmak.

**Architecture:** Ortam başına iki CDK yığını vardır. `soruhavuzu-<env>-platform` uzun ömürlüdür: ağ, RDS, medya, Cognito, ECR ve admin web. `soruhavuzu-<env>-api` her deploy'da güncellenir: ECS Fargate, iç ALB ve CloudFront. Hesap başına bir de `soruhavuzu-ci` yığını vardır: GitHub OIDC rolü. Alan adı olmadan TLS, CloudFront'un varsayılan sertifikasıyla sağlanır; API'nin ALB'si internete kapalıdır ve CloudFront VPC origin üzerinden erişilir. NAT yoktur: görevler public subnet'te public IP ile dışarı çıkar, gelen trafik yalnızca ALB güvenlik grubundan kabul edilir.

**Tech Stack:** AWS CDK 2.273.0 (TS, Node 24 tip ayıklamasıyla doğrudan çalışır), vitest 5.0.4 + `aws-cdk-lib/assertions`, eclipse-temurin 25 JRE, ADOT collector sidecar, GitHub Actions (SHA'ya sabitli), EAS CLI 25.0.0.

**Spec:** NotebookLM "Modüler Soru Havuzu Mimarisi ve Kuralları" → `mimari.md` §2 (yığın), §3 (sistem görünümü), §13 (medya), §14 (kimlik), §17 (altyapı), §18 (gözlemlenebilirlik), §19 (güvenlik); `fazlar.md` Faz 0 "Altyapı" maddeleri ve kabul kriterleri; `kurallar.md` §8 (git/süreç), §9 (güvenlik). Repo içi kararlar: `docs/adr/0001-0022`.

## Global Constraints

- Bölge `eu-central-1` (Frankfurt). Ortamlar `staging` ve `prod`; ikisi de CDK ile.
- RDS: PostgreSQL 17, `db.t4g.small`, tek AZ, 7 gün otomatik yedek, şifreli.
- ECS Fargate: 0.5 vCPU / 1 GB × 1 görev (min 1, max 3).
- NAT Gateway yok. S3 bucket'ları public değil; okuma yalnızca CloudFront OAC.
- Medya bucket'ı `soruhavuzu-media-{env}`, versioning açık; `uploads/` öneki 24 saatlik lifecycle ile temizlenir.
- SQS: `media-in`, `media-results` (her biri DLQ'lu).
- Cognito **Lite** plan; Apple, Google, e-posta.
- Gizli bilgiler yalnızca Secrets Manager'da; repoda `.env` örneği dışında gizli değer yok.
- Tüm trafik TLS.
- `main`'e merge → otomatik `staging` deploy; `prod` deploy elle onaylı, tag ile (kurallar.md §8).
- CI sırası: lint → test → mimari testler → OpenAPI farkı → gitleaks → Docker → ECR → ECS (staging).
- Bağımlılık sürümleri ≥ 14 günlük ve kesin (ADR 0020); GitHub Actions commit SHA'sına sabitlenir.
- Node 24, `auto-install-peers=false`, TypeScript 5.9.3, kod İngilizce, yorumlar ve kullanıcı metinleri Türkçe.
- **Dışa dönük ve maliyetli adımlar** (AWS deploy, GitHub repo oluşturma, push) yalnızca kullanıcının açık onayıyla yapılır. Görev 1–11 hesap gerektirmeden yerelde doğrulanır; Görev 12 onaya bağlıdır.

---

## Dosya Yapısı

```
infra/
├── package.json  tsconfig.json  cdk.json  vitest.config.ts
├── bin/app.ts                         # yığınları ortam başına oluşturur
├── lib/
│   ├── config.ts                      # ortam ayarları (boyutlar, koruma, alan adları)
│   ├── platform-stack.ts              # Network + Database + Media + Auth + Registry + AdminWeb
│   ├── api-stack.ts                   # ApiService
│   ├── ci-stack.ts                    # GitHub OIDC rolü
│   └── constructs/
│       ├── network.ts  database.ts  media.ts  auth.ts
│       ├── registry.ts  admin-web.ts  api-service.ts  github-oidc.ts
└── test/
    ├── config.test.ts  network.test.ts  database.test.ts  media.test.ts
    ├── auth.test.ts  admin-web.test.ts  api-service.test.ts  ci.test.ts  app.test.ts
backend/Dockerfile  backend/.dockerignore
backend/src/main/resources/application-staging.yml  application-prod.yml (güncellenir)
.github/workflows/ci.yml  .github/workflows/release.yml  .github/actionlint.yaml
mobile/eas.json
docs/adr/0023-aws-topology.md  docs/runbooks/ilk-deploy.md
```

---

### Task 1: ADR 0023 ve CDK çalışma alanı

**Files:**
- Create: `docs/adr/0023-aws-topology.md`, `infra/package.json`, `infra/tsconfig.json`, `infra/cdk.json`, `infra/vitest.config.ts`, `infra/lib/config.ts`, `infra/test/config.test.ts`
- Modify: `.gitignore` (zaten `infra/cdk.out/` içeriyor; değişiklik gerekmez)

**Interfaces:**
- Produces: `type EnvName = "staging" | "prod"`, `interface EnvConfig`, `function loadConfig(name: EnvName): EnvConfig`, `const REGION = "eu-central-1"`

- [ ] **Step 1: ADR 0023'ü yaz**

```markdown
# ADR 0023 — AWS topolojisi: alan adsız TLS, NAT'sız çıkış, iki yığın

- **Tarih:** 2026-10-29
- **Durum:** Kabul

## Bağlam
mimari.md §17 NAT'sız bir VPC, ECS Fargate + ALB, RDS, S3 + CloudFront OAC, SQS, Cognito Lite ve Secrets Manager istiyor; ayrıca §19 tüm trafiğin TLS olmasını şart koşuyor. Henüz alan adı yok; ALB'de ACM sertifikası için alan adı gerekir. Görevlerin Cognito JWKS ve (Faz 3) Expo Push gibi internet adreslerine çıkışı gerekir. Interface VPC endpoint'leri AZ başına saatlik ücretlidir: 5 endpoint × 2 AZ, bir NAT Gateway'den pahalıdır.

## Karar
1. **TLS:** API ve admin, CloudFront'un `*.cloudfront.net` sertifikasıyla sunulur. API'nin ALB'si **internal**'dır ve CloudFront'a **VPC origin** ile bağlanır. İnternetten doğrudan erişilemez. Alan adı alınınca yalnızca CloudFront'a alias ve ACM sertifikası eklenir.
2. **Çıkış:** Fargate görevleri public subnet'te **public IP** ile çalışır. Güvenlik grubu yalnızca ALB'den 8080'e gelen trafiğe izin verir. NAT yoktur.
3. **Endpoint'ler:** Yalnızca ücretsiz **S3 gateway endpoint**. SQS/Secrets Manager/ECR/Logs interface endpoint'leri `config.interfaceEndpoints` ile açılabilir; staging'de kapalı, prod'da kapalı başlar.
4. **Yığınlar:** Ortam başına `platform` (uzun ömürlü kaynaklar) ve `api` (her deploy'da değişen servis) yığınları; hesap başına `ci` yığını (GitHub OIDC rolü). Böylece image güncellemesi veritabanını içeren yığına dokunmaz.
5. **İzleme:** OpenTelemetry Java agent → aynı görevdeki **ADOT collector** sidecar'ı (OTLP) → X-Ray. Loglar CloudWatch Logs'a JSON olarak gider.
6. **Admin web:** S3 (private) + CloudFront OAC, SPA yönlendirmesi.

## Alternatifler
- **Public ALB + HTTP:** §19'u çiğner.
- **NAT Gateway:** Aylık sabit maliyet; ihtiyaç yalnızca birkaç dış çağrı.
- **Tüm interface endpoint'ler:** Staging için orantısız maliyet.

## Sonuçlar
- (+) Alan adı olmadan TLS; ALB internete kapalı; NAT maliyeti yok.
- (−) Görevlerin public IP'si var (yalnızca çıkış; gelen trafik SG ile kapalı).
- (−) CloudFront VPC origin, ALB ile aynı hesap/bölge gerektirir (sağlanıyor).
```

- [ ] **Step 2: `infra/package.json`, `tsconfig.json`, `cdk.json`, `vitest.config.ts` oluştur**

`infra/package.json`:
```json
{
  "name": "@soruhavuzu/infra",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "cdk": "cdk",
    "synth": "cdk synth --quiet",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "aws-cdk-lib": "2.273.0",
    "constructs": "10.8.1"
  },
  "devDependencies": {
    "@types/node": "24.19.2",
    "aws-cdk": "2.1145.0",
    "typescript": "5.9.3",
    "vite": "8.3.4",
    "vitest": "5.0.4"
  }
}
```

`infra/tsconfig.json` (Node 24 TS'yi doğrudan çalıştırır; yalnızca silinebilir tip sözdizimi):
```json
{
  "extends": "../tsconfig.base.json",
  "compilerOptions": {
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "erasableSyntaxOnly": true,
    "types": ["node"]
  },
  "include": ["bin", "lib", "test", "vitest.config.ts"]
}
```

`infra/cdk.json`:
```json
{
  "app": "node bin/app.ts",
  "context": {
    "@aws-cdk/core:checkSecretUsage": true,
    "@aws-cdk/aws-iam:minimizePolicies": true
  }
}
```

`infra/vitest.config.ts`:
```ts
import { defineConfig } from "vitest/config";

export default defineConfig({ test: { testTimeout: 60_000 } });
```

- [ ] **Step 3: Başarısız testi yaz — `infra/test/config.test.ts`**

```ts
import { describe, expect, it } from "vitest";

import { loadConfig, REGION } from "../lib/config.ts";

describe("ortam ayarları", () => {
  it("mimari.md §17 boyutlarını kullanır", () => {
    const staging = loadConfig("staging");
    expect(REGION).toBe("eu-central-1");
    expect(staging.database.instanceClass).toBe("t4g.small");
    expect(staging.database.backupRetentionDays).toBe(7);
    expect(staging.api).toEqual({ cpu: 512, memoryMiB: 1024, minTasks: 1, maxTasks: 3 });
  });

  it("prod silinmeye karşı korumalıdır, staging değildir", () => {
    expect(loadConfig("prod").deletionProtection).toBe(true);
    expect(loadConfig("staging").deletionProtection).toBe(false);
  });

  it("interface endpoint'ler varsayılan olarak kapalıdır (ADR 0023)", () => {
    expect(loadConfig("staging").interfaceEndpoints).toBe(false);
    expect(loadConfig("prod").interfaceEndpoints).toBe(false);
  });
});
```

- [ ] **Step 4: Testi çalıştır, kırıldığını gör**

Run: `cd infra && pnpm install && pnpm test -- test/config.test.ts`
Expected: FAIL — `Cannot find module '../lib/config.ts'`

- [ ] **Step 5: `infra/lib/config.ts`'yi yaz**

```ts
export const REGION = "eu-central-1";

export type EnvName = "staging" | "prod";

export interface EnvConfig {
  name: EnvName;
  deletionProtection: boolean;
  interfaceEndpoints: boolean;
  database: { instanceClass: "t4g.small"; backupRetentionDays: number; allocatedStorageGiB: number };
  api: { cpu: 512; memoryMiB: 1024; minTasks: number; maxTasks: number };
  /** Cognito managed login alan öneki: <prefix>.auth.eu-central-1.amazoncognito.com */
  cognitoDomainPrefix: string;
}

const base = {
  interfaceEndpoints: false,
  database: { instanceClass: "t4g.small", backupRetentionDays: 7, allocatedStorageGiB: 20 },
  api: { cpu: 512, memoryMiB: 1024, minTasks: 1, maxTasks: 3 },
} as const;

const CONFIGS: Record<EnvName, EnvConfig> = {
  staging: { ...base, name: "staging", deletionProtection: false, cognitoDomainPrefix: "soruhavuzu-staging" },
  prod: { ...base, name: "prod", deletionProtection: true, cognitoDomainPrefix: "soruhavuzu" },
};

export function loadConfig(name: EnvName): EnvConfig {
  return CONFIGS[name];
}
```

- [ ] **Step 6: Testi çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/config.test.ts && pnpm typecheck`
Expected: 3 test PASS, typecheck hatasız.

- [ ] **Step 7: Commit**

```bash
git add docs/adr/0023-aws-topology.md infra pnpm-lock.yaml
git commit -m "chore(infra): CDK çalışma alanı, ortam ayarları ve ADR 0023"
```

---

### Task 2: Ağ (NAT'sız VPC)

**Files:**
- Create: `infra/lib/constructs/network.ts`, `infra/test/network.test.ts`

**Interfaces:**
- Consumes: `EnvConfig`
- Produces: `class Network extends Construct { readonly vpc: ec2.Vpc }` — `new Network(scope, "Network", { config })`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";

import { loadConfig } from "../lib/config.ts";
import { Network } from "../lib/constructs/network.ts";

function synth(interfaceEndpoints = false) {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  new Network(stack, "Network", { config: { ...loadConfig("staging"), interfaceEndpoints } });
  return Template.fromStack(stack);
}

describe("Network", () => {
  it("NAT Gateway oluşturmaz (mimari.md §17)", () => {
    synth().resourceCountIs("AWS::EC2::NatGateway", 0);
  });

  it("public ve isolated subnet'ler iki AZ'dedir", () => {
    const subnets = synth().findResources("AWS::EC2::Subnet");
    expect(Object.keys(subnets)).toHaveLength(4);
  });

  it("yalnızca ücretsiz S3 gateway endpoint'i açar", () => {
    const template = synth();
    template.resourceCountIs("AWS::EC2::VPCEndpoint", 1);
    template.hasResourceProperties("AWS::EC2::VPCEndpoint", { VpcEndpointType: "Gateway" });
  });

  it("istenirse interface endpoint'leri ekler (ADR 0023)", () => {
    synth(true).resourceCountIs("AWS::EC2::VPCEndpoint", 6);
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/network.test.ts`
Expected: FAIL — `Cannot find module '../lib/constructs/network.ts'`

- [ ] **Step 3: `network.ts`'yi yaz**

```ts
import * as ec2 from "aws-cdk-lib/aws-ec2";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/** NAT'sız VPC: görevler public subnet'te public IP ile çıkar, RDS isolated subnet'te (ADR 0023). */
export class Network extends Construct {
  readonly vpc: ec2.Vpc;

  constructor(scope: Construct, id: string, props: { config: EnvConfig }) {
    super(scope, id);
    this.vpc = new ec2.Vpc(this, "Vpc", {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: "public", subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
        { name: "isolated", subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
      gatewayEndpoints: { S3: { service: ec2.GatewayVpcEndpointAwsService.S3 } },
    });
    if (props.config.interfaceEndpoints) {
      const services = {
        Sqs: ec2.InterfaceVpcEndpointAwsService.SQS,
        SecretsManager: ec2.InterfaceVpcEndpointAwsService.SECRETS_MANAGER,
        EcrApi: ec2.InterfaceVpcEndpointAwsService.ECR,
        EcrDocker: ec2.InterfaceVpcEndpointAwsService.ECR_DOCKER,
        Logs: ec2.InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS,
      };
      for (const [name, service] of Object.entries(services)) {
        this.vpc.addInterfaceEndpoint(name, { service, subnets: { subnetType: ec2.SubnetType.PUBLIC } });
      }
    }
  }
}
```

- [ ] **Step 4: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/network.test.ts`
Expected: 4 PASS

- [ ] **Step 5: Commit**

```bash
git add infra/lib/constructs/network.ts infra/test/network.test.ts
git commit -m "feat(infra): NAT'sız VPC ve S3 gateway endpoint"
```

---

### Task 3: Veritabanı ve uygulama sırları

**Files:**
- Create: `infra/lib/constructs/database.ts`, `infra/test/database.test.ts`

**Interfaces:**
- Consumes: `Network.vpc`, `EnvConfig`
- Produces: `class Database extends Construct { readonly instance: rds.DatabaseInstance; readonly secret: secretsmanager.ISecret; readonly logHashKey: secretsmanager.Secret; allowFrom(peer: ec2.IConnectable): void }`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { loadConfig, type EnvName } from "../lib/config.ts";
import { Database } from "../lib/constructs/database.ts";
import { Network } from "../lib/constructs/network.ts";

function synth(env: EnvName = "staging") {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  const config = loadConfig(env);
  const network = new Network(stack, "Network", { config });
  new Database(stack, "Database", { config, vpc: network.vpc });
  return Template.fromStack(stack);
}

describe("Database", () => {
  it("PostgreSQL 17, t4g.small, tek AZ, şifreli, 7 gün yedek, public değil", () => {
    synth().hasResourceProperties("AWS::RDS::DBInstance", {
      Engine: "postgres",
      EngineVersion: Match.stringLikeRegexp("^17"),
      DBInstanceClass: "db.t4g.small",
      MultiAZ: false,
      StorageEncrypted: true,
      BackupRetentionPeriod: 7,
      PubliclyAccessible: false,
    });
  });

  it("ana parola Secrets Manager'da üretilir", () => {
    synth().hasResourceProperties("AWS::SecretsManager::Secret", {
      GenerateSecretString: Match.objectLike({ SecretStringTemplate: Match.stringLikeRegexp("soruhavuzu") }),
    });
  });

  it("log userId özet anahtarı ayrı bir sırdır", () => {
    synth().hasResourceProperties("AWS::SecretsManager::Secret", {
      Description: Match.stringLikeRegexp("LOG_USER_ID_HASH_KEY"),
    });
  });

  it("prod'da silme koruması açıktır", () => {
    synth("prod").hasResourceProperties("AWS::RDS::DBInstance", { DeletionProtection: true });
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/database.test.ts`
Expected: FAIL — modül yok

- [ ] **Step 3: `database.ts`'yi yaz**

```ts
import { Duration, RemovalPolicy } from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as rds from "aws-cdk-lib/aws-rds";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/** RDS PostgreSQL 17 (mimari.md §17) ve uygulamanın ihtiyaç duyduğu sırlar (kurallar.md §9). */
export class Database extends Construct {
  readonly instance: rds.DatabaseInstance;
  readonly secret: secretsmanager.ISecret;
  readonly logHashKey: secretsmanager.Secret;

  constructor(scope: Construct, id: string, props: { config: EnvConfig; vpc: ec2.IVpc }) {
    super(scope, id);
    const { config } = props;
    this.instance = new rds.DatabaseInstance(this, "Postgres", {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_17 }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.SMALL),
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      multiAz: false,
      allocatedStorage: config.database.allocatedStorageGiB,
      storageEncrypted: true,
      databaseName: "soruhavuzu",
      credentials: rds.Credentials.fromGeneratedSecret("soruhavuzu"),
      backupRetention: Duration.days(config.database.backupRetentionDays),
      deletionProtection: config.deletionProtection,
      removalPolicy: config.deletionProtection ? RemovalPolicy.SNAPSHOT : RemovalPolicy.DESTROY,
      publiclyAccessible: false,
    });
    if (!this.instance.secret) throw new Error("RDS sırrı üretilmedi");
    this.secret = this.instance.secret;
    this.logHashKey = new secretsmanager.Secret(this, "LogUserIdHashKey", {
      description: "LOG_USER_ID_HASH_KEY: loglardaki userId HMAC anahtarı (mimari.md §18)",
      generateSecretString: { passwordLength: 48, excludePunctuation: true },
    });
  }

  allowFrom(peer: ec2.IConnectable): void {
    this.instance.connections.allowDefaultPortFrom(peer, "API görevleri");
  }
}
```

- [ ] **Step 4: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/database.test.ts`
Expected: 4 PASS

- [ ] **Step 5: Commit**

```bash
git add infra/lib/constructs/database.ts infra/test/database.test.ts
git commit -m "feat(infra): RDS PostgreSQL 17 ve uygulama sırları"
```

---

### Task 4: Medya (S3, SQS, CDN)

**Files:**
- Create: `infra/lib/constructs/media.ts`, `infra/test/media.test.ts`

**Interfaces:**
- Consumes: `EnvConfig`
- Produces: `class Media extends Construct { readonly bucket: s3.Bucket; readonly mediaIn: sqs.Queue; readonly mediaResults: sqs.Queue; readonly cdn: cloudfront.Distribution }`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { loadConfig } from "../lib/config.ts";
import { Media } from "../lib/constructs/media.ts";

function synth() {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  new Media(stack, "Media", { config: loadConfig("staging") });
  return Template.fromStack(stack);
}

describe("Media", () => {
  it("bucket soruhavuzu-media-<env>, versioning açık, tamamen private", () => {
    synth().hasResourceProperties("AWS::S3::Bucket", {
      BucketName: "soruhavuzu-media-staging",
      VersioningConfiguration: { Status: "Enabled" },
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true, BlockPublicPolicy: true, IgnorePublicAcls: true, RestrictPublicBuckets: true,
      },
    });
  });

  it("uploads/ öneki 1 günde silinir (mimari.md §13)", () => {
    synth().hasResourceProperties("AWS::S3::Bucket", {
      LifecycleConfiguration: {
        Rules: Match.arrayWith([Match.objectLike({ Prefix: "uploads/", ExpirationInDays: 1, Status: "Enabled" })]),
      },
    });
  });

  it("media-in ve media-results DLQ'lu kuyruklardır", () => {
    const template = synth();
    template.resourceCountIs("AWS::SQS::Queue", 4);
    template.hasResourceProperties("AWS::SQS::Queue", {
      QueueName: "soruhavuzu-staging-media-in",
      RedrivePolicy: Match.objectLike({ maxReceiveCount: 3 }),
    });
  });

  it("uploads/ olayları media-in kuyruğuna gider", () => {
    synth().hasResourceProperties("Custom::S3BucketNotifications", {
      NotificationConfiguration: {
        QueueConfigurations: [Match.objectLike({ Events: ["s3:ObjectCreated:*"], Filter: Match.anyValue() })],
      },
    });
  });

  it("CDN yalnızca public/ önekini OAC ile ve HTTPS'e yönlendirerek sunar", () => {
    const template = synth();
    template.resourceCountIs("AWS::CloudFront::OriginAccessControl", 1);
    template.hasResourceProperties("AWS::CloudFront::Distribution", {
      DistributionConfig: Match.objectLike({
        DefaultCacheBehavior: Match.objectLike({ ViewerProtocolPolicy: "redirect-to-https" }),
        Origins: [Match.objectLike({ OriginPath: "/public" })],
      }),
    });
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/media.test.ts`
Expected: FAIL — modül yok

- [ ] **Step 3: `media.ts`'yi yaz**

```ts
import { Duration, RemovalPolicy } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3n from "aws-cdk-lib/aws-s3-notifications";
import * as sqs from "aws-cdk-lib/aws-sqs";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/** Medya yükleme akışının kaynakları (mimari.md §13, ADR 0010). Lambda Faz 1'de eklenir. */
export class Media extends Construct {
  readonly bucket: s3.Bucket;
  readonly mediaIn: sqs.Queue;
  readonly mediaResults: sqs.Queue;
  readonly cdn: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: { config: EnvConfig }) {
    super(scope, id);
    const env = props.config.name;
    this.bucket = new s3.Bucket(this, "Bucket", {
      bucketName: `soruhavuzu-media-${env}`,
      versioned: true,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      lifecycleRules: [{ prefix: "uploads/", expiration: Duration.days(1) }],
      removalPolicy: props.config.deletionProtection ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY,
      autoDeleteObjects: !props.config.deletionProtection,
    });
    this.mediaIn = queueWithDlq(this, `soruhavuzu-${env}-media-in`, Duration.seconds(60), 3);
    this.mediaResults = queueWithDlq(this, `soruhavuzu-${env}-media-results`, Duration.seconds(30), 5);
    this.bucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(this.mediaIn),
      { prefix: "uploads/" },
    );
    this.cdn = new cloudfront.Distribution(this, "Cdn", {
      comment: `soruhavuzu-${env} medya (public/)`,
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.bucket, { originPath: "/public" }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      },
    });
  }
}

function queueWithDlq(scope: Construct, name: string, visibility: Duration, maxReceiveCount: number): sqs.Queue {
  const dlq = new sqs.Queue(scope, `${name}-dlq`, {
    queueName: `${name}-dlq`,
    retentionPeriod: Duration.days(14),
    enforceSSL: true,
  });
  return new sqs.Queue(scope, name, {
    queueName: name,
    visibilityTimeout: visibility,
    deadLetterQueue: { queue: dlq, maxReceiveCount },
    enforceSSL: true,
  });
}
```

- [ ] **Step 4: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/media.test.ts`
Expected: 5 PASS

- [ ] **Step 5: Commit**

```bash
git add infra/lib/constructs/media.ts infra/test/media.test.ts
git commit -m "feat(infra): medya bucket'ı, kuyruklar ve CDN"
```

---

### Task 5: Kimlik (Cognito Lite)

**Files:**
- Create: `infra/lib/constructs/auth.ts`, `infra/test/auth.test.ts`

**Interfaces:**
- Consumes: `EnvConfig`
- Produces: `class Auth extends Construct { readonly userPool: cognito.UserPool; readonly mobileClient: cognito.UserPoolClient; readonly adminClient: cognito.UserPoolClient; readonly domainUrl: string; readonly issuerUrl: string }`
  `new Auth(scope, "Auth", { config, adminCallbackUrl: string })`. `adminCallbackUrl` örneği: `https://dxxxx.cloudfront.net/auth/callback`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { loadConfig } from "../lib/config.ts";
import { Auth } from "../lib/constructs/auth.ts";

function synth() {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  new Auth(stack, "Auth", { config: loadConfig("staging"), adminCallbackUrl: "https://admin.example/auth/callback" });
  return Template.fromStack(stack);
}

describe("Auth", () => {
  it("Lite planlı, e-posta ile kayıt olunan kullanıcı havuzu (ADR 0009)", () => {
    synth().hasResourceProperties("AWS::Cognito::UserPool", {
      UserPoolTier: "LITE",
      UsernameAttributes: ["email"],
      AutoVerifiedAttributes: ["email"],
    });
  });

  it("mobil ve admin istemcileri gizli anahtarsız, yalnızca authorization code akışlıdır", () => {
    const template = synth();
    template.resourceCountIs("AWS::Cognito::UserPoolClient", 2);
    template.allResourcesProperties("AWS::Cognito::UserPoolClient", {
      GenerateSecret: false,
      AllowedOAuthFlows: ["code"],
      AllowedOAuthScopes: Match.arrayWith(["openid", "email"]),
    });
    template.hasResourceProperties("AWS::Cognito::UserPoolClient", {
      CallbackURLs: ["soruhavuzu://auth/callback"],
    });
  });

  it("managed login alan adı ortam önekini kullanır", () => {
    synth().hasResourceProperties("AWS::Cognito::UserPoolDomain", {
      Domain: "soruhavuzu-staging",
      ManagedLoginVersion: 2,
    });
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/auth.test.ts`
Expected: FAIL — modül yok

- [ ] **Step 3: `auth.ts`'yi yaz**

```ts
import { Duration, RemovalPolicy, Stack } from "aws-cdk-lib";
import * as cognito from "aws-cdk-lib/aws-cognito";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/**
 * Cognito Lite (ADR 0009, 0018). Backend access token kabul eder; `client_id` izin listesi bu iki istemcidir.
 * Apple ve Google sağlayıcıları, kimlik bilgileri Secrets Manager'a girildikten sonra ayrı bir değişiklikle eklenir.
 */
export class Auth extends Construct {
  readonly userPool: cognito.UserPool;
  readonly mobileClient: cognito.UserPoolClient;
  readonly adminClient: cognito.UserPoolClient;
  readonly domainUrl: string;
  readonly issuerUrl: string;

  constructor(scope: Construct, id: string, props: { config: EnvConfig; adminCallbackUrl: string }) {
    super(scope, id);
    this.userPool = new cognito.UserPool(this, "UserPool", {
      featurePlan: cognito.FeaturePlan.LITE,
      selfSignUpEnabled: true,
      signInAliases: { email: true },
      autoVerify: { email: true },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      removalPolicy: props.config.deletionProtection ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY,
    });
    const domain = this.userPool.addDomain("Domain", {
      cognitoDomain: { domainPrefix: props.config.cognitoDomainPrefix },
      managedLoginVersion: cognito.ManagedLoginVersion.NEWER_MANAGED_LOGIN,
    });
    this.domainUrl = domain.baseUrl();
    this.issuerUrl = `https://cognito-idp.${Stack.of(this).region}.amazonaws.com/${this.userPool.userPoolId}`;

    const client = (name: string, callbackUrl: string) =>
      this.userPool.addClient(name, {
        generateSecret: false,
        authFlows: {},
        oAuth: {
          flows: { authorizationCodeGrant: true },
          scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL],
          callbackUrls: [callbackUrl],
        },
        accessTokenValidity: Duration.hours(1),
        refreshTokenValidity: Duration.days(30),
        preventUserExistenceErrors: true,
      });
    this.mobileClient = client("MobileClient", "soruhavuzu://auth/callback");
    this.adminClient = client("AdminClient", props.adminCallbackUrl);
  }
}
```

- [ ] **Step 4: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/auth.test.ts`
Expected: 3 PASS. `UserPoolTier` alanı sentezde farklı adla çıkarsa: `pnpm cdk synth` çıktısında `AWS::Cognito::UserPool` özelliklerine bakıp testteki özellik adını sentezin ürettiği adla eşitle; davranış (Lite) değişmez.

- [ ] **Step 5: Commit**

```bash
git add infra/lib/constructs/auth.ts infra/test/auth.test.ts
git commit -m "feat(infra): Cognito Lite kullanıcı havuzu, mobil ve admin istemcileri"
```

---

### Task 6: Admin web barındırma ve ECR deposu

**Files:**
- Create: `infra/lib/constructs/admin-web.ts`, `infra/lib/constructs/registry.ts`, `infra/test/admin-web.test.ts`

**Interfaces:**
- Produces: `class AdminWeb extends Construct { readonly bucket: s3.Bucket; readonly distribution: cloudfront.Distribution; readonly url: string }` — `url` = `https://${distribution.domainName}`
- Produces: `class Registry extends Construct { readonly repository: ecr.Repository }` — repo adı `soruhavuzu-api-<env>`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { loadConfig } from "../lib/config.ts";
import { AdminWeb } from "../lib/constructs/admin-web.ts";
import { Registry } from "../lib/constructs/registry.ts";

function synth() {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  const config = loadConfig("staging");
  new AdminWeb(stack, "AdminWeb", { config });
  new Registry(stack, "Registry", { config });
  return Template.fromStack(stack);
}

describe("AdminWeb", () => {
  it("SPA: 403/404 index.html'e döner, HTTPS zorunlu, güvenlik başlıkları açık", () => {
    synth().hasResourceProperties("AWS::CloudFront::Distribution", {
      DistributionConfig: Match.objectLike({
        DefaultRootObject: "index.html",
        CustomErrorResponses: Match.arrayWith([Match.objectLike({ ErrorCode: 403, ResponsePagePath: "/index.html" })]),
        DefaultCacheBehavior: Match.objectLike({
          ViewerProtocolPolicy: "redirect-to-https",
          ResponseHeadersPolicyId: Match.anyValue(),
        }),
      }),
    });
  });
});

describe("Registry", () => {
  it("ECR deposu tarama açık, etiketler değiştirilemez, en fazla 30 image tutar", () => {
    synth().hasResourceProperties("AWS::ECR::Repository", {
      RepositoryName: "soruhavuzu-api-staging",
      ImageScanningConfiguration: { ScanOnPush: true },
      ImageTagMutability: "IMMUTABLE",
      LifecyclePolicy: Match.objectLike({ LifecyclePolicyText: Match.stringLikeRegexp("30") }),
    });
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/admin-web.test.ts`
Expected: FAIL — modüller yok

- [ ] **Step 3: `admin-web.ts` ve `registry.ts`'yi yaz**

`admin-web.ts`:
```ts
import { Duration, RemovalPolicy } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as s3 from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/** Admin paneli (Vite çıktısı): private S3 + CloudFront OAC, SPA yönlendirmesi (ADR 0023). */
export class AdminWeb extends Construct {
  readonly bucket: s3.Bucket;
  readonly distribution: cloudfront.Distribution;
  readonly url: string;

  constructor(scope: Construct, id: string, props: { config: EnvConfig }) {
    super(scope, id);
    this.bucket = new s3.Bucket(this, "Bucket", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
    const spaFallback = (code: number): cloudfront.ErrorResponse => ({
      httpStatus: code,
      responseHttpStatus: 200,
      responsePagePath: "/index.html",
      ttl: Duration.seconds(0),
    });
    this.distribution = new cloudfront.Distribution(this, "Distribution", {
      comment: `soruhavuzu-${props.config.name} admin`,
      defaultRootObject: "index.html",
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.bucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
      },
      errorResponses: [spaFallback(403), spaFallback(404)],
    });
    this.url = `https://${this.distribution.distributionDomainName}`;
  }
}
```

`registry.ts`:
```ts
import { RemovalPolicy } from "aws-cdk-lib";
import * as ecr from "aws-cdk-lib/aws-ecr";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";

/** API image'ları; etiket = git commit SHA, değiştirilemez. */
export class Registry extends Construct {
  readonly repository: ecr.Repository;

  constructor(scope: Construct, id: string, props: { config: EnvConfig }) {
    super(scope, id);
    this.repository = new ecr.Repository(this, "Repository", {
      repositoryName: `soruhavuzu-api-${props.config.name}`,
      imageScanOnPush: true,
      imageTagMutability: ecr.TagMutability.IMMUTABLE,
      lifecycleRules: [{ maxImageCount: 30 }],
      removalPolicy: props.config.deletionProtection ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY,
      emptyOnDelete: !props.config.deletionProtection,
    });
  }
}
```

- [ ] **Step 4: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/admin-web.test.ts`
Expected: 2 PASS

- [ ] **Step 5: Commit**

```bash
git add infra/lib/constructs/admin-web.ts infra/lib/constructs/registry.ts infra/test/admin-web.test.ts
git commit -m "feat(infra): admin web barındırma ve ECR deposu"
```

---

### Task 7: Backend konteyneri ve staging/prod profilleri

**Files:**
- Create: `backend/Dockerfile`, `backend/.dockerignore`, `backend/src/main/resources/application-staging.yml`
- Modify: `backend/src/main/resources/application-prod.yml` (staging ile aynı bağlantı ayarları + mevcut springdoc kapatma), `Makefile` (yeni `docker-smoke` hedefi)
- Test: `make docker-smoke` (image'ı build edip compose Postgres'e karşı çalıştırır, `/actuator/health` = UP)

**Interfaces:**
- Produces: Image çalışma sözleşmesi (Task 8 bunu kullanır). Ortam değişkenleri:
  - Veritabanı: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
  - Kimlik: `COGNITO_ISSUER_URI`, `COGNITO_CLIENT_IDS`
  - Log ve CORS: `LOG_USER_ID_HASH_KEY`, `ALLOWED_ORIGINS`
  - Çalışma: `SPRING_PROFILES_ACTIVE`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`
  - Port 8080, sağlık `GET /actuator/health`

- [ ] **Step 1: Başarısız "testi" çalıştır**

Run: `make docker-smoke`
Expected: FAIL — `make: *** No rule to make target 'docker-smoke'`

- [ ] **Step 2: `backend/.dockerignore` ve `backend/Dockerfile`'ı yaz**

`.dockerignore`:
```
*
!build/libs/soruhavuzu-api.jar
!build/otel/opentelemetry-javaagent.jar
```

`Dockerfile`:
```dockerfile
# Jar ve OTel agent'ı CI'da Gradle üretir (bootJar + otelAgentJar); image yalnızca çalışma ortamıdır.
FROM eclipse-temurin:25.0.4.1_1-jre-noble

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --chown=app:app build/libs/soruhavuzu-api.jar app.jar
COPY --chown=app:app build/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
USER app

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    OTEL_SERVICE_NAME=soruhavuzu-api \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Jar adını sabitle — `backend/build.gradle.kts` sonuna ekle**

```kotlin
tasks.bootJar {
    archiveFileName = "soruhavuzu-api.jar"
}
```

- [ ] **Step 4: Profil dosyalarını yaz**

`application-staging.yml`:
```yaml
# ECS görev tanımı değerleri Secrets Manager ve CDK çıktılarından ortam değişkeni olarak verir (ADR 0023)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

soruhavuzu:
  web:
    allowed-origins: ${ALLOWED_ORIGINS}
```

`application-prod.yml` (tam içerik):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

soruhavuzu:
  web:
    allowed-origins: ${ALLOWED_ORIGINS}

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

- [ ] **Step 5: `Makefile`'a `docker-smoke` hedefini ekle (`.PHONY` satırına da `docker-smoke` ekle)**

```make
docker-smoke: infra ## Backend image'ını build edip lokal Postgres'e karşı staging profiliyle sağlık kontrolü yapar
	$(GRADLE) bootJar otelAgentJar
	docker build -t soruhavuzu-api:smoke backend
	docker rm -f soruhavuzu-api-smoke >/dev/null 2>&1 || true
	docker run -d --name soruhavuzu-api-smoke --network soruhavuzu_default -p 18080:8080 \
	  -e SPRING_PROFILES_ACTIVE=staging \
	  -e DB_HOST=postgres -e DB_PORT=5432 -e DB_NAME=soruhavuzu -e DB_USERNAME=soruhavuzu -e DB_PASSWORD=soruhavuzu \
	  -e COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_smoke \
	  -e COGNITO_CLIENT_IDS=smoke -e LOG_USER_ID_HASH_KEY=smoke-anahtari -e ALLOWED_ORIGINS=https://admin.smoke \
	  -e OTEL_TRACES_EXPORTER=none soruhavuzu-api:smoke
	curl -sf --retry 60 --retry-delay 2 --retry-all-errors --retry-connrefused http://localhost:18080/actuator/health | grep -q '"UP"'
	docker logs soruhavuzu-api-smoke 2>&1 | grep -q '"level":"INFO"'
	docker rm -f soruhavuzu-api-smoke >/dev/null
	@echo "✓ image sağlıklı ve JSON log yazıyor"
```

- [ ] **Step 6: Çalıştır, geçtiğini gör**

Run: `make docker-smoke`
Expected: `✓ image sağlıklı ve JSON log yazıyor`. Ayrıca `docker run --rm --entrypoint id soruhavuzu-api:smoke` çıktısında `uid=` değeri 0 OLMAMALI.

- [ ] **Step 7: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore backend/build.gradle.kts backend/src/main/resources/application-staging.yml backend/src/main/resources/application-prod.yml Makefile
git commit -m "feat(shared): backend konteyneri, staging/prod profilleri ve docker-smoke"
```

---

### Task 8: API servisi (ECS Fargate + iç ALB + CloudFront)

**Files:**
- Create: `infra/lib/constructs/api-service.ts`, `infra/test/api-service.test.ts`

**Interfaces:**
- Consumes: `Network.vpc`, `Database` (`secret`, `logHashKey`, `allowFrom`), `Registry.repository`, `Auth` (`issuerUrl`, `mobileClient`, `adminClient`), `Media` (`bucket`, `mediaIn`, `mediaResults`), `AdminWeb.url`, `EnvConfig`
- Produces: `class ApiService extends Construct { readonly url: string; readonly service: ecs.FargateService }`
  `new ApiService(scope, "Api", { config, vpc, database, repository, auth, media, adminUrl, imageTag })`

- [ ] **Step 1: Başarısız testi yaz**

```ts
import { App, Stack } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { loadConfig } from "../lib/config.ts";
import { AdminWeb } from "../lib/constructs/admin-web.ts";
import { ApiService } from "../lib/constructs/api-service.ts";
import { Auth } from "../lib/constructs/auth.ts";
import { Database } from "../lib/constructs/database.ts";
import { Media } from "../lib/constructs/media.ts";
import { Network } from "../lib/constructs/network.ts";
import { Registry } from "../lib/constructs/registry.ts";

function synth() {
  const stack = new Stack(new App(), "T", { env: { account: "111111111111", region: "eu-central-1" } });
  const config = loadConfig("staging");
  const network = new Network(stack, "Network", { config });
  const database = new Database(stack, "Database", { config, vpc: network.vpc });
  const admin = new AdminWeb(stack, "AdminWeb", { config });
  const auth = new Auth(stack, "Auth", { config, adminCallbackUrl: `${admin.url}/auth/callback` });
  new ApiService(stack, "Api", {
    config, vpc: network.vpc, database, auth,
    repository: new Registry(stack, "Registry", { config }).repository,
    media: new Media(stack, "Media", { config }),
    adminUrl: admin.url, imageTag: "abc1234",
  });
  return Template.fromStack(stack);
}

describe("ApiService", () => {
  it("görev 0.5 vCPU / 1 GB, uygulama + ADOT sidecar", () => {
    synth().hasResourceProperties("AWS::ECS::TaskDefinition", {
      Cpu: "512",
      Memory: "1024",
      ContainerDefinitions: Match.arrayWith([
        Match.objectLike({ Name: "api", Image: Match.anyValue() }),
        Match.objectLike({ Name: "otel-collector" }),
      ]),
    });
  });

  it("sırlar düz ortam değişkeni değil, Secrets Manager referansıdır", () => {
    const secret = (name: string) => Match.objectLike({ Name: name, ValueFrom: Match.anyValue() });
    synth().hasResourceProperties("AWS::ECS::TaskDefinition", {
      ContainerDefinitions: Match.arrayWith([
        Match.objectLike({
          Name: "api",
          Secrets: Match.arrayWith(
            ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "LOG_USER_ID_HASH_KEY"].map(secret),
          ),
          Environment: Match.not(Match.arrayWith([Match.objectLike({ Name: "DB_PASSWORD" })])),
        }),
      ]),
    });
  });

  it("servis 1–3 görev arasında ölçeklenir, public IP ile çıkar", () => {
    const template = synth();
    template.hasResourceProperties("AWS::ECS::Service", {
      DesiredCount: 1,
      NetworkConfiguration: { AwsvpcConfiguration: Match.objectLike({ AssignPublicIp: "ENABLED" }) },
    });
    template.hasResourceProperties("AWS::ApplicationAutoScaling::ScalableTarget", { MinCapacity: 1, MaxCapacity: 3 });
  });

  it("ALB internete kapalıdır, CloudFront'a VPC origin ile bağlanır", () => {
    const template = synth();
    template.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", { Scheme: "internal" });
    template.resourceCountIs("AWS::CloudFront::VpcOrigin", 1);
    template.hasResourceProperties("AWS::CloudFront::Distribution", {
      DistributionConfig: Match.objectLike({
        DefaultCacheBehavior: Match.objectLike({ ViewerProtocolPolicy: "https-only" }),
      }),
    });
  });

  it("hedef grup /actuator/health ile sağlık kontrolü yapar", () => {
    synth().hasResourceProperties("AWS::ElasticLoadBalancingV2::TargetGroup", {
      HealthCheckPath: "/actuator/health",
      Port: 8080,
    });
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/api-service.test.ts`
Expected: FAIL — modül yok

- [ ] **Step 3: `api-service.ts`'yi yaz**

```ts
import { Duration } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import type * as ecr from "aws-cdk-lib/aws-ecr";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as elbv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as iam from "aws-cdk-lib/aws-iam";
import * as logs from "aws-cdk-lib/aws-logs";
import { Construct } from "constructs";

import type { EnvConfig } from "../config.ts";
import type { Auth } from "./auth.ts";
import type { Database } from "./database.ts";
import type { Media } from "./media.ts";

interface ApiServiceProps {
  config: EnvConfig;
  vpc: ec2.IVpc;
  database: Database;
  repository: ecr.IRepository;
  auth: Auth;
  media: Media;
  adminUrl: string;
  imageTag: string;
}

/** soruhavuzu-api: Fargate (public IP, gelen trafik yalnızca ALB'den) + iç ALB + CloudFront VPC origin (ADR 0023). */
export class ApiService extends Construct {
  readonly url: string;
  readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: ApiServiceProps) {
    super(scope, id);
    const { config, database } = props;
    const cluster = new ecs.Cluster(this, "Cluster", { vpc: props.vpc, containerInsightsV2: ecs.ContainerInsights.ENABLED });

    const task = new ecs.FargateTaskDefinition(this, "Task", {
      cpu: config.api.cpu,
      memoryLimitMiB: config.api.memoryMiB,
      runtimePlatform: { cpuArchitecture: ecs.CpuArchitecture.ARM64, operatingSystemFamily: ecs.OperatingSystemFamily.LINUX },
    });
    const logGroup = new logs.LogGroup(this, "Logs", { retention: logs.RetentionDays.ONE_MONTH });
    const fromDb = (field: string) => ecs.Secret.fromSecretsManager(database.secret, field);

    task.addContainer("api", {
      image: ecs.ContainerImage.fromEcrRepository(props.repository, props.imageTag),
      portMappings: [{ containerPort: 8080 }],
      logging: ecs.LogDrivers.awsLogs({ logGroup, streamPrefix: "api" }),
      environment: {
        SPRING_PROFILES_ACTIVE: config.name,
        COGNITO_ISSUER_URI: props.auth.issuerUrl,
        COGNITO_CLIENT_IDS: [props.auth.mobileClient.userPoolClientId, props.auth.adminClient.userPoolClientId].join(","),
        ALLOWED_ORIGINS: props.adminUrl,
        OTEL_EXPORTER_OTLP_ENDPOINT: "http://localhost:4317",
        OTEL_EXPORTER_OTLP_PROTOCOL: "grpc",
      },
      secrets: {
        DB_HOST: fromDb("host"),
        DB_PORT: fromDb("port"),
        DB_NAME: fromDb("dbname"),
        DB_USERNAME: fromDb("username"),
        DB_PASSWORD: fromDb("password"),
        LOG_USER_ID_HASH_KEY: ecs.Secret.fromSecretsManager(database.logHashKey),
      },
    });
    task.addContainer("otel-collector", {
      image: ecs.ContainerImage.fromRegistry("public.ecr.aws/aws-observability/aws-otel-collector:v0.45.1"),
      command: ["--config=/etc/ecs/ecs-default-config.yaml"],
      essential: false,
      logging: ecs.LogDrivers.awsLogs({ logGroup, streamPrefix: "otel" }),
    });
    task.taskRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"));
    props.media.bucket.grantReadWrite(task.taskRole);
    props.media.mediaResults.grantConsumeMessages(task.taskRole);

    const alb = new elbv2.ApplicationLoadBalancer(this, "Alb", {
      vpc: props.vpc,
      internetFacing: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
    });
    this.service = new ecs.FargateService(this, "Service", {
      cluster,
      taskDefinition: task,
      desiredCount: config.api.minTasks,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      circuitBreaker: { enable: true, rollback: true },
      minHealthyPercent: 100,
    });
    database.allowFrom(this.service);
    const listener = alb.addListener("Http", { port: 80, open: false });
    listener.addTargets("Api", {
      port: 8080,
      targets: [this.service],
      healthCheck: { path: "/actuator/health", healthyHttpCodes: "200", interval: Duration.seconds(15) },
      deregistrationDelay: Duration.seconds(20),
    });
    const scaling = this.service.autoScaleTaskCount({ minCapacity: config.api.minTasks, maxCapacity: config.api.maxTasks });
    scaling.scaleOnCpuUtilization("Cpu", { targetUtilizationPercent: 60 });

    const distribution = new cloudfront.Distribution(this, "Cdn", {
      comment: `soruhavuzu-${config.name} api`,
      defaultBehavior: {
        origin: origins.VpcOrigin.withApplicationLoadBalancer(alb, { protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
        originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
      },
    });
    // VPC origin, ALB güvenlik grubuna CloudFront'un yönetilen önek listesinden erişim ister
    alb.connections.allowFrom(
      ec2.Peer.prefixList(this.cloudFrontPrefixListId()),
      ec2.Port.tcp(80),
      "CloudFront VPC origin",
    );
    this.url = `https://${distribution.distributionDomainName}`;
  }

  /** CloudFront'un AWS tarafından yönetilen "origin-facing" önek listesi; sentezde hesap bağlamıyla aranır. */
  private cloudFrontPrefixListId(): string {
    return ec2.PrefixList.fromLookup(this, "CloudFrontOrigins", {
      prefixListName: "com.amazonaws.global.cloudfront.origin-facing",
    }).prefixListId;
  }
}
```

`fromLookup` sentezde hesap/bölge bağlamı ister. Testlerde yığına sabit `env` verildiği için CDK sahte bir değer üretir ve uyarı yazar; bu kabul edilir. Gerçek değer ilk `cdk deploy`'da `cdk.context.json`'a yazılır ve commit'lenir (Task 12).

- [ ] **Step 4: ADOT collector image sürümünü doğrula**

Run: `curl -s "https://public.ecr.aws/v2/aws-observability/aws-otel-collector/tags/list" | python3 -m json.tool | grep -E '"v0\.' | tail -5`
Expected: `v0.45.1` listede. Değilse, en az 14 günlük en yeni `v0.x.y` etiketini kullan ve hem koda hem `docs/adr/0023-aws-topology.md` sonuçlarına yaz.

- [ ] **Step 5: Çalıştır, geçtiğini gör**

Run: `cd infra && pnpm test -- test/api-service.test.ts`
Expected: 5 PASS

- [ ] **Step 6: Commit**

```bash
git add infra/lib/constructs/api-service.ts infra/test/api-service.test.ts
git commit -m "feat(infra): ECS Fargate API servisi, iç ALB ve CloudFront VPC origin"
```

---

### Task 9: Yığınlar, GitHub OIDC rolü ve uygulama girişi

**Files:**
- Create: `infra/lib/platform-stack.ts`, `infra/lib/api-stack.ts`, `infra/lib/ci-stack.ts`, `infra/lib/constructs/github-oidc.ts`, `infra/bin/app.ts`, `infra/test/ci.test.ts`, `infra/test/app.test.ts`

**Interfaces:**
- Produces: `class PlatformStack extends Stack` — public alanlar: `network`, `database`, `media`, `auth`, `registry`, `adminWeb`
- Produces: `class ApiStack extends Stack { readonly apiUrl: string }` — `props.platform: PlatformStack`, `props.imageTag: string`
- Produces: `class CiStack extends Stack` — `props.repository: string`, ör. `"kullanici/soruhavuzu"`
- Produces: `function buildApp(app: App): { platforms: Record<EnvName, PlatformStack>; apis: Record<EnvName, ApiStack>; ci: CiStack }`
- Bağlam parametreleri: `-c imageTag=<sha>` (varsayılan `bootstrap`), `-c githubRepo=<owner/repo>` (varsayılan `soruhavuzu/soruhavuzu`)
- CfnOutput adları (CI bunları okur): `ApiUrl`, `AdminUrl`, `AdminBucket`, `AdminDistributionId`, `CognitoDomain`, `CognitoAdminClientId`, `CognitoMobileClientId`, `CognitoIssuer`, `MediaCdnUrl`, `EcrRepositoryUri`

- [ ] **Step 1: Başarısız testleri yaz**

`infra/test/ci.test.ts`:
```ts
import { App } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { describe, it } from "vitest";

import { CiStack } from "../lib/ci-stack.ts";

describe("CiStack", () => {
  it("GitHub OIDC rolü yalnızca belirtilen reponun main dalı, v* etiketleri ve ortamlarına güvenir", () => {
    const stack = new CiStack(new App(), "Ci", {
      env: { account: "111111111111", region: "eu-central-1" },
      repository: "ornek/soruhavuzu",
    });
    Template.fromStack(stack).hasResourceProperties("AWS::IAM::Role", {
      AssumeRolePolicyDocument: Match.objectLike({
        Statement: [
          Match.objectLike({
            Condition: Match.objectLike({
              StringEquals: { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
              StringLike: {
                "token.actions.githubusercontent.com:sub": [
                  "repo:ornek/soruhavuzu:ref:refs/heads/main",
                  "repo:ornek/soruhavuzu:ref:refs/tags/v*",
                  "repo:ornek/soruhavuzu:environment:staging",
                  "repo:ornek/soruhavuzu:environment:production",
                ],
              },
            }),
          }),
        ],
      }),
    });
  });
});
```

`infra/test/app.test.ts`:
```ts
import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";

import { buildApp } from "../bin/app.ts";

describe("CDK uygulaması", () => {
  const app = new App({ context: { imageTag: "abc1234", githubRepo: "ornek/soruhavuzu" } });
  const built = buildApp(app, { account: "111111111111" });

  it("her ortam için platform ve api yığını, hesap için bir ci yığını", () => {
    expect(Object.keys(built.platforms).sort()).toEqual(["prod", "staging"]);
    expect(built.apis.staging.stackName).toBe("soruhavuzu-staging-api");
    expect(built.platforms.prod.stackName).toBe("soruhavuzu-prod-platform");
    expect(built.ci.stackName).toBe("soruhavuzu-ci");
  });

  it("CI'ın okuduğu çıktılar mevcuttur", () => {
    const outputs = {
      ...Template.fromStack(built.platforms.staging).findOutputs("*"),
      ...Template.fromStack(built.apis.staging).findOutputs("*"),
    };
    for (const name of [
      "ApiUrl", "AdminUrl", "AdminBucket", "AdminDistributionId", "CognitoDomain",
      "CognitoAdminClientId", "CognitoMobileClientId", "CognitoIssuer", "MediaCdnUrl", "EcrRepositoryUri",
    ]) {
      expect(Object.keys(outputs)).toContain(name);
    }
  });
});
```

- [ ] **Step 2: Çalıştır, kırıldığını gör**

Run: `cd infra && pnpm test -- test/ci.test.ts test/app.test.ts`
Expected: FAIL — modüller yok

- [ ] **Step 3: `github-oidc.ts` ve `ci-stack.ts`'yi yaz**

`github-oidc.ts`:
```ts
import * as iam from "aws-cdk-lib/aws-iam";
import { Construct } from "constructs";

/** GitHub Actions'ın uzun ömürlü anahtar olmadan AWS'e erişmesi (kurallar.md §9: sırlar repoda yok). */
export class GitHubOidc extends Construct {
  readonly deployRole: iam.Role;

  constructor(scope: Construct, id: string, props: { repository: string }) {
    super(scope, id);
    const provider = new iam.OpenIdConnectProvider(this, "Provider", {
      url: "https://token.actions.githubusercontent.com",
      clientIds: ["sts.amazonaws.com"],
    });
    const repo = props.repository;
    this.deployRole = new iam.Role(this, "DeployRole", {
      roleName: "soruhavuzu-github-deploy",
      maxSessionDuration: undefined,
      assumedBy: new iam.WebIdentityPrincipal(provider.openIdConnectProviderArn, {
        StringEquals: { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
        StringLike: {
          "token.actions.githubusercontent.com:sub": [
            `repo:${repo}:ref:refs/heads/main`,
            `repo:${repo}:ref:refs/tags/v*`,
            `repo:${repo}:environment:staging`,
            `repo:${repo}:environment:production`,
          ],
        },
      }),
    });
    // CDK deploy, bootstrap rollerini üstlenerek yapılır; doğrudan geniş yetki verilmez
    this.deployRole.addToPolicy(
      new iam.PolicyStatement({
        actions: ["sts:AssumeRole"],
        resources: ["arn:aws:iam::*:role/cdk-hnb659fds-*"],
      }),
    );
  }
}
```

`ci-stack.ts`:
```ts
import { CfnOutput, Stack, type StackProps } from "aws-cdk-lib";
import type { Construct } from "constructs";

import { GitHubOidc } from "./constructs/github-oidc.ts";

export class CiStack extends Stack {
  constructor(scope: Construct, id: string, props: StackProps & { repository: string }) {
    super(scope, id, props);
    const oidc = new GitHubOidc(this, "GitHub", { repository: props.repository });
    new CfnOutput(this, "DeployRoleArn", { value: oidc.deployRole.roleArn });
  }
}
```

- [ ] **Step 4: `platform-stack.ts` ve `api-stack.ts`'yi yaz**

`platform-stack.ts`:
```ts
import { CfnOutput, Stack, type StackProps } from "aws-cdk-lib";
import type { Construct } from "constructs";

import type { EnvConfig } from "./config.ts";
import { AdminWeb } from "./constructs/admin-web.ts";
import { Auth } from "./constructs/auth.ts";
import { Database } from "./constructs/database.ts";
import { Media } from "./constructs/media.ts";
import { Network } from "./constructs/network.ts";
import { Registry } from "./constructs/registry.ts";

/** Uzun ömürlü kaynaklar; image güncellemesi bu yığına dokunmaz (ADR 0023). */
export class PlatformStack extends Stack {
  readonly network: Network;
  readonly database: Database;
  readonly media: Media;
  readonly auth: Auth;
  readonly registry: Registry;
  readonly adminWeb: AdminWeb;

  constructor(scope: Construct, id: string, props: StackProps & { config: EnvConfig }) {
    super(scope, id, props);
    const { config } = props;
    this.network = new Network(this, "Network", { config });
    this.database = new Database(this, "Database", { config, vpc: this.network.vpc });
    this.media = new Media(this, "Media", { config });
    this.registry = new Registry(this, "Registry", { config });
    this.adminWeb = new AdminWeb(this, "AdminWeb", { config });
    this.auth = new Auth(this, "Auth", { config, adminCallbackUrl: `${this.adminWeb.url}/auth/callback` });

    new CfnOutput(this, "AdminUrl", { value: this.adminWeb.url });
    new CfnOutput(this, "AdminBucket", { value: this.adminWeb.bucket.bucketName });
    new CfnOutput(this, "AdminDistributionId", { value: this.adminWeb.distribution.distributionId });
    new CfnOutput(this, "CognitoDomain", { value: this.auth.domainUrl });
    new CfnOutput(this, "CognitoIssuer", { value: this.auth.issuerUrl });
    new CfnOutput(this, "CognitoAdminClientId", { value: this.auth.adminClient.userPoolClientId });
    new CfnOutput(this, "CognitoMobileClientId", { value: this.auth.mobileClient.userPoolClientId });
    new CfnOutput(this, "MediaCdnUrl", { value: `https://${this.media.cdn.distributionDomainName}` });
    new CfnOutput(this, "EcrRepositoryUri", { value: this.registry.repository.repositoryUri });
  }
}
```

`api-stack.ts`:
```ts
import { CfnOutput, Stack, type StackProps } from "aws-cdk-lib";
import type { Construct } from "constructs";

import type { EnvConfig } from "./config.ts";
import { ApiService } from "./constructs/api-service.ts";
import type { PlatformStack } from "./platform-stack.ts";

export class ApiStack extends Stack {
  readonly apiUrl: string;

  constructor(
    scope: Construct,
    id: string,
    props: StackProps & { config: EnvConfig; platform: PlatformStack; imageTag: string },
  ) {
    super(scope, id, props);
    const { platform } = props;
    const api = new ApiService(this, "Api", {
      config: props.config,
      vpc: platform.network.vpc,
      database: platform.database,
      repository: platform.registry.repository,
      auth: platform.auth,
      media: platform.media,
      adminUrl: platform.adminWeb.url,
      imageTag: props.imageTag,
    });
    this.apiUrl = api.url;
    new CfnOutput(this, "ApiUrl", { value: api.url });
  }
}
```

- [ ] **Step 5: `bin/app.ts`'yi yaz**

```ts
import { App } from "aws-cdk-lib";

import { ApiStack } from "../lib/api-stack.ts";
import { CiStack } from "../lib/ci-stack.ts";
import { type EnvName, loadConfig, REGION } from "../lib/config.ts";
import { PlatformStack } from "../lib/platform-stack.ts";

const ENVS: EnvName[] = ["staging", "prod"];

export function buildApp(app: App, options: { account?: string } = {}) {
  const env = { account: options.account ?? process.env.CDK_DEFAULT_ACCOUNT, region: REGION };
  const imageTag = String(app.node.tryGetContext("imageTag") ?? "bootstrap");
  const githubRepo = String(app.node.tryGetContext("githubRepo") ?? "soruhavuzu/soruhavuzu");

  const platforms = {} as Record<EnvName, PlatformStack>;
  const apis = {} as Record<EnvName, ApiStack>;
  for (const name of ENVS) {
    const config = loadConfig(name);
    platforms[name] = new PlatformStack(app, `soruhavuzu-${name}-platform`, { env, config });
    apis[name] = new ApiStack(app, `soruhavuzu-${name}-api`, { env, config, platform: platforms[name], imageTag });
  }
  const ci = new CiStack(app, "soruhavuzu-ci", { env, repository: githubRepo });
  return { platforms, apis, ci };
}

if (import.meta.main) {
  buildApp(new App());
}
```

> `as Record<...>` iki satırı ESLint'in `consistent-type-assertions` kuralına takılır. Her birinin üstüne gerekçeli istisna ekle: `// eslint-disable-next-line @typescript-eslint/consistent-type-assertions -- döngüde iki ortamla doldurulur; dönüşten önce tamamlanır`. `import.meta.main` Node 24.2+'de vardır.

- [ ] **Step 6: Çalıştır, geçtiğini gör; tam sentezi doğrula**

Run: `cd infra && pnpm test && pnpm typecheck && CDK_DEFAULT_ACCOUNT=111111111111 pnpm synth -c imageTag=abc1234 -c githubRepo=ornek/soruhavuzu`
Expected: Tüm infra testleri PASS. `cdk synth` 5 yığını (`soruhavuzu-staging-platform`, `soruhavuzu-staging-api`, `soruhavuzu-prod-platform`, `soruhavuzu-prod-api`, `soruhavuzu-ci`) hatasız sentezler. `fromLookup` sahte değer uyarısı kabul edilir; gerçek değer ilk `cdk deploy`'da `cdk.context.json`'a yazılır ve commit'lenir.

- [ ] **Step 7: Kökten lint ve commit**

Run: `pnpm lint`
Expected: hatasız.
```bash
git add infra
git commit -m "feat(infra): ortam yığınları, GitHub OIDC deploy rolü ve CDK girişi"
```

---

### Task 10: GitHub Actions — CI ve staging/prod deploy

**Files:**
- Create: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/actionlint.yaml`
- Test: `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.12 -color` hatasız

**Interfaces:**
- Consumes: Task 9 çıktı adları; Task 7 `Dockerfile` ve `make docker-smoke` sözleşmesi.
- GitHub ayarları (Görev 12'de girilir):
  - `vars.AWS_DEPLOY_ROLE_ARN`
  - `staging` ve `production` ortamları; `production` için zorunlu onaylayıcı
- Admin build değişkenleri:
  - `VITE_API_URL` ← `ApiUrl`
  - `VITE_AUTH_MODE=cognito`
  - `VITE_COGNITO_AUTHORITY` ← `CognitoIssuer`
  - `VITE_COGNITO_CLIENT_ID` ← `CognitoAdminClientId`
  - `VITE_ASSET_BASE_URL` ← `MediaCdnUrl`

- [ ] **Step 1: Başarısız doğrulamayı çalıştır**

Run: `ls .github/workflows/ci.yml`
Expected: FAIL — dosya yok

- [ ] **Step 2: `.github/workflows/ci.yml`'yi yaz**

```yaml
name: ci

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  backend:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - name: Lint, test, mimari testler (ktlint, detekt, Modulith, ArchUnit, Testcontainers)
        working-directory: backend
        run: ./gradlew check --console=plain
      - name: OpenAPI farkı (packages/api-client/openapi.json koddan yeniden üretildi)
        run: git diff --exit-code -- packages/api-client/openapi.json

  js:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with: { node-version-file: .nvmrc, cache: pnpm }
      - run: pnpm install --frozen-lockfile
      - run: pnpm lint
      - run: pnpm typecheck
      - run: pnpm test
      - name: Üretilen dosyaların farkı (api-client tipleri, content-schema JSON Schema)
        run: |
          pnpm --filter @soruhavuzu/api-client generate
          pnpm --filter @soruhavuzu/content-schema generate
          git diff --exit-code -- packages/api-client/src/schema.ts packages/content-schema/schema.json
      - run: pnpm --filter @soruhavuzu/infra synth
        env: { CDK_DEFAULT_ACCOUNT: "111111111111" }

  gitleaks:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with: { fetch-depth: 0 }
      - uses: gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e # v3.0.0
        env: { GITHUB_TOKEN: "${{ secrets.GITHUB_TOKEN }}" }

  actionlint:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - run: docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.12 -color

  image:
    runs-on: ubuntu-24.04-arm
    needs: [backend]
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - name: Image duman testi (Postgres'e karşı staging profili)
        run: make docker-smoke

  deploy-staging:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [backend, js, gitleaks, actionlint, image]
    runs-on: ubuntu-24.04-arm
    environment: staging
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: ./.github/actions/deploy
        with: { env: staging, role-arn: "${{ vars.AWS_DEPLOY_ROLE_ARN }}" }
```

- [ ] **Step 3: Paylaşılan deploy adımını yaz — `.github/actions/deploy/action.yml`**

```yaml
name: deploy
description: Image'ı ECR'a gönderir, api yığınını günceller, admin'i yayınlar
inputs:
  env: { required: true, description: "staging | prod" }
  role-arn: { required: true, description: "GitHub OIDC deploy rolü" }
runs:
  using: composite
  steps:
    - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
      with: { distribution: temurin, java-version: "25" }
    - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
    - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
    - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
      with: { node-version-file: .nvmrc, cache: pnpm }
    - uses: aws-actions/configure-aws-credentials@e1253824e5c10ff9df46874f81ed3ec929e19cfd # v6.3.0
      with: { role-to-assume: "${{ inputs.role-arn }}", aws-region: eu-central-1 }
    - id: ecr
      uses: aws-actions/amazon-ecr-login@03f1aad4c6c7ffd436567f42f9384779290529bd # v2.1.7
    - shell: bash
      run: pnpm install --frozen-lockfile
    - name: Image (etiket = commit SHA)
      shell: bash
      env:
        REPO: ${{ steps.ecr.outputs.registry }}/soruhavuzu-api-${{ inputs.env }}
      run: |
        (cd backend && ./gradlew bootJar otelAgentJar --console=plain)
        docker build -t "$REPO:$GITHUB_SHA" backend
        docker push "$REPO:$GITHUB_SHA"
    - name: Altyapı ve servis
      shell: bash
      working-directory: infra
      run: >
        pnpm cdk deploy soruhavuzu-${{ inputs.env }}-platform soruhavuzu-${{ inputs.env }}-api
        -c imageTag=$GITHUB_SHA -c githubRepo=$GITHUB_REPOSITORY
        --require-approval never --outputs-file cdk-outputs.json
    - name: Admin paneli
      shell: bash
      run: |
        out() { jq -r --arg k "$1" '[.[] | to_entries[] | select(.key==$k) | .value][0]' infra/cdk-outputs.json; }
        export VITE_API_URL="$(out ApiUrl)" VITE_AUTH_MODE=cognito VITE_COGNITO_AUTHORITY="$(out CognitoIssuer)"
        export VITE_COGNITO_CLIENT_ID="$(out CognitoAdminClientId)" VITE_ASSET_BASE_URL="$(out MediaCdnUrl)"
        pnpm --filter @soruhavuzu/admin build
        aws s3 sync admin/dist "s3://$(out AdminBucket)" --delete
        aws cloudfront create-invalidation --distribution-id "$(out AdminDistributionId)" --paths "/*" >/dev/null
    - name: Duman testi
      shell: bash
      run: |
        API="$(jq -r '[.[] | to_entries[] | select(.key=="ApiUrl") | .value][0]' infra/cdk-outputs.json)"
        curl -sf --retry 30 --retry-delay 10 --retry-all-errors "$API/actuator/health" | grep -q '"UP"'
        test "$(curl -s -o /dev/null -w '%{http_code}' "$API/v1/me")" = "401"
```

- [ ] **Step 4: `.github/workflows/release.yml`'yi yaz (prod: tag + elle onay)**

```yaml
name: release

on:
  push:
    tags: ["v*"]

permissions:
  contents: read

jobs:
  deploy-prod:
    runs-on: ubuntu-24.04-arm
    environment: production
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: ./.github/actions/deploy
        with: { env: prod, role-arn: "${{ vars.AWS_DEPLOY_ROLE_ARN }}" }
```

`.github/actionlint.yaml`:
```yaml
self-hosted-runner:
  labels: []
config-variables:
  - AWS_DEPLOY_ROLE_ARN
```

- [ ] **Step 5: Çalıştır, geçtiğini gör**

Run: `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.12 -color && echo OK`
Expected: `OK` ve hiçbir uyarı yok.

- [ ] **Step 6: Commit**

```bash
git add .github
git commit -m "ci: lint/test/mimari/OpenAPI farkı/gitleaks/image ve OIDC ile staging/prod deploy"
```

---

### Task 11: EAS Build profilleri ve development build

**Files:**
- Create: `mobile/eas.json`
- Modify: `mobile/package.json` (`expo-dev-client` ekle), `mobile/app.json` (`plugins`'e `expo-dev-client`), `mobile/.env.example` (yorum)
- Test: `pnpm --filter @soruhavuzu/mobile exec eas config --platform android --profile preview --non-interactive` hatasız (EAS hesabı gerektirmez)

**Interfaces:**
- Produces: profiller `development`, `preview`, `production`. Ortam değerleri EAS sunucusundaki `development` / `preview` / `production` ortamlarından gelir (`eas env:create`, Görev 12). Repoda değer yoktur.

- [ ] **Step 1: `expo-dev-client` SDK 57 sürümünü bul (≥14 gün)**

Run: `npm view expo-dev-client time --json | python3 -c "import sys,json;d=json.load(sys.stdin);print([k for k,t in d.items() if k.startswith('57.') and '-' not in k and t[:10]<='2026-10-15'][-1])"`
Expected: bir `57.0.x` sürümü (ör. `57.0.9`). Bu sürümü `mobile/package.json` `dependencies` içine kesin olarak yaz, `pnpm install` çalıştır.

- [ ] **Step 2: `mobile/eas.json`'u yaz**

```json
{
  "cli": { "version": ">= 25.0.0", "appVersionSource": "remote" },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "environment": "development"
    },
    "preview": {
      "distribution": "internal",
      "environment": "preview",
      "android": { "buildType": "apk" }
    },
    "production": {
      "environment": "production",
      "autoIncrement": true
    }
  },
  "submit": { "production": {} }
}
```

`mobile/app.json` `plugins` dizisi: `["expo-router", "expo-secure-store", "expo-web-browser", "expo-dev-client"]`

- [ ] **Step 3: Yapılandırmayı doğrula**

Run: `cd mobile && pnpm exec eas config --platform android --profile preview --non-interactive 2>&1 | tail -20`
Expected: `buildType: apk` ve `environment: preview` gösterilir; şema hatası yok. EAS oturumu yoksa yalnızca "not logged in" uyarısı kabul edilir; şema hatası kabul edilmez.

- [ ] **Step 4: Mobil testler ve tip kontrolü**

Run: `pnpm --filter @soruhavuzu/mobile typecheck && pnpm --filter @soruhavuzu/mobile test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile/eas.json mobile/package.json mobile/app.json pnpm-lock.yaml
git commit -m "feat(mobile): EAS build profilleri ve development client"
```

---

### Task 12: İlk deploy runbook'u ve (ONAYLI) staging kurulumu

**Files:**
- Create: `docs/runbooks/ilk-deploy.md`
- Modify: `README.md` ("Deploy" bölümü: runbook'a bağlantı)

**Interfaces:**
- Consumes: Task 9 yığın ve çıktı adları, Task 10 değişken adları, Task 11 EAS ortam adları.

- [ ] **Step 1: Runbook'u yaz — `docs/runbooks/ilk-deploy.md`**

```markdown
# İlk deploy (staging)

Ön koşullar: AWS hesabı (yönetici yetkili oturum), GitHub reposu, Docker, Node 24, `make doctor` yeşil.

1. **Hesap hazırlığı (bir kez):**
   `cd infra && pnpm cdk bootstrap aws://<HESAP>/eu-central-1`
2. **CI rolü:**
   `pnpm cdk deploy soruhavuzu-ci -c githubRepo=<owner>/<repo>`
   Çıktıdaki `DeployRoleArn` değerini GitHub → Settings → Variables'a `AWS_DEPLOY_ROLE_ARN` adıyla gir.
3. **GitHub ortamları:**
   - `staging` (koruma yok)
   - `production` (Required reviewers: proje sahibi; yalnızca `v*` etiketleri)
   - Branch protection: `main`'e doğrudan push kapalı, `ci` iş akışı zorunlu (kurallar.md §8).
4. **Platform yığını:**
   `pnpm cdk deploy soruhavuzu-staging-platform`
   RDS oluşumu yaklaşık 15 dk sürer. `cdk.context.json` oluşursa commit'le.
5. **İlk image:** `main`'e ilk merge, CI'ın `deploy-staging` işini tetikler. Bu iş image'ı gönderir, `soruhavuzu-staging-api`'yi oluşturur ve admin'i yayınlar.
6. **Doğrulama (Faz 0 kabul kriterleri):**
   - CI duman testi: `GET <ApiUrl>/actuator/health` = UP, `GET <ApiUrl>/v1/me` = 401.
   - Admin: `<AdminUrl>` → Cognito ile kayıt ol ve giriş yap → "… olarak giriş yapıldı (USER)".
   - Mobil:
     - `eas env:create --environment preview` ile şunları gir: `EXPO_PUBLIC_API_URL=<ApiUrl>`, `EXPO_PUBLIC_AUTH_MODE=cognito`, `EXPO_PUBLIC_COGNITO_DOMAIN=<CognitoDomain>`, `EXPO_PUBLIC_COGNITO_CLIENT_ID=<CognitoMobileClientId>`, `EXPO_PUBLIC_ASSET_BASE_URL=<MediaCdnUrl>`.
     - `eas build -p android --profile preview` ile build al, kur, giriş yap → ana sayfada kullanıcı bilgisi.
7. **Apple / Google ile giriş:**
   - Apple Developer'da Services ID + key, Google Cloud'da OAuth istemcisi oluştur.
   - Bunları Secrets Manager'a gir.
   - `Auth` construct'ına `UserPoolIdentityProviderApple` / `UserPoolIdentityProviderGoogle` ekleyen değişikliği PR olarak aç (ayrı görev).

Maliyet (staging, yaklaşık): RDS t4g.small, Fargate 0.5 vCPU, iç ALB, CloudFront ve Cognito Lite (10k MAU'ya kadar ücretsiz). Aylık tahmin AWS Pricing Calculator ile çıkarılıp bu dosyaya yazılır.
```

- [ ] **Step 2: README'ye "Deploy" bölümü ekle (Hızlı başlangıç'tan sonra)**

```markdown
## Deploy

Altyapı AWS CDK ile (`infra/`, [ADR 0023](docs/adr/0023-aws-topology.md)):
- `main`'e merge → CI → staging'e otomatik deploy.
- `v*` etiketi → onaylı prod deploy.
- İlk kurulum adımları: [docs/runbooks/ilk-deploy.md](docs/runbooks/ilk-deploy.md).
```

- [ ] **Step 3: Commit**

```bash
git add docs/runbooks/ilk-deploy.md README.md
git commit -m "docs: ilk deploy runbook'u ve README deploy bölümü"
```

- [ ] **Step 4: DUR — kullanıcı onayı.** Kullanıcıya şunları sor:
  - AWS hesap kimliği ve yönetici oturumu
  - GitHub `owner/repo` (repo oluşturulsun mu?)
  - Push ve staging deploy onayı (maliyet yaratır)

  Onay gelmeden 1–5. runbook adımlarını çalıştırma.

- [ ] **Step 5 (onaydan sonra): Runbook 1–6'yı uygula ve kabul kriterlerini işaretle**

Expected: CI'da tüm işler yeşil; `deploy-staging` duman testi yeşil; admin Cognito girişi ve mobil preview build'de `GET /v1/me` sonucu görülür. fazlar.md Faz 0 kabul kriterlerindeki "staging'e giriş" ve "main → staging" maddeleri ✅. Sonuç NotebookLM'e Faz 0 kapanış raporu olarak yazılır.
```
