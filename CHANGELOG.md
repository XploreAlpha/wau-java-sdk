# Changelog

All notable changes to **wau-java-sdk** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

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
