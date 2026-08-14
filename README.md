[English](README.md) | [中文](README.zh-CN.md)

[![Maven Central](https://img.shields.io/badge/Maven%20Central-not%20published-lightgrey)](https://central.sonatype.com/)
[![javadoc](https://javadoc.io/badge/io.wau/wau-sdk.svg)](https://www.javadoc.io/doc/io.wau/wau-sdk)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/wau/wau-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/wau/wau-java-sdk/actions)

# wau-java-sdk

> Official Java SDK for **WAU-core-kernel** — the AI Agent Network OS
> (sidecar deployment, libp2p kernel, OpenClaw A2A gateway adapter).

The Java SDK is one of five language SDKs (Go, Python, TypeScript, Rust, Java) that
share a single canonical contract for the WAU network. It targets JDK 17 LTS and is
delivered as a single Maven artifact (`io.wau:wau-sdk`) with zero internal WAU
dependencies — only OkHttp, Jackson, and jjwt.

This document is the English reference. A Chinese mirror is available at
[README.zh-CN.md](README.zh-CN.md).

---

## Features

The SDK exposes **49 public top-level types** across 8 sub-packages, organized as
follows:

- **Core client** — `WauClient` (entry point) and `WauConfig` (immutable builder
  configuration).
- **Domain clients** — `BotsClient` (bot registry), `L5Service` (package manager
  v1.3.3), `UCPClient` (commerce v1.3.3), `MCPClient` (8 sync tools v1.3.2), and
  `MCPStreamClient` (2 SSE streaming tools).
- **Bot platform integrations** (Stage 1 native, per W6.2 closure): `SlackBot`,
  `FeishuBot`, `QQBot`, `DingTalkBot`, `EmailBot`. Three additional platforms
  (Telegram, Discord, Webhook) are available server-side via `wau-channel`.
- **Exception hierarchy** — `WauException` (root) plus 7 specialized subclasses
  for auth, rate limit, not-found, server errors, and bot-registry failures.
- **Model DTOs** — 13 wire-format POJOs (`IncomingMessage`, `OutgoingMessage`,
  `Agent`, `Bot`, `Provider`, `SampleRequest`, `SampleResponse`, `CostEvent`,
  `WauWorkflow` plus four workflow nested types) aligned byte-for-byte with the
  4 other SDKs per the **D13** canonical contract.
- **Auth helpers** — `JwtVerifier` (D79 three-way JWT verify) and
  `BotHeaderParser` (D78 `X-WAU-Bot` / `X-WAU-User` headers).
- **Transport** — `HttpTransport` (OkHttp wrapper, used internally and reusable
  for custom callers).

All additions since v1.0.0 are **D60-additive**: zero renames, zero removals,
zero breaking signature changes.

---

## Installation

The artifact coordinates are:

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

> **Maven Central status** — the artifact is **not yet published** to Maven
> Central. Publication is planned for the next release cycle. The `io.wau`
> group and `wau-sdk` artifact id are reserved; build the SDK locally via
> `mvn install` to consume it from a snapshot environment.

---

## Quickstart

```java
import io.wau.sdk.WauClient;
import io.wau.sdk.WauConfig;
import io.wau.sdk.model.CostEvent;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;

public class HelloWau {

    public static void main(String[] args) {
        // 1. Configure the SDK (per D63 §4.1.1)
        WauConfig cfg = new WauConfig.Builder()
                .wauEdgeUrl("http://localhost:18402")  // wau-edge base URL
                .botId("bot:demo:hello-java")          // public bot ID (D78)
                .ownerUserId("100")                    // owner_user_id (D80)
                .botToken("demo-token-change-in-prod") // bearer token (D79)
                .build();

        // 2. Build the client
        WauClient client = new WauClient(cfg);

        // 3. Send a message (M5 — 8 platform downstream)
        IncomingMessage msg = IncomingMessage.builder()
                .channelId("telegram")
                .userId("alice")
                .text("hello from Java")
                .build();
        OutgoingMessage reply = client.sendMessage(msg);
        System.out.println("reply=" + reply);

        // 4. Record LLM cost (M3 §3.1)
        client.recordCost(CostEvent.builder()
                .userId("alice")
                .provider("deepseek")
                .costUsd(0.001)
                .build());
    }
}
```

Run it:

```bash
mvn compile exec:java -Dexec.mainClass="HelloWau"
```

A more complete walkthrough — bot registration, LLM sampling, agent lookup,
cost recording — is bundled at [examples/BasicBot.java](examples/BasicBot.java).

---

## Public API

| Class                  | Package                    | Purpose                                            |
|------------------------|----------------------------|----------------------------------------------------|
| `WauClient`            | `io.wau.sdk`               | Top-level client, exposes the 5 core methods       |
| `WauConfig`            | `io.wau.sdk`               | Immutable builder-based configuration              |
| `BotsClient`           | `io.wau.sdk.bot.registry`  | Bot registry: register / get / update / list / delete |
| `L5Service`            | `io.wau.sdk.l5`            | L5 package manager (install / uninstall / update / search / login) |
| `UCPClient`            | `io.wau.sdk.ucp`           | UCP commerce client (11 tools, JSON-RPC 2.0)       |
| `MCPClient`            | `io.wau.sdk.mcp`           | MCP client, 8 sync tools                           |
| `MCPStreamClient`      | `io.wau.sdk.mcp`           | MCP SSE streaming client, 2 tools                  |
| `SlackBot`             | `io.wau.sdk.bot.slack`     | Slack platform integration (Stage 1 native)        |
| `FeishuBot`            | `io.wau.sdk.bot.feishu`    | Feishu (Lark) platform integration                 |
| `QQBot`                | `io.wau.sdk.bot.qq`        | QQ platform integration (OkHttp fallback)          |
| `DingTalkBot`          | `io.wau.sdk.bot.dingtalk`  | DingTalk stream integration                        |
| `EmailBot`             | `io.wau.sdk.bot.email`     | SMTP + IMAP email integration                      |

### 5 Core Methods on `WauClient`

| # | Method                            | HTTP                                      | Milestone |
|---|-----------------------------------|-------------------------------------------|-----------|
| 1 | `sendMessage(IncomingMessage)`    | `POST /v1/bots/{id}/messages`             | M5        |
| 2 | `registerBot(String, String)`     | `POST /v1/registry/bots`                  | M10 N3.3  |
| 3 | `getAgent(String)`                | `GET /v1/registry/agents/{id}`            | M8 W4     |
| 4 | `sampleLLM(SampleRequest)`        | `POST /v1/llm/sample`                     | M3 W3     |
| 5 | `recordCost(CostEvent)`           | `POST /v1/llm/cost`                       | M3 §3.1   |

---

## Sub-modules

The SDK is internally organized into focused sub-packages, each with a clear
responsibility boundary:

| Package                  | Responsibility                                            |
|--------------------------|-----------------------------------------------------------|
| `io.wau.sdk`             | `WauClient`, `WauConfig` — public entry points            |
| `io.wau.sdk.model`       | 13 wire-format DTOs (PascalCase Java, snake_case JSON)     |
| `io.wau.sdk.transport`   | `HttpTransport` (OkHttp wrapper, shared by all clients)   |
| `io.wau.sdk.auth`        | `JwtVerifier` + `BotHeaderParser` (D79 / D78)              |
| `io.wau.sdk.exception`   | 8 exception types rooted at `WauException`                |
| `io.wau.sdk.l5`          | `L5Service` + 11 L5 DTOs (v1.3.3)                         |
| `io.wau.sdk.ucp`         | `UCPClient` + 8 commerce DTOs + `UcpException` (v1.3.3)   |
| `io.wau.sdk.mcp`         | `MCPClient` + `MCPStreamClient` + 10 DTOs (v1.3.2)        |
| `io.wau.sdk.bot.common`  | `Bot`, `Account`, `BotBuilder`, `BotsService`             |
| `io.wau.sdk.bot.registry`| `BotsClient` HTTP client (v1.3.0)                         |
| `io.wau.sdk.bot.slack`   | `SlackBot` native SDK integration                         |
| `io.wau.sdk.bot.feishu`  | `FeishuBot` native SDK integration                        |
| `io.wau.sdk.bot.qq`      | `QQBot` OkHttp-based integration                          |
| `io.wau.sdk.bot.dingtalk`| `DingTalkBot` stream SDK integration                      |
| `io.wau.sdk.bot.email`   | `EmailBot` SMTP + IMAP integration                        |

---

## Examples

A runnable example is shipped in [`examples/BasicBot.java`](examples/BasicBot.java).
It exercises all 5 core methods of `WauClient` end-to-end:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="BasicBot" -Dexec.classpathScope=runtime
```

---

## Documentation

- **[Javadoc](https://www.javadoc.io/doc/io.wau/wau-sdk)** — generated from
  every release; mirrored locally under `target/site/apidocs/`.
- **[CHANGELOG.md](CHANGELOG.md)** — version history and per-release notes.
- **[RELEASING.md](RELEASING.md)** — maintainer release checklist (97 lines, 7
  sections).
- **[WAU protocol spec](https://github.com/wau-network/WAU-protocol-spec)** —
  authoritative wire-format reference.

Architecture notes and the per-package `package-info.java` files are colocated
in each sub-module; `package-info.java` is the canonical entry point for IDE
javadoc navigation.

---

## Versioning

The SDK follows [Semantic Versioning 2.0.0](https://semver.org/) and is
**lockstep versioned with the WAU 19-repository matrix**. The current release
is **v1.3.4**; the next version will move to v1.3.5, v1.4.0, or a higher major
in lockstep with the other 4 SDKs (Go, Python, TypeScript, Rust).

| SDK           | Repository                          | Aligns with |
|---------------|-------------------------------------|-------------|
| Java (this)   | `wau-network/wau-java-sdk`          | v1.3.4      |
| Go            | `wau-network/wau-go-sdk`            | v1.3.4      |
| Python        | `wau-network/wau-python-sdk`        | v1.3.4      |
| TypeScript    | `wau-network/wau-typescript-sdk`    | v1.3.4      |
| Rust          | `wau-network/wau-rust-sdk`          | v1.3.4      |

Pre-release suffixes follow the form `-alpha.N` / `-beta.N` / `-rc.N`.

---

## JDK Requirement

The SDK targets **JDK 17 LTS** (aligned with Spring Boot 3.x and the broader
Java ecosystem in 2026). It does not require any preview features and runs
on any distribution that provides a 17+ toolchain (Temurin, Liberica, Zulu,
Corretto, Oracle JDK, etc.).

Maven 3.8+ is the recommended build tool. Gradle 8+ is supported as an
alternative; the provided `pom.xml` is the canonical build descriptor.

---

## Maven Central Publishing

Maven Central publication is **planned for the next release cycle**. The
required Sonatype OSS infrastructure is being prepared:

- `sonatype-oss` Maven profile with `maven-gpg-plugin` for GPG signing
- `<distributionManagement>` configured to `oss.sonatype.org` (or
  `s01.oss.sonatype.org` for the new namespace) with the staged-plugins
  staging repo
- Javadoc and source JARs already attached by `maven-source-plugin` and
  `maven-javadoc-plugin` (see `pom.xml` §Build)

Until publication, the artifact can be installed locally via:

```bash
mvn install -DskipTests
```

…and consumed from the local `~/.m2/repository/io/wau/wau-sdk/` directory.

---

## Contributing

Contributions are welcome and follow a single-branch workflow until v1.0.0 GA.
Please read [CONTRIBUTING.md](CONTRIBUTING.md) for:

- Development setup (JDK 17, Maven 3.8+)
- Running the test suite (`mvn test`)
- Code style and import order
- Pull request process
- The version-sync policy with the other 4 SDKs

For maintainer publishing instructions, see [RELEASING.md](RELEASING.md).

---

## License

**Apache License 2.0**. See [LICENSE](LICENSE) for the full text.

Copyright 2026 WAU Network and the wau-java-sdk contributors.

---

## Links

- [WAU Whitepaper & Governance](https://github.com/wau-network/WAU-core-kernel)
- [WAU Protocol Spec](https://github.com/wau-network/WAU-protocol-spec)
- [Other SDKs](#versioning) — Go, Python, TypeScript, Rust
- [Issues](https://github.com/wau-network/wau-java-sdk/issues)
