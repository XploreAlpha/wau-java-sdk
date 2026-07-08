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
        h.put("X-WAU-Client", "wau-java-sdk/0.1.0-preview");
        if (cfg.botToken() != null) {
            h.put("Authorization", "Bearer " + cfg.botToken());
        }
        return h;
    }

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
