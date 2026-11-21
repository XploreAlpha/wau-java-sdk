# Changelog

All notable changes to **wau-java-sdk** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## Wire Format Reference (added 2026-07-11 per D93 SOP)

This SDK is **canonical-compliant** with [WAU-protocol-spec v1.0.0](https://github.com/youhaoxi/WAU-protocol-spec).
JSON schemas for `a2a-message` / `afp-send` / `ap2-mandate` / `netbird-config` / `ucp-checkout` are reference;
Java record/class field names (camelCase in JSON via Jackson, PascalCase in Java) may differ from schema `properties` keys,
but the **JSON-RPC envelope + wire format MUST match**.

---

## [v1.4.0] - 2026-11-19 — "Opal" — ★ intent 实装 + A2A v1.0 客户端

### Fixed

- **★ User-Agent 里的版本号 stale**(跟 SDK 版本对不上)
  - Java SDK 的 `X-WAU-Client` 头写死 `wau-java-sdk/0.1.0-preview`(L5Service 里还是 `0.2.0-preview`)
    ⇒ 全部对齐到 **1.4.0**。

### Added

- **★ `IntentService` —— 全新(Java SDK 之前**完全没有** intent 服务)**
  - 实装 kernel 的 `POST /v1/intent/parse` + `POST /v1/intent/score`(实测确认可用)。
  - `parseIntent` / `scoreAgents` / `healthCheck`。
  - ⚠️ `/v1/intent/score` 的 score 元素用 **Go 字段名**;解析时两套拼写都认。

- **★ `A2AClient` —— A2A v1.0 客户端(全新)**
  - 线格式(实测):方法名 `SendMessage` + **每次必带 `A2A-Version: 1.0` 头** + 可选 Bearer token。
  - `sendMessage` 返回原始 JSON-RPC result;`sendText` 直接返回文本
    (覆盖 task 分支 / message 分支 / legacy v0.3.0 三种形状)。

- `WauClient.registerAgent(...)` —— 注册进 WAU registry,支持携带 `auth_token` 调用凭证。

## [v1.3.4] - 2026-08-14 — "OSS onboarding (README + CONTRIBUTING + LICENSE + version bump)"

### Added

- **OSS onboarding documentation set** for the public GitHub repository:
  - `README.md` — full English reference with language switcher, 4 shields.io
    badges, features, installation, quickstart, public API table, sub-module
    map, examples, documentation, versioning, JDK requirement, Maven Central
    publishing status, contributing, and license sections
  - `README.zh-CN.md` — Chinese mirror of the README, preserving the existing
    detail on the v1.3.3 UCP client, the bot-platform capability matrix, and
    the cross-SDK sync window
  - `CONTRIBUTING.md` — full contribution guide with code of conduct, dev
    setup, testing, linting, PR process, commit conventions, version-sync
    policy, and a Maven Central publishing section for maintainers, plus a
    `## 中文贡献指南` section at the bottom
- `<distributionManagement>` documentation in `CONTRIBUTING.md` covering the
  planned `sonatype-oss` Maven profile, `maven-gpg-plugin` signing, and the
  Sonatype OSSRH staging flow

### Changed

- `pom.xml` `<version>` bumped from `0.1.0-preview` to **`1.3.4`** to align
  with the lockstep WAU 19-repository matrix and the other 4 SDKs (Go, Python,
  TypeScript, Rust) which are all on v1.3.4.
- `pom.xml` Maven coordinates now documented as `io.wau:wau-sdk` (artifactId
  shortened from `wau-java-sdk` for Maven Central publication; the GitHub
  repository name remains `wau-java-sdk`).

### Fixed

- `LICENSE` was a 17-line header-only file; replaced with the full Apache-2.0
  standard text from https://www.apache.org/licenses/LICENSE-2.0.txt. This
  unblocks downstream consumers who require the verbatim grant language in
  their license audits.

### Compatibility (D60 additive)

- **0 changes** to any source under `src/main/java/io/wau/sdk/` — only
  documentation, metadata, and `LICENSE` were modified.
- `0.1.0-preview` consumers can upgrade to `1.3.4` without recompilation: the
  import paths, class names, and method signatures are byte-identical.

---

## [v1.3.3] - 2026-07-11 — "UCP commerce client (D88.9, W2 prep buffer Day 4)"

### Added

- New submodule `src/main/java/io/wau/sdk/ucp/` (independent package, D60
  additive) plus `src/test/java/io/wau/sdk/ucp/`, with 7 source files + 1
  test file + 1 `package-info.java`:
  - `Tools.java` — 11 `TOOL_*` String constants
    (`TOOL_LIST_PRODUCTS` ... `TOOL_CANCEL_ORDER`) plus the
    `ALL_TOOL_NAMES` list
  - `Stripe.java` — `isStripePath(toolName)` Stripe path helper + 4
    `PAYMENT_STATUS_*` constants (`SUCCEEDED` / `FAILED` / `PROCESSING` /
    `PENDING`)
  - `UcpAuth.java` — `UcpAuth` immutable record (`bearerToken` + `tenantId`)
    + `apply(headers)` header injection + `setBearerToken` / `setTenantId`
    static helpers + the `AUTH_HEADER_NAME` / `AUTH_SCHEME_PREFIX` /
    `DEFAULT_TENANT_HEADER_NAME` constants
  - `UcpException.java` — root exception (extends `WauException`) + 4 inner
    classes (`Config` for local validation, `NotImplemented` for the W3 stub
    friendly path, `RpcError` for JSON-RPC error envelope code+message+data,
    `Malformed` for envelopes missing both `result` and `error`) + an inner
    `RPCError` DTO + 5 spec codes (`-32700/-32600/-32601/-32602/-32603`) and
    5 UCP codes (`-32101/-32102/-32103/-32104/-32105`) + the
    `isNotFound(err)` / `isStripeError(err)` / `wrapW3Stub(rpcErr)` static
    helpers
  - `Dto.java` — 8 commerce DTOs (`Product` / `Cart` + `CartLineItem` /
    `CheckoutSession` / `PaymentConfirmation` / `Order` +
    `ListOrdersFilter` + `ListOrdersResult` / `CancelOrderResult`) + 3
    filter/result DTOs (`ListProductsFilter` + `ListProductsResult` +
    `SearchProductsResult`)
  - `UCPClient.java` — `UCPClient` final class (`AtomicLong` ID counter +
    Jackson `ObjectMapper` + reused `HttpTransport`) with two constructors
    (simplified default + full ctor injecting endpoint/userAgent) + 11
    typed wrapper methods (`listProducts` / `getProduct` / `searchProducts` /
    `addToCart` / `getCart` / `removeFromCart` / `createCheckoutSession`
    [W3 stub catch] / `confirmPayment` [W3 stub catch] / `getOrder` /
    `listOrders` / `cancelOrder`) + private `callTool<T>` JSON-RPC
    dispatcher + `unwrapEnvelope` decoder
  - `package-info.java` — UCP module doc
- `WauClient.ucp()` getter (D60 additive, matches the `l5()` getter pattern,
  lazy init)
- 8 commerce DTOs include the `tenant_id` field (per D65 multi-tenant)
- 11 `TOOL_*` String constants + `ALL_TOOL_NAMES` list (per design doc §3)
- 5 spec + 5 UCP error code constants (per design doc §4)
- `UcpAuth.apply(headers)` + `setBearerToken` / `setTenantId` (per
  D78/D79/D80 OAuth bearer + D65 tenant isolation) static helpers
- `Stripe.isStripePath(tool_name)` Stripe path helper (per D88.8)
- 4 `PAYMENT_STATUS_*` constants
- ~22 unit tests in `src/test/java/io/wau/sdk/ucp/UCPClientTest.java`, using
  the `HttpTransport.Sender` mock (mirroring the `BotsClientTest.CapturingSender`
  pattern, no MockWebServer required) — mirrors the UCP test style of
  wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk

### Compatibility (D60 additive)

- 0 changes to the existing SDK (`WauClient` 5 core methods `sendMessage` /
  `registerBot` / `getAgent` / `sampleLLM` / `recordCost` are all untouched)
- 0 changes to the existing `WauConfig` and the existing `l5()` getter
  (D60 additive, pure `ucp()` getter append)
- Uses OkHttp + Jackson (reuses `HttpTransport` + `Sender` mock pattern) —
  same as `L5Service` and `BotsClient`
- W3 stub: `create_checkout_session` + `confirm_payment` hit the kernel
  `INTERNAL` error (`RPCError` code `-32603`, message containing "W5 Stripe"),
  and the SDK auto-wraps it as
  `UcpException.NotImplemented("W5 Stripe 集成中 ...")`
- No direct Stripe SDK dependency (waits for kernel
  `internal/protocol/ucp/ucp_stripe.go` to land in W5+)

### Reference

- D88 decision (UCP server):
  [stage2/2026-07-10-D86-D87-D88-protocol-gateway-decision](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/stage2/2026-07-10-D86-D87-D88-protocol-gateway-decision.md)
- 5-SDK UCP client design:
  [process/2026-07-11-W3-UCP-client-SDK-design](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/process/2026-07-11-W3-UCP-client-SDK-design.md)
- UCP Stripe design:
  [process/2026-07-11-W3-UCP-Stripe-Checkout-design](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/process/2026-07-11-W3-UCP-Stripe-Checkout-design.md)
- Sibling SDKs: wau-go-sdk v1.3.3 `ucpclient/` (D88.5 commit `d8a600f`, 28 tests);
  wau-python-sdk v1.3.3 `ucp_*.py` (D88.6 commit `43c2e08`, 25 tests);
  wau-typescript-sdk v1.3.3 `src/ucp/` (D88.7 commit `2765c1f`, 29 tests);
  wau-rust-sdk v1.3.3 `src/ucp/` (D88.8 commit `de500c5`, 27 tests);
  this module is the D88.9 sync landing
- benny migration clarification (2026-07-11, user-confirmed): the kernel UCP
  is the universal commerce vertical-protocol layer; benny remains an
  independent demo plugin

---

## [v1.3.3] - 2026-07-10 — "L5 package manager client (A3 prep, per D72/D73/D74)"

### Added

- New `l5/L5Service.java` (~470 LoC): **5 L5 core methods** aligned with
  wau-go-sdk `L5Service` (per D13):
  - `install(InstallRequest)`     → `POST /v1/l5/install`
  - `uninstall(UninstallRequest)` → `POST /v1/l5/uninstall`
  - `update(UpdateRequest)`       → `POST /v1/l5/update`
  - `search(SearchRequest)`       → `POST /v1/l5/search`
  - `login(LoginRequest)`         → `POST /v1/l5/login`
- 11 DTO classes (`InstallRequest` / `InstallResponse` / `UninstallRequest` /
  `UninstallResponse` / `UpdateRequest` / `UpdateResponse` / `SearchRequest` /
  `SearchHit` / `SearchResponse` / `LoginRequest` / `LoginResponse`) — Jackson
  `@JsonProperty("snake_case")` aligned with the 5-SDK protocol
- `WauClient.l5()` getter — D60 additive, 0 changes to the 5 core methods
  and 0 changes to `WauConfig`
- New `l5/L5ServiceTest.java` with 5 unit tests (MockWebServer: 5 endpoints
  + error codes + D73 soft-delete + 401 `AuthException` + 409 `WauException`)
- `pom.xml` adds `com.squareup.okhttp3:mockwebserver` in test scope (per
  D72/D74 L5 test infrastructure)

### Compatibility (D60 additive)

- 0 changes to the existing `WauClient` 5 core methods
  (`sendMessage` / `registerBot` / `getAgent` / `sampleLLM` / `recordCost`)
- 0 changes to the existing `WauConfig`, the existing `Account`, and the
  existing `BotsService` fields
- Existing callers who already have a `WauClient` can call
  `client.l5().install(...)` without any recompilation (lazy init — the
  `L5Service` is not constructed until the first call)
- v0.1.0-preview callers who never touched `l5()` keep compiling and running
  unchanged

### Reference

- D72 A decision (wau-toolkit v2.0 OS-level, repository count +0):
  [stage1/01-D66-D74-9-decisions-summary#七](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/stage1/01-D66-D74-9-decisions-summary.md)
- D74 A decision (install protocol = gRPC `InstallAgent`):
  [stage1/01-D66-D74-9-decisions-summary#九](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/stage1/01-D66-D74-9-decisions-summary.md)
- wau-go-sdk `L5Service` reference: `wau-go-sdk/l5.go` (snake_case protocol
  alignment with the Go SDK)

---

## [v1.3.0] - 2026-07-10 — "bot_uuid field add (W2.1, 7-10 ahead of schedule)"

### Added

- `botUuid` (UUID v4, server-assigned) field added to `Account` +
  `BotsService.RegisterRequest` records — Jackson `@JsonProperty("bot_uuid")`
  bidirectional serialization (the 10-field order
  `account_id` / `tenant_id` / `bot_id` / `bot_uuid` / `public_bot_id` /
  `owner_user_id` / `channel_type` / `channel_config_id` / `created_at` /
  `updated_at` is aligned byte-for-byte with wau-go-sdk per D13)
- New `bot/registry/BotsClient.java` HTTP client with 5 methods
  (`register` / `get` / `update` / `list` / `delete`)
- 3 new exception classes aligned with the v1.3.0 bot-registration error
  codes:
  - `BotNotFoundException` (status 404)
  - `BotAlreadyExistsException` (status 409)
  - `BotInvalidArgumentException` (status 400)
- New `BotsClientTest.java` with 10 unit tests (5 methods × 2 cases) using
  the `HttpTransport.Sender` mock

### Compatibility (D60 additive)

- 0 changes to the existing 9 fields (order preserved)
- Existing callers using `newAccount(tenant, bot, owner, ch, cfg)` who do
  not pass `botUuid` will have it server-side backfilled at Register time,
  matching the behavior of the 4 other SDKs
- The v1.2.0 SDK that did not write the `bot_uuid` field falls back to
  `bot_id` slug addressing and server-side auto-generation (per D80
  compatibility downgrade)

---

## [0.1.0-preview] - 2026-07-08

### Added

First preview release of wau-java-sdk (per D63=A, 2026-07-06 by youhaoxi).

**5 Core Methods** (per [[2026-07-06-D63-new-sdk-intro]]):

- `WauClient.sendMessage(IncomingMessage msg)` — M5 eight-platform downstream
- `WauClient.registerBot(String botId, String token)` — M10 N3.3 bot
  registration
- `WauClient.getAgent(String agentId)` — M8 agent metadata
- `WauClient.sampleLLM(SampleRequest req)` — M3 W3 LLM decision sampling
- `WauClient.recordCost(CostEvent event)` — M3 W3 cost event reporting

**Model DTOs** (per D13 + D78 + D80 = 9 fields aligned with 4 SDKs):

- `IncomingMessage` — 7 fields (D13) + `bot_id` + `owner_user_id` (D78/D80)
- `OutgoingMessage`, `Agent`, `Bot`, `Provider`
- `SampleRequest`, `SampleResponse`
- `CostEvent`

**Auth** (per D79 three-way verify):

- `JwtVerifier` — verify token + user_id + bot_id + owner_user_id three-way
  check
- `BotHeaderParser` — `X-WAU-Bot` + `X-WAU-User` header injection
  (snake_case per D78)

**Transport**:

- `HttpTransport` — OkHttp wrapper (GET / POST / JSON encode/decode)
- `GrpcTransport` — gRPC stub placeholder (finalized after M11)

**Exceptions**:

- `WauException` (base class)
- `AuthException` (D79 three-way verify failure)
- `RateLimitException` (M3 rate limit)
- `WauNotFoundException` (404)
- `WauServerException` (5xx upstream failure)

**Build / Tests**:

- JDK 17 LTS + Maven 3.8+ (Gradle alternative supported)
- 16 unit tests + `BasicBot` example

### Compatibility (D60-all-style additive)

- 5-SDK sync cost: +80 lines / 3 bumps (v1.3.0 / v1.3.1 / v1.3.2)
- 0 changes to the 4 sibling SDKs' existing contracts
- 0 changes to the WAU-protocol-spec protocol (JSON snake_case is already
  canonical)

### Not in this preview (planned for v1.x)

- Streaming — push to v1.1.x
- L5 package manager — push to v1.3.2 (subsequently delivered as v1.3.3)
- Honcho user-modeling — push to v1.x
- Spring Boot starter — push to v1.x
