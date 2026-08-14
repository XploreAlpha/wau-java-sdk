[English](README.md) | [中文](README.zh-CN.md)

[![Maven Central](https://img.shields.io/badge/Maven%20Central-not%20published-lightgrey)](https://central.sonatype.com/)
[![javadoc](https://javadoc.io/badge/io.wau/wau-sdk.svg)](https://www.javadoc.io/doc/io.wau/wau-sdk)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/wau/wau-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/wau/wau-java-sdk/actions)

# wau-java-sdk

> **WAU-core-kernel** 官方 Java SDK — AI Agent Network OS
> (sidecar 部署,libp2p 内核,OpenClaw A2A 网关 adapter 层)。

Java SDK 是 5 语言 SDK(Go / Python / TypeScript / Rust / Java)之一,共享同一份
WAU 网络的 canonical 契约。目标 JDK 17 LTS,以单一 Maven artifact
(`io.wau:wau-sdk`)交付,零内部 WAU 依赖 — 仅 OkHttp、Jackson、jjwt 三个外部库。

> 英文版参考见 [README.md](README.md)。

---

## 特性

SDK 暴露 **49 个公开顶层类型**,分布到 8 个子 package,组织如下:

- **核心 client** — `WauClient`(入口)+ `WauConfig`(不可变 builder 配置)。
- **领域 client** — `BotsClient`(bot 注册)、`L5Service`(L5 包管理器 v1.3.3)、
  `UCPClient`(UCP 电商 v1.3.3)、`MCPClient`(MCP 8 个同步 tool v1.3.2)、
  `MCPStreamClient`(MCP 2 个 SSE 流式 tool)。
- **Bot 平台集成**(Stage 1 native,per W6.2 closure):`SlackBot` /
  `FeishuBot` / `QQBot` / `DingTalkBot` / `EmailBot`。另外 3 平台
  (Telegram / Discord / Webhook) 由 `wau-channel` 服务端实现完整 4 步,
  本 SDK bot/ 推到 v1.3.0+ bump。
- **异常体系** — `WauException` 根类 + 7 个专用子类(鉴权、限流、
  not-found、server 错误、bot 注册失败)。
- **Model DTO** — 13 个 wire-format POJO(`IncomingMessage` / `OutgoingMessage` /
  `Agent` / `Bot` / `Provider` / `SampleRequest` / `SampleResponse` / `CostEvent` /
  `WauWorkflow` + 4 个 workflow 嵌套类型),与 4 个兄弟 SDK 字节级对齐
  (per **D13** canonical 契约)。
- **Auth 辅助** — `JwtVerifier`(D79 JWT 三向校验)+ `BotHeaderParser`
  (D78 `X-WAU-Bot` / `X-WAU-User` header 注入)。
- **Transport** — `HttpTransport`(OkHttp 封装,内部共享,允许自定义调用方复用)。

自 v1.0.0 起所有新增功能全部走 **D60 additive**:**零** 重命名、**零** 删除、
**零** 破坏性签名变更。

---

## 安装

Artifact 坐标:

- **GroupId**: `io.wau`
- **ArtifactId**: `wau-sdk`
- **Packaging**: `jar`

### Maven

```xml
<dependency>
    <groupId>io.wau</groupId>
    <artifactId>wau-sdk</artifactId>
    <version>1.3.4</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.wau:wau-sdk:1.3.4'
```

> **Maven Central 状态** — artifact **尚未发布** 到 Maven Central。下一发版周期
> 计划发布。`io.wau` group + `wau-sdk` artifact id 已预留;本地可通过
> `mvn install` 消费 snapshot 环境。

---

## 快速开始(Quickstart)

```java
import io.wau.sdk.WauClient;
import io.wau.sdk.WauConfig;
import io.wau.sdk.model.CostEvent;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;

public class HelloWau {

    public static void main(String[] args) {
        // 1. 配置 SDK(per D63 §4.1.1)
        WauConfig cfg = new WauConfig.Builder()
                .wauEdgeUrl("http://localhost:18402")  // wau-edge base URL
                .botId("bot:demo:hello-java")          // 公开 bot ID(D78)
                .ownerUserId("100")                    // owner_user_id(D80)
                .botToken("demo-token-change-in-prod") // bearer token(D79)
                .build();

        // 2. 构造 client
        WauClient client = new WauClient(cfg);

        // 3. 发消息(M5 — 8 平台下游)
        IncomingMessage msg = IncomingMessage.builder()
                .channelId("telegram")
                .userId("alice")
                .text("hello from Java")
                .build();
        OutgoingMessage reply = client.sendMessage(msg);
        System.out.println("reply=" + reply);

        // 4. 记录 LLM 成本(M3 §3.1)
        client.recordCost(CostEvent.builder()
                .userId("alice")
                .provider("deepseek")
                .costUsd(0.001)
                .build());
    }
}
```

运行:

```bash
mvn compile exec:java -Dexec.mainClass="HelloWau"
```

更完整示例(bot 注册、LLM 决策采样、agent 拉取、成本记录)见
[examples/BasicBot.java](examples/BasicBot.java)。

---

## 公开 API

| Class                  | Package                    | 用途                                            |
|------------------------|----------------------------|------------------------------------------------|
| `WauClient`            | `io.wau.sdk`               | 顶层 client,5 核心方法                          |
| `WauConfig`            | `io.wau.sdk`               | 不可变 builder 配置                             |
| `BotsClient`           | `io.wau.sdk.bot.registry`  | Bot 注册: register / get / update / list / delete |
| `L5Service`            | `io.wau.sdk.l5`            | L5 包管理器(install / uninstall / update / search / login) |
| `UCPClient`            | `io.wau.sdk.ucp`           | UCP commerce client(11 tool,JSON-RPC 2.0)     |
| `MCPClient`            | `io.wau.sdk.mcp`           | MCP client,8 个同步 tool                        |
| `MCPStreamClient`      | `io.wau.sdk.mcp`           | MCP SSE streaming client,2 个 tool              |
| `SlackBot`             | `io.wau.sdk.bot.slack`     | Slack 平台集成(Stage 1 native)                  |
| `FeishuBot`            | `io.wau.sdk.bot.feishu`    | 飞书平台集成                                     |
| `QQBot`                | `io.wau.sdk.bot.qq`        | QQ 平台集成(OkHttp fallback)                   |
| `DingTalkBot`          | `io.wau.sdk.bot.dingtalk`  | 钉钉 stream 集成                                 |
| `EmailBot`             | `io.wau.sdk.bot.email`     | SMTP + IMAP 邮件集成                             |

### `WauClient` 5 核心方法

| # | 方法                                | HTTP                                      | 关联 M |
|---|-------------------------------------|-------------------------------------------|--------|
| 1 | `sendMessage(IncomingMessage)`      | `POST /v1/bots/{id}/messages`             | M5     |
| 2 | `registerBot(String, String)`       | `POST /v1/registry/bots`                  | M10 N3.3 |
| 3 | `getAgent(String)`                  | `GET /v1/registry/agents/{id}`            | M8 W4  |
| 4 | `sampleLLM(SampleRequest)`          | `POST /v1/llm/sample`                     | M3 W3  |
| 5 | `recordCost(CostEvent)`             | `POST /v1/llm/cost`                       | M3 §3.1 |

---

## 子模块

SDK 内部按职责边界拆分为聚焦子 package:

| Package                  | 职责                                                  |
|--------------------------|------------------------------------------------------|
| `io.wau.sdk`             | `WauClient`、`WauConfig` — 公开入口                    |
| `io.wau.sdk.model`       | 13 个 wire-format DTO(Java PascalCase,JSON snake_case) |
| `io.wau.sdk.transport`   | `HttpTransport`(OkHttp 封装,所有 client 共享)          |
| `io.wau.sdk.auth`        | `JwtVerifier` + `BotHeaderParser`(D79 / D78)           |
| `io.wau.sdk.exception`   | 8 个异常类,根类 `WauException`                         |
| `io.wau.sdk.l5`          | `L5Service` + 11 个 L5 DTO(v1.3.3)                    |
| `io.wau.sdk.ucp`         | `UCPClient` + 8 个 commerce DTO + `UcpException`(v1.3.3) |
| `io.wau.sdk.mcp`         | `MCPClient` + `MCPStreamClient` + 10 个 DTO(v1.3.2)   |
| `io.wau.sdk.bot.common`  | `Bot`、`Account`、`BotBuilder`、`BotsService`         |
| `io.wau.sdk.bot.registry`| `BotsClient` HTTP client(v1.3.0)                     |
| `io.wau.sdk.bot.slack`   | `SlackBot` native SDK 集成                            |
| `io.wau.sdk.bot.feishu`  | `FeishuBot` native SDK 集成                           |
| `io.wau.sdk.bot.qq`      | `QQBot` OkHttp 自实现                                 |
| `io.wau.sdk.bot.dingtalk`| `DingTalkBot` stream SDK 集成                         |
| `io.wau.sdk.bot.email`   | `EmailBot` SMTP + IMAP 集成                           |

---

## 示例

可运行示例见 [`examples/BasicBot.java`](examples/BasicBot.java),端到端覆盖
`WauClient` 全部 5 核心方法:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="BasicBot" -Dexec.classpathScope=runtime
```

---

## 文档

- **[Javadoc](https://www.javadoc.io/doc/io.wau/wau-sdk)** — 每发版自动生成,
  本地副本在 `target/site/apidocs/`。
- **[CHANGELOG.md](CHANGELOG.md)** — 版本历史 + 每版本变更说明。
- **[RELEASING.md](RELEASING.md)** — maintainer 发版 checklist(97 行 / 7 节)。
- **[WAU protocol spec](https://github.com/wau-network/WAU-protocol-spec)** —
  wire-format 权威参考。

每个子 package 的 `package-info.java` 是 IDE javadoc 导航的 canonical 入口。

---

## 版本策略

SDK 遵循 [Semantic Versioning 2.0.0](https://semver.org/),**与 WAU 19 仓矩阵
lockstep 版本对齐**。当前发版号为 **v1.3.4**,下一个版本将随其他 4 SDK
(Go / Python / TypeScript / Rust) 同步 bump 到 v1.3.5 / v1.4.0 / 更高主版本。

| SDK           | 仓库                                  | 对齐版本   |
|---------------|---------------------------------------|-----------|
| Java(本仓)    | `wau-network/wau-java-sdk`            | v1.3.4    |
| Go            | `wau-network/wau-go-sdk`              | v1.3.4    |
| Python        | `wau-network/wau-python-sdk`          | v1.3.4    |
| TypeScript    | `wau-network/wau-typescript-sdk`      | v1.3.4    |
| Rust          | `wau-network/wau-rust-sdk`            | v1.3.4    |

预发版后缀格式 `-alpha.N` / `-beta.N` / `-rc.N`。

---

## 跟 4 SDK 同步窗口(详细)

| 时间 | SDK 版本 | Java SDK 改动 | 状态 |
|------|----------|----------------|------|
| **2026-07-10**(W2.1 提前 ~10 周) | **v1.3.0** | **+ `botUuid` 1 字段(+ BotsClient + 3 exception + 10 test,~236 行)** | **✅ DONE**(per D78/D79/D80 拍板 7-09) |
| 2026-07-11(W2 prep buffer Day 4,D88.9 提前 W6 ~9 周) | **v1.3.3** | **+ UCP commerce client(11 tool + 8 DTO + JSON-RPC 2.0 + ~22 test,~1180 行)** | **✅ DONE**(per D88) |
| 2026-08-14 | **v1.3.4** | + README/CONTRIBUTING/CHANGELOG/LICENSE OSS onboarding + pom.xml 1.3.4 bump | **✅ 本 commit** |
| 2026-09-20 | v1.3.1 | + `register_agent` 1 方法(+30 行)| ⏳ 计划 |
| 2026-11-14 | v1.3.2(5 SDK 统一)| (本期不含推 v1.x)| ⏳ 计划 |

---

## v1.3.3 新增:UCP 客户端(per D88,2026-07-11 W2 prep buffer Day 4)

5 SDK 同步落地 UCP(Universal Commerce Protocol),wau-java-sdk 通过
`WauClient.ucp()` getter 暴露:

```java
import io.wau.sdk.ucp.Dto;
import io.wau.sdk.ucp.UcpException;

try {
    Dto.ListProductsResult products = client.ucp().listProducts(null);
    Dto.Product p = client.ucp().getProduct("p-1");
    Dto.Cart cart = client.ucp().addToCart("p-1", 2);

    // W3 stub 路径:create_checkout_session + confirm_payment 走
    // kernel ErrNotImplemented → SDK 抛 UcpException.NotImplemented("W5 Stripe 集成中 ...")
    Dto.CheckoutSession session = client.ucp().createCheckoutSession("cart-1");
} catch (UcpException.NotImplemented e) {
    // W3 stub period: kernel Stripe 集成待 W5+ 落地
    System.out.println(e.getMessage());
} catch (UcpException.RpcError e) {
    // 业务错误:product_not_found (-32101) / payment_failed (-32103) ...
    System.err.println("UCP RPC error code=" + e.code() + ": " + e.getMessage());
}
```

**11 commerce tool**(per `ucp/Tools.java`):

| # | Tool | HTTP/envelope | DTO |
|---|------|----------------|-----|
| 1 | `list_products(filter)` | POST /ucp tools/call | `Dto.ListProductsResult` |
| 2 | `get_product(productId)` | 同上 | `Dto.Product` |
| 3 | `search_products(query, limit)` | 同上 | `Dto.SearchProductsResult` |
| 4 | `add_to_cart(productId, qty)` | 同上 | `Dto.Cart` |
| 5 | `get_cart(cartId)` | 同上 | `Dto.Cart` |
| 6 | `remove_from_cart(cartId, lineItemId)` | 同上 | `Dto.Cart` |
| 7 | `create_checkout_session(cartId)` | 同上,W3 stub | `Dto.CheckoutSession` |
| 8 | `confirm_payment(sessionId)` | 同上,W3 stub | `Dto.PaymentConfirmation` |
| 9 | `get_order(orderId)` | 同上 | `Dto.Order` |
| 10 | `list_orders(userId, filter)` | 同上 | `Dto.ListOrdersResult` |
| 11 | `cancel_order(orderId)` | 同上 | `Dto.CancelOrderResult` |

**总同步成本:已完成 236 行 v1.3.0(7-10 提前),后续 ~80 行 / 2 个 bump** ✅ per D63

> **W2.1 提前落地 (2026-07-10)**:
> - 5 SDK v1.3.0 同步缺口修复:Java SDK 加 `botUuid`(UUID v4 server-assigned,D78 拍板),
>   4 SDK 已 7-09 bump v1.3.0
> - 新 `BotsClient` 5 method 实现 + 3 exception class + 10 单元测试
> - commit `awaits push`(per feedback-cli-cant-push-git),push manifest 见
>   `WAU-develop/develop-log/kernel/v1.0.0/stage2/2026-07-10-W2.1-push-manifest.sh`
>   (用户手动 push 后给路径)
>
> **W2 prep buffer Day 4 (2026-07-11) D88.9 提前落地**:
> - 5 SDK UCP client v1.3.3 同步完成(go 7-11 + python 7-11 + typescript 7-11 +
>   rust 7-11 + java **本 commit**)
> - 新 `ucp/` 包 7 文件 + 1 test(~1180 行):11 commerce tool + 8 DTO + JSON-RPC 2.0 dispatcher
> - W3 stub:`create_checkout_session` + `confirm_payment` →
>   `UcpException.NotImplemented("W5 Stripe 集成中")` friendly
> - D60 additive:0 改老 WauClient 5 核心方法 + 0 改 l5() getter

---

## Bot Platforms(per W5 反 W4.1)

WAU SDK 通过两段责任分工对接 N 个 Bot 平台(W5 新增):

| 责任段 | 仓 | 文件 | 覆盖范围 |
|--------|----|------|----------|
| **公共契约** | 本 SDK(`bot/common/`) | `Bot` + `BotsService` 抽象接口 | 5 SDK 100% 一致(per M10 N1) |
| **C 端 SDK bot/ 子包** | 本 SDK | `bot/{telegram,discord,webhook,slack,feishu,qq,dingtalk,email}/` | 每个 SDK 自带 8 平台(W5 Q2 拍板:Java SDK 加 5 平台 bot/ 完整 SDK 端实现) |
| **服务端 8 平台 adapter** | `wau-channel` | `internal/adapter/{slack,feishu,dingtalk,qq,email,telegram,discord,webhook}/*_real.go` | 全部 8 平台完整 4 步(per W7 2026-07-07 SDK 接通) |
| **服务端 bot HTTP API** | `wau-edge` | `POST /v1/bots/{bot_id}/messages`(per M10 N3) | Bot → 后端路由 |

**Bot Platforms 公开能力表**(2026-07-13, **W5 update 反 W4.1**):

| Platform     | 本 SDK bot/ | wau-channel adapter | 状态 |
|--------------|-------------|---------------------|------|
| Telegram     | 🔲 | ✅ | 仅 wau-channel 完整(per W4.1 Java SDK 5 核心方法未含 bot/) |
| Discord      | 🔲 | ✅ | 同上 |
| Webhook      | 🔲 | ✅ | 同上 |
| Slack        | ✅ Stage 1 native | ✅ 完整 4 步(`com.slack.api:slack-api-client` v1+ SocketModeApp) | W6.2 closure: SocketModeApp 集成 (~267 LoC) |
| Feishu       | ✅ Stage 1 native | ✅ 完整 4 步(`com.larksuite.oapi:oapi-sdk-java` v1+) | W6.2 closure: Client + EventDispatcher 集成 (~321 LoC) |
| QQ           | ✅ Stage 1 native | ✅ 完整 4 步(`OkHttp` fallback) | W6.2 closure: 自实现 HTTP client (~365 LoC) |
| DingTalk     | ✅ Stage 1 native | ✅ 完整 4 步(`com.dingtalk.open:dingtalk-stream-sdk-java` v1+) | W6.2 closure: OpenDingTalkStreamClient 长连接 (~247 LoC) |
| Email        | ✅ Stage 1 native | ✅ 完整 4 步(`com.sun.mail:jakarta.mail` v2+) | W6.2 closure: SMTP + IMAP (~353 LoC) |

> **W5 Q2 拍板**(per 2026-07-13):wau-java-sdk 加 5 平台 bot/ 完整 SDK 端实现
> (Slack / Feishu / QQ / DingTalk / Email);Telegram / Discord / Webhook 3 平台
> bot/ 推到 v1.3.0+ bump(per SDK v1.3.0+v1.3.1+v1.3.2 deadline 2026-10-24 W10 末)。
>
> **W6.2 closure (2026-07-09)**: 5 平台(Slack / Feishu / QQ / DingTalk / Email)
> Stage 0 stub → Stage 1 native SDK integration 100% 收口
> (per M10 N1 + D13 + D78 + D80 公共契约)。W6.0 同步补 Account + BotBuilder +
> BotsService DTO 反转(per D13 5 SDK byte-equal 公共契约)。

---

## JDK 要求

SDK 目标 **JDK 17 LTS**(对齐 Spring Boot 3.x 与 2026 年 Java 生态)。不依赖
任何 preview 特性,任何提供 17+ 工具链的发行版均可运行(Temurin、Liberica、
Zulu、Corretto、Oracle JDK 等)。

推荐构建工具:**Maven 3.8+**。Gradle 8+ 作为备选支持;`pom.xml` 是 canonical
构建描述符。

---

## Maven Central 发布

Maven Central 发布 **计划于下一发版周期**。所需 Sonatype OSS 基础设施正在准备:

- `sonatype-oss` Maven profile + `maven-gpg-plugin` GPG 签名
- `<distributionManagement>` 配置 `oss.sonatype.org`(或新命名空间
  `s01.oss.sonatype.org`)staged-plugins staging repo
- Javadoc + source JAR 已被 `maven-source-plugin` + `maven-javadoc-plugin`
  自动 attach(见 `pom.xml` §Build)

发布前,可通过本地安装消费:

```bash
mvn install -DskipTests
```

从本地 `~/.m2/repository/io/wau/wau-sdk/` 目录消费。

---

## 贡献指南

欢迎贡献,follow 单分支工作流直到 v1.0.0 GA。详见
[CONTRIBUTING.md](CONTRIBUTING.md):

- 开发环境搭建(JDK 17 + Maven 3.8+)
- 跑测试套件(`mvn test`)
- 代码风格 + import order
- Pull Request 流程
- 跟其他 4 SDK 的版本同步策略

Maintainer 发版说明见 [RELEASING.md](RELEASING.md)。

---

## 协议

**Apache License 2.0**。完整文本见 [LICENSE](LICENSE)。

Copyright 2026 WAU Network and wau-java-sdk contributors.

---

## 关联链接

- [WAU Whitepaper & 治理](https://github.com/wau-network/WAU-core-kernel)
- [WAU Protocol Spec](https://github.com/wau-network/WAU-protocol-spec)
- [其他 SDK](#版本策略) — Go / Python / TypeScript / Rust
- [Issues](https://github.com/wau-network/wau-java-sdk/issues)
