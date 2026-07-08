# wau-java-sdk

> Official Java SDK for **WAU-core-kernel** — AI agent dispatch network.
> v1.0.0 "Phoenix" Java SDK preview (**2026-07-08**)

[English](#english) | [中文](#中文)

---

## English

### What is wau-java-sdk?

A **preview** Java client for the **WAU network** — the AI Agent Network OS
(sidecar-based, libp2p kernel, OpenClaw A2A gateway adapter).

This SDK matches the **v0.9.0 GA** + **v1.0.0 "Phoenix"** protocol layer
(JSON snake_case, JWT 3-way verify per **D79**, bot_id naming per **D78/D80**).

### 5 Core Methods (per D63=A, 2026-07-06)

```java
import io.wau.sdk.WauClient;
import io.wau.sdk.WauConfig;
import io.wau.sdk.model.IncomingMessage;

WauConfig cfg = new WauConfig.Builder()
    .wauEdgeUrl("http://localhost:18402")
    .botId("my-java-bot")
    .build();

WauClient client = new WauClient(cfg);

// 1. sendMessage (M5 八平台下游调用)
IncomingMessage msg = IncomingMessage.builder()
    .channelId("telegram")
    .userId("alice")
    .text("hello from Java")
    .build();
client.sendMessage(msg);

// 2. registerBot (M10 N3.3)
Bot bot = client.registerBot("bot:default:myjava", "tok-xxx");

// 3. getAgent (M8)
Agent a = client.getAgent("agent-001");

// 4. sampleLLM (M3 W3 — LLM 决策采样)
SampleResponse sampled = client.sampleLLM(
    SampleRequest.builder().prompt("summarize").build());

// 5. recordCost (M3 W3 — 成本事件上报)
client.recordCost(CostEvent.builder()
    .userId("alice").provider("deepseek").costUsd(0.001)
    .build());
```

### Install (Maven)

```xml
<dependency>
    <groupId>io.wau</groupId>
    <artifactId>wau-java-sdk</artifactId>
    <version>0.1.0-preview</version>
</dependency>
```

### What's NOT in this preview (per D63+A)

- ❌ **Streaming** — push to v1.1.x
- ❌ **L5 包管理** (`wau agent install`) — push to v1.3.2
- ❌ **Honcho user-modeling** — push to v1.x
- ❌ **Spring Boot starter** — push to v1.x

These are planned upgrades; this preview is sufficient for v1.0.0 commercial use.

### JDK / Build

- **JDK 17 LTS** (aligned with Spring Boot 3.x)
- **Maven 3.8+** (Gradle alt supported)
- 0 dependencies on internal WAU code (pure HTTP + gRPC)

### Links

- WAU overview: see [[../wau-go-sdk/README.md]] (Go SDK — the canonical reference)
- v1.0.0 D63 doc: [D63-new-sdk-intro](https://github.com/wau-network/WAU-protocol-spec/blob/main/docs/decisions/2026-07-06-D63-new-sdk-intro.md)
- 17 + 2 = 19 仓 matrix(per [[2026-07-04-v1.0.0-W1-W15-detailed-schedule]])

---

## 中文

### wau-java-sdk 是什么?

**WAU 网络**的 Java 客户端预览版 — AI Agent Network OS
(sidecar 部署,libp2p 内核,OpenClaw A2A 网关 adapter 层)。

本 SDK 对齐 **v0.9.0 GA** + **v1.0.0 "Phoenix"** 协议层
(JSON snake_case、JWT 3-way 校验 per **D79**、bot_id 命名 per **D78/D80**)。

### 5 核心方法(per 2026-07-06 D63=A 用户拍板)

| # | 方法 | 用途 | 关联 M |
|---|---|---|---|
| 1 | `sendMessage(IncomingMessage)` | 发消息(M5 八平台下游)| M5 |
| 2 | `registerBot(String, String)` | 注册 bot | M10 N3.3 |
| 3 | `getAgent(String)` | 拉 agent 元数据 | M8 |
| 4 | `sampleLLM(SampleRequest)` | LLM 决策采样 | M3 W3 |
| 5 | `recordCost(CostEvent)` | 成本事件上报 | M3 W3 |

### 不在 preview 范围(per D63+A 推 v1.x)

| 缺失 | 推到 |
|---|---|
| Streaming 流式聊天 | v1.1.x |
| L5 包管理器(wau agent install)| v1.3.2 |
| Honcho user-modeling | v1.x |
| Spring Boot starter | v1.x |

### 跟 4 SDK 同步窗口

| 时间 | SDK 版本 | Java SDK 改动 |
|---|---|---|
| 2026-09-20 | v1.3.0 | + `bot_id` + `owner_user_id` 2 字段(+50 行)|
| 2026-10-24 | v1.3.1 | + `register_agent` 1 方法(+30 行)|
| 2026-11-14 | v1.3.2(5 SDK 统一)| (本期不含推 v1.x)|

**总同步成本:+80 行 / 3 个 bump** ✅ per D63

### License

Apache-2.0
