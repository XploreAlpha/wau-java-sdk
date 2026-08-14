# Contributing to wau-java-sdk

Thank you for your interest in contributing to **wau-java-sdk**, the official
Java client for the WAU network. This document walks you through the
development setup, coding conventions, and pull-request process.

> **中文贡献指南** — 详见本文末尾的 [## 中文贡献指南](#中文贡献指南) 段落。

---

## Code of Conduct

By participating in this project, you agree to maintain a respectful and
constructive tone in all interactions — issues, pull requests, reviews, and
discussions. The project is released under Apache-2.0 and follows the
[WAU governance model](https://github.com/wau-network/WAU-core-kernel);
please be patient with newcomers and assume good faith.

---

## Development Setup

The SDK targets **JDK 17 LTS** and is built with **Maven 3.8+**.

### Prerequisites

| Tool        | Version    | Notes                                          |
|-------------|------------|------------------------------------------------|
| JDK         | 17 LTS     | Temurin, Liberica, Zulu, Corretto all work     |
| Maven       | 3.8+       | `./mvnw` is **not** included; install system-wide |
| Git         | 2.30+      | For branching and commit hygiene               |

### Clone and Build

```bash
git clone https://github.com/wau-network/wau-java-sdk.git
cd wau-java-sdk
mvn install
```

The first build resolves the dependency graph and runs the full test suite
(123 unit tests across 9 test classes). Subsequent builds are incremental.

### IDE Setup

- **IntelliJ IDEA** — recommended. Open the project root and IDEA will detect
  `pom.xml` automatically. JDK 17 should be selected under *Project Structure*
  → *Project SDK*.
- **VS Code** — install the *Extension Pack for Java* and open the project
  folder; the *Java Projects* view will discover the Maven build.
- **Eclipse** — import as *Existing Maven Project* and set the JRE to a JDK 17
  installation.

---

## Running Tests

```bash
mvn test
```

To run a single test class:

```bash
mvn test -Dtest=WauClientTest
```

To run a single test method:

```bash
mvn test -Dtest=WauClientTest#sendMessage_buildsCorrectUrl
```

### Test Style

The project uses **JUnit 5** (Jupiter) and **Mockito 5** for mocking. The
internal `HttpTransport.Sender` interface is the canonical seam for
transport-level mocking; prefer it over `MockWebServer` for unit tests because
it avoids the cost of binding a port.

```java
HttpTransport.Sender fake = (method, url, headers, body) -> {
    if (url.endsWith("/v1/registry/bots")) {
        return "{\"bot_id\":\"bot:demo:x\",\"enabled\":true}";
    }
    throw new IllegalStateException("unexpected: " + url);
};
HttpTransport http = new HttpTransport(fake);
WauClient client = new WauClient(cfg, http);
```

Integration tests that need real network behavior should be tagged with
`@Tag("integration")` so they can be excluded from the default test run with
`-DexcludedGroups=integration`.

---

## Linting

The project does not currently enforce a Checkstyle or Spotless rule. The
following conventions are observed by hand and are reviewed in PRs:

- **Indentation** — 4 spaces, no tabs.
- **Line length** — soft limit 120 columns.
- **Import order** — `java.*` / `javax.*` first, then third-party (`com.*`,
  `io.*`, `org.*`), then `io.wau.sdk.*` last, separated by blank lines. *This
  order matters because `io.wau.sdk.*` must come after the standard `io.*`
  imports to keep the grouping stable.*
- **Naming** — PascalCase for types, camelCase for fields/methods, SCREAMING_SNAKE
  for constants. JSON property names use snake_case via Jackson
  `@JsonProperty("snake_case")`.
- **Public API** — every public class and method must carry a Javadoc comment.
  New public types must include a `package-info.java` if they introduce a new
  sub-package.
- **No Lombok** — keep the dependency surface at zero (per D63 R23-Java).

If you'd like to see a Checkstyle profile added, please open an issue.

---

## Pull Request Process

The project follows a **single-branch workflow** until v1.0.0 GA (per the
`feedback-no-branches-until-1.0-0` convention). All contributions land directly
on `main` via PR.

1. **Open an issue first** for non-trivial changes — describe the motivation,
   proposed API, and any backward-compatibility considerations. For obvious
   fixes (typos, test flakes, dependency bumps) an issue is not required.

2. **Create a topic branch** from `main` (this is the *only* time branches are
   encouraged; the branch should be deleted after merge).

   ```bash
   git checkout -b fix/jwt-verifier-allow-empty-sub
   ```

3. **Write code + tests** — every new public method must have at least one
   positive test and one error-path test.

4. **Verify locally**:

   ```bash
   mvn clean test
   ```

5. **Push and open the PR** — the PR title should follow the commit message
   convention below. Link the originating issue with `Closes #NNN`.

6. **Pass CI** — GitHub Actions runs on `ubuntu-latest` and `macos-latest`
   against JDK 17. The CI must be green before merge.

7. **Squash-merge** — the maintainer will squash-merge once the PR is approved
   and CI is green. The PR branch will be deleted automatically.

---

## Commit Message Convention

The project uses a slightly relaxed form of [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>

<longer description if needed>

Refs #NNN
```

| Type       | Purpose                                                        |
|------------|----------------------------------------------------------------|
| `feat`     | A new feature (new SDK method, new sub-module)                 |
| `fix`      | A bug fix                                                       |
| `docs`     | Documentation only (README, CHANGELOG, Javadoc)                 |
| `test`     | Adding or correcting tests                                      |
| `refactor` | Internal change with no public API effect                       |
| `chore`    | Build, dependencies, CI, license, metadata                      |
| `release`  | Release-prep commit (CHANGELOG bump, version bump in `pom.xml`) |

`<scope>` is one of: `sdk`, `l5`, `ucp`, `mcp`, `bot`, `auth`, `transport`,
`exception`, `model`, `ci`, `docs`.

---

## Version Sync Policy

The five WAU SDKs (Go, Python, TypeScript, Rust, Java) are **lockstep versioned**.
This means:

- The version in `pom.xml` (`<version>1.3.4</version>`) is updated in lockstep
  with the equivalent `<version>` in the other four SDKs' build descriptors.
- A new feature that lands in any one SDK is back-ported to the others within
  the same release cycle, or an ADR is filed explaining why the parity was
  broken.
- Pre-release tags (`-alpha.N`, `-beta.N`, `-rc.N`) are also shared.

If your PR changes a public API or adds a new method, please flag this in the
PR description so the maintainer can schedule the cross-SDK sync.

---

## Maven Central Publishing

> **Maintainer-only section** — end users can ignore this.

The artifact is published to **Maven Central** under the
`io.wau:wau-sdk` coordinates. Publishing is done via the
[Sonatype OSSRH](https://central.sonatype.org/publish/) flow.

### Required Infrastructure

The `pom.xml` will need a `sonatype-oss` profile that:

1. Configures `<distributionManagement>` pointing at the Sonatype OSSRH
   staging repository.
2. Configures the `maven-gpg-plugin` to sign the artifact JARs, sources JAR,
   and Javadoc JAR with the maintainer's GPG key.
3. Attaches the sources and Javadoc JARs (already wired via
   `maven-source-plugin` and `maven-javadoc-plugin` in `<build>`).

### One-time Setup

1. Create a Sonatype OSSRH account and claim the `io.wau` group.
2. Generate a GPG key pair (`gpg --gen-key`) and publish the public key to
   a public keyserver (`gpg --keyserver keys.openpgp.org --send-keys <ID>`).
3. Configure `~/.m2/settings.xml` with the Sonatype credentials (under
   `<server><id>ossrh</id>`) and the GPG passphrase
   (`<server><id>gpg.passphrase</id>`).

### Release Steps

```bash
# 1. Ensure CHANGELOG.md and pom.xml version are bumped
# 2. Commit the release-prep change
git commit -am "chore(release): prepare v1.3.4"

# 3. Build, sign, and deploy
mvn clean deploy -P sonatype-oss

# 4. Log in to https://central.sonatype.com/ and close + release the staging repo
```

### Post-release

Once Sonatype has mirrored the artifact, the version is "live" on Maven
Central within 10-30 minutes. Update the badge in `README.md` to point to
`https://central.sonatype.com/artifact/io.wau/wau-sdk` and add a `## [v1.3.4]`
entry at the top of `CHANGELOG.md` with the release date.

---

## 中文贡献指南

### 行为准则

参与本项目请保持尊重、专业的语气。所有 issue、PR、review、讨论都以善意推定为前提。

### 开发环境

- **JDK 17 LTS** — 任意发行版(Temurin / Liberica / Zulu / Corretto)。
- **Maven 3.8+** — 不带 `./mvnw`,请系统级安装。
- **Git 2.30+** — 用于分支管理与 commit 规范。

### 克隆 + 构建

```bash
git clone https://github.com/wau-network/wau-java-sdk.git
cd wau-java-sdk
mvn install
```

首次构建会拉依赖 + 跑完所有测试(123 个单测,9 个测试类)。

### 跑测试

```bash
mvn test                                # 全部
mvn test -Dtest=WauClientTest           # 单类
mvn test -Dtest=WauClientTest#method    # 单方法
```

### 代码风格

- 4 空格缩进,禁用 Tab。
- 行宽软上限 120 列。
- **import 顺序**:`java.*` / `javax.*` 在前,然后第三方(`com.*` / `io.*` /
  `org.*`),最后 `io.wau.sdk.*`,各组之间空行分隔。*`io.wau.sdk.*` 必须在
  标准库 `io.*` 之后,以保持分组稳定。*
- 命名:类型 PascalCase、字段/方法 camelCase、常量 SCREAMING_SNAKE。JSON
  属性名一律 snake_case(经 Jackson `@JsonProperty("snake_case")`)。
- 每个 public 类 + 方法必须有 Javadoc。
- 禁 Lombok(per D63 R23-Java)。

### Pull Request 流程

v1.0.0 GA 之前走单分支工作流(per `feedback-no-branches-until-1.0-0`)。建议
流程:

1. 非平凡改动先开 issue 讨论;
2. 从 `main` 拉 topic 分支;
3. 写代码 + 测试(新 public 方法至少 1 个正向 + 1 个反向 case);
4. 本地 `mvn clean test` 通过;
5. push 并开 PR,标题遵循 commit 规范,关联 `Closes #NNN`;
6. 等 CI 绿(ubuntu + macos × JDK 17);
7. maintainer squash-merge,自动删分支。

### Commit 规范

采用宽松版 [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>
```

- `type`:`feat` / `fix` / `docs` / `test` / `refactor` / `chore` / `release`
- `scope`:`sdk` / `l5` / `ucp` / `mcp` / `bot` / `auth` / `transport` /
  `exception` / `model` / `ci` / `docs`

### 版本同步策略

5 SDK(Go / Python / TypeScript / Rust / Java)走 **lockstep 版本对齐**。
`pom.xml` 里的 `<version>` 必须跟其他 4 SDK 的 build descriptor 同步 bump。
若 PR 改动 public API,务必在 PR 描述里 flag,以便 maintainer 安排跨仓同步。

### Maven Central 发布(Maintainer 专属)

发布到 Maven Central 的 `io.wau:wau-sdk` 坐标,走 [Sonatype OSSRH](https://central.sonatype.org/publish/)
流程。`pom.xml` 需要 `sonatype-oss` profile,带 `<distributionManagement>`、
`maven-gpg-plugin` GPG 签名、Javadoc + sources JAR attach。

一次性配置:

1. 注册 Sonatype OSSRH 账号,认领 `io.wau` group;
2. 生成 GPG key pair 并发布公钥到 `keys.openpgp.org`;
3. 配置 `~/.m2/settings.xml` 加 `<server><id>ossrh</id>` + `<server><id>gpg.passphrase</id>`。

发布步骤:

```bash
mvn clean deploy -P sonatype-oss
# 然后在 https://central.sonatype.com/ 关闭 + 发布 staging repo
```

发布完成后,在 `CHANGELOG.md` 顶部加 `## [v1.3.4] - 2026-MM-DD` 段,并更新
README badge 指向 `https://central.sonatype.com/artifact/io.wau/wau-sdk`。

---

## Questions?

Open an issue or reach out via the [WAU network discussion forum](https://github.com/wau-network/WAU-core-kernel/discussions).
We respond within 1-2 business days.
