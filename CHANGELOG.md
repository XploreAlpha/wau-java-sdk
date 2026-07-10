# Changelog

All notable changes to **wau-java-sdk** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased] — v1.3.0 "bot_uuid field add (W2.1, 2026-07-10)"

### Added

- `botUuid` (UUID v4, server-assigned) field added to `Account` + `BotsService.RegisterRequest` records
  - Jackson `@JsonProperty("bot_uuid")` 双向序列化(`account_id`/`tenant_id`/`bot_id`/`bot_uuid`/`public_bot_id`/`owner_user_id`/`channel_type`/`channel_config_id`/`created_at`/`updated_at` 10 字段顺序对齐 wau-go-sdk D13)
- 新 `bot/registry/BotsClient.java` HTTP client 实现 5 method (`register` / `get` / `update` / `list` / `delete`)
- 新 3 exception 类对齐 v1.3.0 bot 注册错误码:
  - `BotNotFoundException`(status 404)
  - `BotAlreadyExistsException`(status 409)
  - `BotInvalidArgumentException`(status 400)
- 新 `BotsClientTest.java` 10 单元测试(5 method × 2 case)用 `HttpTransport.Sender` mock

### Compatibility (D60 additive)

- 0 改老 9 字段(顺序保持一致)
- 老 caller `newAccount(tenant, bot, owner, ch, cfg)` 不传 `botUuid` → 服务端 Register 时回填,跟 4 SDK 行为一致
- 老 SDK v1.2.0 不写 `bot_uuid` 字段 → server 用 `bot_id` slug 寻址并自动生成(per D80 兼容降级)

### Verified (待 user CI 跑)

- Sandbox 无 `mvn` binary,plan 落地靠 `grep` 验证:`botUuid`/`bot_uuid`/`BotUUID` 在 main/ 共 8 命中
- 完整 `mvn test -Dtest=BotsClientTest` 留 user 本地或 CI 跑

---

## [Unreleased] — v1.3.3 "⭐L5 包管理器 client 雏形 (A3 prep 2026-07-10, per D72/D73/D74)"

### Added

- 新 `l5/L5Service.java` (~470 LoC):**5 L5 核心方法**对齐 wau-go-sdk L5Service(per D13):
  - `install(InstallRequest)`     → POST /v1/l5/install
  - `uninstall(UninstallRequest)` → POST /v1/l5/uninstall
  - `update(UpdateRequest)`       → POST /v1/l5/update
  - `search(SearchRequest)`       → POST /v1/l5/search
  - `login(LoginRequest)`         → POST /v1/l5/login
- 11 个 DTO class(`InstallRequest`/`InstallResponse`/`UninstallRequest`/`UninstallResponse`/`UpdateRequest`/`UpdateResponse`/`SearchRequest`/`SearchHit`/`SearchResponse`/`LoginRequest`/`LoginResponse`)— Jackson `@JsonProperty("snake_case")` 对齐 5 SDK 协议
- `WauClient.l5()` getter — D60 additive,0 改老 5 核心方法 + 0 改 WauConfig
- 新 `l5/L5ServiceTest.java` 5 单测(MockWebServer,5 端点 + 错误码 + D73 软删 + 401 AuthException + 409 WauException)
- `pom.xml` 加 `com.squareup.okhttp3:mockwebserver` test scope(per D72/D74 L5 测试基建)

### Compatibility (D60 additive)

- 0 改老 `WauClient` 5 核心方法(`sendMessage`/`registerBot`/`getAgent`/`sampleLLM`/`recordCost`)
- 0 改老 `WauConfig` + 老 `Account` + 老 `BotsService` 字段
- 老 caller 拿到 WauClient 后想用 L5,只需 `client.l5().install(...)`(lazy init,L5Service 没引到就不创建)
- v0.1.0-preview 老 caller 不知道 `l5()` getter → 编译向后兼容

### Verified (待 user CI 跑,本沙箱无 mvn)

- `grep -rn "l5.Service\|l5.Install\|l5.Search" /home/inamoto888/project/wau-java-sdk/src/main/java/io/wau/sdk/l5/`
  期望:5 endpoint + 11 DTO 命名一致
- 完整 `mvn test -Dtest=L5ServiceTest` 留 user 本地或 CI 跑

### Reference

- D72 A 拍板(wau-toolkit v2.0 OS-level, 仓数 +0):[stage1/01-D66-D74-9-decisions-summary#七](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/stage1/01-D66-D74-9-decisions-summary.md)
- D74 A 拍板(install 协议 = gRPC InstallAgent):[stage1/01-D66-D74-9-decisions-summary#九](https://github.com/wau-network/WAU-develop/blob/main/develop-log/kernel/v1.0.0/stage1/01-D66-D74-9-decisions-summary.md)
- wau-go-sdk L5Service 参考:`wau-go-sdk/l5.go`(跟 Go SDK snake_case 协议对齐)

---

## [0.1.0-preview] — 2026-07-08

### Added

First preview release of wau-java-sdk (per D63=A, 2026-07-06 by youhaoxi).

**5 Core Methods**(per [[2026-07-06-D63-new-sdk-intro]]):

- `WauClient.sendMessage(IncomingMessage msg)` — M5 八平台下游消费
- `WauClient.registerBot(String botId, String token)` — M10 N3.3 Bot 注册
- `WauClient.getAgent(String agentId)` — M8 Agent 元数据
- `WauClient.sampleLLM(SampleRequest req)` — M3 W3 LLM 决策采样
- `WauClient.recordCost(CostEvent event)` — M3 W3 成本事件上报

**Model DTOs** (per D13 + D78 + D80 = 9 字段对齐 4 SDK):

- `IncomingMessage` — 7 字段(D13)+ `bot_id` + `owner_user_id`(D78/D80)
- `OutgoingMessage`, `Agent`, `Bot`, `Provider`
- `SampleRequest`, `SampleResponse`
- `CostEvent`

**Auth**(per D79 三向校验):

- `JwtVerifier` — verify token + user_id + bot_id + owner_user_id 三向校验
- `BotHeaderParser` — X-WAU-Bot + X-WAU-User header 注入(snake_case per D78)

**Transport**:

- `HttpTransport` — OkHttp 封装(GET / POST / JSON encode/decode)
- `GrpcTransport` — gRPC stub 占位(M11 完工后实装)

**Exceptions**:

- `WauException` (基类)
- `AuthException` (D79 三向校验失败)
- `RateLimitException` (M3 限流)
- `WauNotFoundException` (404)
- `WauServerException` (5xx 上游故障)

**Build / Tests**:

- JDK 17 LTS + Maven 3.8+(Gradle alt supported)
- 16 unit tests + BasicBot example

### Compatibility (D60-all-style additive)

- 5 SDK 同步成本:+80 行 / 3 个 bump(v1.3.0 / v1.3.1 / v1.3.2)
- 0 改 4 SDK 既有契约
- 0 改 WAU-protocol-spec 协议(JSON snake_case 已存在)

### Not in this preview (推 v1.x)

- ❌ Streaming(push to v1.1.x)
- ❌ L5 包管理(push to v1.3.2)
- ❌ Honcho user-modeling(push to v1.x)
- ❌ Spring Boot starter(push to v1.x)
