package io.wau.sdk.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.wau.sdk.transport.HttpTransport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IntentService — intent 解析 + agent 评分。
 *
 * <h2>2026-11-19:实装</h2>
 *
 * <p>Java SDK 之前**完全没有** intent 服务(其它 4 个 SDK 至少还有 stub)。
 *
 * <p>kernel 侧早就有可用的 HTTP 端点(2026-11-19 对着生产服务器 100.125.139.145 实测确认):
 *
 * <pre>
 * POST /v1/intent/parse  {message, source_universe?}
 *     → 200 {"intent":{"type","required_skills","urgency","estimated_complexity","source_universe"}}
 *
 * POST /v1/intent/score  {agents[], required_skills[], intent_type, urgency, source_universe?}
 *     → 200 {"scores":[{"AgentID","TotalScore","Dimensions":{...},"Rank"}]}
 * </pre>
 *
 * <h2>形状上的坑(实测)</h2>
 *
 * <p>{@code /v1/intent/score} 返回的 score 元素用的是 <b>Go 字段名</b>
 * ({@code AgentID} / {@code TotalScore} / {@code Dimensions}),不是 snake_case
 * —— 因为 {@code internal/scoring.AgentScore} 这个 struct <b>没有 json tag</b>。
 * 而 {@code /registry/agents/{name}/score} 返回的是 camelCase
 * ({@code agentId} / {@code totalScore} / {@code dimensions})
 * ⇒ 两个端点形状不一致,本类两种都认。
 */
public final class IntentService {

    private final HttpTransport http;
    private final String baseUrl;
    private final Map<String, String> headers;

    public IntentService(HttpTransport http, String baseUrl, Map<String, String> headers) {
        this.http = Objects.requireNonNull(http, "http must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    // ============================================================
    // DTO
    // ============================================================

    /**
     * {@code POST /v1/intent/parse} 的解析结果。
     * 字段名跟 kernel 的 {@code proxy.IntentEntry} 1:1(snake_case)。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class IntentEntry {
        @JsonProperty("type")
        public String type = "";
        @JsonProperty("required_skills")
        public List<String> requiredSkills = new ArrayList<>();
        @JsonProperty("urgency")
        public String urgency = "";
        @JsonProperty("estimated_complexity")
        public int estimatedComplexity;
        @JsonProperty("source_universe")
        public String sourceUniverse = "";
    }

    /**
     * {@code POST /v1/intent/score} 里一个 agent 的评分。
     *
     * <p>kernel 返回 Go 字段名({@code AgentID}/{@code TotalScore}/{@code Dimensions}),
     * 这里两套拼写都认。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class IntentAgentScore {
        @JsonProperty("AgentID")
        public String agentIdGo = "";
        @JsonProperty("agentId")
        public String agentIdCamel = "";
        @JsonProperty("TotalScore")
        public Double totalScoreGo;
        @JsonProperty("totalScore")
        public Double totalScoreCamel;
        @JsonProperty("Dimensions")
        public Map<String, Double> dimensionsGo;
        @JsonProperty("dimensions")
        public Map<String, Double> dimensionsCamel;
        @JsonProperty("Rank")
        public Integer rankGo;
        @JsonProperty("rank")
        public Integer rankCamel;

        public String agentId() {
            return agentIdGo != null && !agentIdGo.isEmpty() ? agentIdGo : (agentIdCamel == null ? "" : agentIdCamel);
        }

        public double totalScore() {
            if (totalScoreGo != null) return totalScoreGo;
            return totalScoreCamel == null ? 0.0 : totalScoreCamel;
        }

        public int rank() {
            if (rankGo != null) return rankGo;
            return rankCamel == null ? 0 : rankCamel;
        }

        public Map<String, Double> dimensions() {
            if (dimensionsGo != null && !dimensionsGo.isEmpty()) return dimensionsGo;
            return dimensionsCamel == null ? Collections.emptyMap() : dimensionsCamel;
        }
    }

    /** {@code POST /v1/intent/parse} 的响应外壳({@code {"intent":{...}}})。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class IntentParseResponse {
        @JsonProperty("intent")
        public IntentEntry intent = new IntentEntry();
    }

    /** {@code POST /v1/intent/score} 的响应外壳。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class IntentScoreResponse {
        @JsonProperty("scores")
        public List<IntentAgentScore> scores = new ArrayList<>();
    }

    // ============================================================
    // 方法
    // ============================================================

    /**
     * 解析用户输入的 intent —— 对应 {@code POST /v1/intent/parse}。
     */
    public IntentEntry parseIntent(String text) {
        return parseIntent(text, "");
    }

    /**
     * 带 source_universe 的 intent 解析。
     */
    public IntentEntry parseIntent(String text, String sourceUniverse) {
        Objects.requireNonNull(text, "text must not be null");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", text);
        if (sourceUniverse != null && !sourceUniverse.isEmpty()) {
            body.put("source_universe", sourceUniverse);
        }
        IntentParseResponse resp =
                http.postJson(baseUrl + "/v1/intent/parse", headers, body, IntentParseResponse.class);
        return resp == null || resp.intent == null ? new IntentEntry() : resp.intent;
    }

    /**
     * 给 agent 列表打分 —— 对应 {@code POST /v1/intent/score}(按 Rank 升序)。
     */
    public List<IntentAgentScore> scoreAgents(
            List<String> agents,
            List<String> requiredSkills,
            String intentType,
            String urgency,
            String sourceUniverse) {
        Objects.requireNonNull(agents, "agents must not be null");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agents", agents);
        if (requiredSkills != null && !requiredSkills.isEmpty()) {
            body.put("required_skills", requiredSkills);
        }
        if (intentType != null && !intentType.isEmpty()) {
            body.put("intent_type", intentType);
        }
        if (urgency != null && !urgency.isEmpty()) {
            body.put("urgency", urgency);
        }
        if (sourceUniverse != null && !sourceUniverse.isEmpty()) {
            body.put("source_universe", sourceUniverse);
        }
        IntentScoreResponse resp =
                http.postJson(baseUrl + "/v1/intent/score", headers, body, IntentScoreResponse.class);
        List<IntentAgentScore> scores =
                resp == null || resp.scores == null ? new ArrayList<>() : new ArrayList<>(resp.scores);
        scores.sort((a, b) -> {
            int ra = a.rank() == 0 ? Integer.MAX_VALUE : a.rank();
            int rb = b.rank() == 0 ? Integer.MAX_VALUE : b.rank();
            if (ra != rb) return Integer.compare(ra, rb);
            return Double.compare(b.totalScore(), a.totalScore());
        });
        return scores;
    }

    /**
     * kernel 健康检查 —— 对应 {@code GET /health}。
     */
    public Map<String, Object> healthCheck() {
        return http.get(baseUrl + "/health", headers, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
    }
}
