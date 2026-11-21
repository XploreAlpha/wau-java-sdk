package io.wau.sdk;

import io.wau.sdk.auth.BotHeaderParser;
import io.wau.sdk.model.Agent;
import io.wau.sdk.model.Bot;
import io.wau.sdk.model.CostEvent;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import io.wau.sdk.model.Provider;
import io.wau.sdk.model.SampleRequest;
import io.wau.sdk.model.SampleResponse;
import io.wau.sdk.transport.HttpTransport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WauClient — wau-java-sdk 顶层 client(per D63 §5.1,5 核心方法)。
 *
 * <h2>5 核心方法</h2>
 * <table>
 *   <tr><th>#</th><th>方法</th><th>HTTP</th><th>用途</th><th>关联 M</th></tr>
 *   <tr><td>1</td><td>{@link #sendMessage}</td><td>POST /v1/bots/{id}/messages</td><td>M5 下游调用</td><td>M5</td></tr>
 *   <tr><td>2</td><td>{@link #registerBot}</td><td>POST /v1/registry/bots</td><td>Bot 注册</td><td>M10 N3.3</td></tr>
 *   <tr><td>3</td><td>{@link #getAgent}</td><td>GET /v1/registry/agents/{id}</td><td>Agent 元数据</td><td>M8 W4</td></tr>
 *   <tr><td>4</td><td>{@link #sampleLLM}</td><td>POST /v1/llm/sample</td><td>LLM 决策采样</td><td>M3 W3</td></tr>
 *   <tr><td>5</td><td>{@link #recordCost}</td><td>POST /v1/llm/cost</td><td>成本事件上报</td><td>M3 §3.1</td></tr>
 * </table>
 *
 * <h2>5 SDK 一致</h2>
 * <p>5 方法对齐 wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk
 * 同名方法,per D63 §5.1 同步策略。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public final class WauClient {

    private final WauConfig cfg;
    private final HttpTransport http;

    public WauClient(WauConfig cfg) {
        this(cfg, new HttpTransport());
    }

    public WauClient(WauConfig cfg, HttpTransport http) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.http = Objects.requireNonNull(http, "http");
    }

    /** 暴露给测试 / e2e 用. */
    public WauConfig config() { return cfg; }
    public HttpTransport http() { return http; }

    // ============================================================
    // ⭐L5 包管理器(per D72/D73/D74,2026-07-10,v1.3.3 L5 deadline 提前)
    //   - 5 方法对齐 wau-go-sdk L5Service
    //   - 通过 l5() getter 暴露,避免单仓改动面扩散
    // ============================================================

    /**
     * L5 包管理器 client(per D72 wau-toolkit v2.0 OS-level install / uninstall / update / search / login)。
     *
     * <p>例:
     * <pre>{@code
     *   InstallResponse r = client.l5().install(
     *       new InstallRequest("alice", "weather-agent", "1.2.3"));
     *   System.out.println("installed agent_id=" + r.getAgentId());
     * }</pre>
     *
     * <p>5 SDK 一致:与 wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk
     * 同名方法 + snake_case JSON 协议(per D13)。
     *
     * <p>D60 additive:0 改老 5 核心方法 + 0 改 WauConfig / WauClient 老字段;
     * 仅新加 l5() getter + L5Service constructor injection。
     *
     * @since 0.2.0-preview (v1.3.3 L5 deadline 提前, 2026-07-10)
     */
    public io.wau.sdk.l5.L5Service l5() {
        if (l5 == null) {
            l5 = new io.wau.sdk.l5.L5Service(cfg, http);
        }
        return l5;
    }

    private io.wau.sdk.l5.L5Service l5;

    // ============================================================
    // UCP client(per D88.9,2026-07-11 v1.3.3 UCP deadline 提前)
    //   - 11 commerce tool wrapper(per [[process/2026-07-11-W3-UCP-client-SDK-design]] §三)
    //   - 通过 ucp() getter 暴露,避免单仓改动面扩散
    // ============================================================

    /**
     * UCP commerce client(per D88 UCP server protocol + 5 SDK UCP client v1.3.3)。
     *
     * <p>例:
     * <pre>{@code
     *   Dto.Product p = client.ucp().getProduct("p-1");
     *   Dto.Cart cart = client.ucp().addToCart("p-1", 2);
     * }</pre>
     *
     * <p>5 SDK 一致:与 wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk
     * 同名方法 + snake_case JSON 协议(per D13)。
     *
     * <p>D60 additive:0 改老 5 核心方法 + 0 改 WauConfig / WauClient 老字段;
     * 仅新加 ucp() getter + UCPClient constructor injection。
     */
    public io.wau.sdk.ucp.UCPClient ucp() {
        if (ucp == null) {
            ucp = new io.wau.sdk.ucp.UCPClient(
                    cfg.wauEdgeUrl(),
                    http,
                    new io.wau.sdk.ucp.UcpAuth(cfg.botToken(), null));
        }
        return ucp;
    }

    private io.wau.sdk.ucp.UCPClient ucp;

    // ============================================================
    // MCP client(per D87.9,2026-07-11 v1.3.2 MCP client 实装)
    //   - 8 sync MCP tool wrapper(per [[process/2026-07-10-W3-MCP-client-SDK-design]] §三)
    //   - 通过 mcp() getter 暴露,避免单仓改动面扩散
    //   - 2 SSE streaming tool deferred to W5+
    // ============================================================

    /**
     * MCP client(per D87 kernel MCP server + 5 SDK MCP client v1.3.2)。
     *
     * <p>例:
     * <pre>{@code
     *   Dto.AgentCard card = client.mcp().parseAgentCard("{\"name\":\"Fox\"}");
     *   Dto.Task task = client.mcp().sendMessage("fox-agent", Dto.Message.user("Hi"));
     * }</pre>
     *
     * <p>5 SDK 一致:与 wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk
     * 同名方法 + snake_case JSON 协议(per D13)。
     *
     * <p>D60 additive:0 改老 5 核心方法 + 0 改 WauConfig / WauClient 老字段;
     * 仅新加 mcp() getter + MCPClient constructor injection。
     */
    public io.wau.sdk.mcp.MCPClient mcp() {
        if (mcp == null) {
            mcp = new io.wau.sdk.mcp.MCPClient(
                    cfg.wauEdgeUrl(),
                    cfg.botToken(),
                    http);
        }
        return mcp;
    }

    private io.wau.sdk.mcp.MCPClient mcp;

    // ============================================================
    // 1. sendMessage — M5 八平台下游消费
    // ============================================================

    /**
     * 发消息到 wau-channel(per M5 实装:8 平台 Telegram/Discord/Slack/Feishu/DingTalk/QQ/Email/WeChat)。
     *
     * @param msg incoming message DTO(9 字段对齐 4 SDK per D13+D78+D80)
     * @return outgoing message(reply + provider + tokens)
     */
    public OutgoingMessage sendMessage(IncomingMessage msg) {
        Objects.requireNonNull(msg, "msg");
        // 默认 botId 来自 cfg(D78)
        if (msg.getBotId() == null || msg.getBotId().isBlank()) {
            msg.setBotId(cfg.botId());
        }
        // 默认 ownerUserId 来自 cfg
        if (msg.getOwnerUserId() == null && cfg.ownerUserId() != null) {
            msg.setOwnerUserId(Long.parseLong(cfg.ownerUserId()));
        }
        String url = cfg.wauEdgeUrl()
                + "/v1/bots/" + escapePathSegment(cfg.botId())
                + "/messages";
        Map<String, String> headers = botHeaders(msg.getUserId(),
                msg.getOwnerUserId() != null ? msg.getOwnerUserId() : (cfg.ownerUserId() != null ? Long.parseLong(cfg.ownerUserId()) : null));
        return http.postJson(url, headers, msg, OutgoingMessage.class);
    }

    // ============================================================
    // 2. registerBot — M10 N3.3
    // ============================================================

    /**
     * 注册 bot 到 wau-registry(per M10)。
     *
     * @param botId 公开 bot ID(格式 {@code bot:<tenant>:<botid>})
     * @param token bot token
     * @return 注册后的 bot metadata
     */
    public Bot registerBot(String botId, String token) {
        Objects.requireNonNull(botId, "botId");
        Objects.requireNonNull(token, "token");
        if (!BotHeaderParser.isValidBotId(botId)) {
            throw new IllegalArgumentException("botId must match 'bot:<tenant>:<botid>', got: " + botId);
        }
        String url = cfg.wauEdgeUrl() + "/v1/registry/bots";
        Map<String, String> headers = baseHeaders();
        if (cfg.botToken() != null) {
            headers.put("Authorization", "Bearer " + cfg.botToken());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("bot_id", botId);
        body.put("token", token);
        if (cfg.ownerUserId() != null) {
            body.put("owner_user_id", Long.parseLong(cfg.ownerUserId()));
        }
        return http.postJson(url, headers, body, Bot.class);
    }

    // ============================================================
    // 3. getAgent — M8 W4
    // ============================================================

    /**
     * 拉 agent 元数据(per M8 W4)。
     *
     * @param agentId agent 公开 ID
     * @return agent metadata
     */
    public Agent getAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        String url = cfg.wauEdgeUrl() + "/v1/registry/agents/" + escapePathSegment(agentId);
        return http.get(url, baseHeaders(), Agent.class);
    }

    // ============================================================
    // registerAgent — 2026-11-19 新增(A2A v1.0 / 注册面对齐)
    // ============================================================

    /**
     * 把本 agent 注册进 WAU registry。
     *
     * <p>★ 2026-11-19 新增:Java SDK 之前**没有**注册方法
     * (只有 wau-store 语义的 {@link #getAgent})。
     *
     * <p><b>authToken</b> 是**调用凭证** —— kernel 取候选时需要它拼
     * {@code Authorization} 头去调边缘 agent
     * (registry 的注册 = 整卡覆盖写,不带 token 就没有 token)。
     *
     * @param name        agent 名(全局唯一)
     * @param url         kernel 能 POST 的端点根路径
     * @param skills      能力标签
     * @param universes   universe 标签(非安全边界,只是路由分组)
     * @param authToken   调用凭证(可空)
     */
    public void registerAgent(String name, String url, java.util.List<String> skills,
                               java.util.List<String> universes, String authToken) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        body.put("url", url);
        body.put("skills", skills == null ? java.util.List.of() : skills);
        body.put("universes", universes == null ? java.util.List.of() : universes);
        if (authToken != null && !authToken.isEmpty()) {
            body.put("auth_token", authToken);
        }
        http.postJson(cfg.wauEdgeUrl() + "/registry/agents/register", baseHeaders(), body, Void.class);
    }

    // ============================================================
    // 4. sampleLLM — M3 W3 (cost-aware Thompson Sampling)
    // ============================================================

    /**
     * LLM 决策采样(per M3 W3 子项 2 cost-aware Thompson Sampling)。
     *
     * <p>调 wau-llm-router `POST /v1/llm/sample`,返回最优 provider 决策。
     *
     * @param req sampling 请求(prompt + universe + capabilities)
     * @return 采样响应(provider + model + estimated_cost_usd)
     */
    public SampleResponse sampleLLM(SampleRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/llm/sample";
        return http.postJson(url, baseHeaders(), req, SampleResponse.class);
    }

    // ============================================================
    // 5. recordCost — M3 §3.1 cost feeder
    // ============================================================

    /**
     * 记录 LLM 调用成本(per M3 §3.1)。
     *
     * <p>调到 wau-store `POST /v1/llm/cost`,生产路径被 wau-edge cost feeder 异步批量调用,
     * 本方法支持同步路径(测试 / 简单 SDK 调用)。
     *
     * @param event cost event DTO
     */
    public void recordCost(CostEvent event) {
        Objects.requireNonNull(event, "event");
        String url = cfg.wauEdgeUrl() + "/v1/llm/cost";
        // 默认 botId
        if (event.getBotId() == null || event.getBotId().isBlank()) {
            event.setBotId(cfg.botId());
        }
        // 上游返 {"accepted":true} / 202 Accepted,这里只关心是否抛异常
        http.postJson(url, baseHeaders(), event, java.util.Map.class);
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private Map<String, String> baseHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("X-WAU-Client", "wau-java-sdk/1.4.0");
        if (cfg.botToken() != null) {
            h.put("Authorization", "Bearer " + cfg.botToken());
        }
        return h;
    }

    // ============================================================
    // IntentService(2026-11-19 实装)
    //   - kernel 的 /v1/intent/parse + /v1/intent/score
    //   - 通过 intent() getter 暴露,避免单仓改动面扩散
    // ============================================================

    /**
     * Intent client —— intent 解析 + agent 评分。
     *
     * <p>★ 2026-11-19:Java SDK 之前**完全没有** intent 服务。
     * kernel 的 {@code /v1/intent/parse} + {@code /v1/intent/score} 早已可用
     * (2026-11-19 对着生产服务器实测确认),现在补上。
     *
     * <p>例:
     * <pre>{@code
     *   var entry = client.intent().parseIntent("帮我查血糖趋势");
     *   var scores = client.intent().scoreAgents(List.of("hermes-shop01"), null, null, null, null);
     * }</pre>
     */
    public io.wau.sdk.intent.IntentService intent() {
        if (intent == null) {
            intent = new io.wau.sdk.intent.IntentService(http, cfg.wauEdgeUrl(), baseHeaders());
        }
        return intent;
    }

    private io.wau.sdk.intent.IntentService intent;

    // ============================================================
    // A2AClient(2026-11-19 实装)
    //   - kernel 的 /v1/intent/* 之外的 A2A v1.0 面
    // ============================================================

    /**
     * A2A v1.0 client —— 调外部 agent。
     *
     * <p>★ 2026-11-19:5 个 SDK 之前都没有 A2A 客户端。kernel 已经在用
     * A2A v1.0 调外部 agent 了(ZeroClaw / Hermes / 边缘 agent)
     * ⇒ SDK 侧要能直连 A2A 才能自己组网。
     *
     * <p>例:
     * <pre>{@code
     *   String reply = client.a2a().sendText("http://10.0.0.1:9901/", "你好", token, "");
     * }</pre>
     */
    public io.wau.sdk.a2a.A2AClient a2a() {
        if (a2a == null) {
            a2a = new io.wau.sdk.a2a.A2AClient(http, cfg.wauEdgeUrl(), baseHeaders());
        }
        return a2a;
    }

    private io.wau.sdk.a2a.A2AClient a2a;

    /** X-WAU-Bot + X-WAU-User + X-WAU-Owner 3 header(per D78+D80). */
    private Map<String, String> botHeaders(String userId, Long ownerUserId) {
        Map<String, String> h = baseHeaders();
        BotHeaderParser.build(cfg.botId(), userId, ownerUserId).forEach(h::put);
        return h;
    }

    /** URL path segment 简单转义(防 path traversal). */
    static String escapePathSegment(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9_:-]", "_");
    }

    /** Provider helper(per M3 子项 3 seed 50+ providers). */
    public java.util.List<Provider> listProviders() {
        String url = cfg.wauEdgeUrl() + "/v1/llm/providers";
        return http.get(url, baseHeaders(),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Provider>>() {});
    }
}
