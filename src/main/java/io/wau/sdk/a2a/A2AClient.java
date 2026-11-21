package io.wau.sdk.a2a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.wau.sdk.transport.HttpTransport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A2A v1.0 客户端 —— 调外部 agent。
 *
 * <h2>2026-11-19 新增</h2>
 *
 * <p>5 个 SDK 之前都没有 A2A 客户端 —— 只有 MCP 的 {@code send_message} tool
 * (那走 MCP,不是 A2A)。而 kernel 已经在用 A2A v1.0 调外部 agent 了
 * (ZeroClaw / Hermes / 边缘 agent)⇒ SDK 侧要能直连 A2A 才能自己组网。
 *
 * <h2>A2A v1.0 的线格式(实测,对着 hermes-shop01:9901)</h2>
 *
 * <pre>
 * POST {agent_url}
 * Content-Type: application/json
 * Accept: application/json
 * A2A-Version: 1.0              ← ★ 必带,缺失或值不匹配服务端直接拒
 * Authorization: Bearer &lt;token&gt;  ← 可选(agent 开 pairing 认证时必带)
 *
 * {"jsonrpc":"2.0","id":"&lt;uuid&gt;","method":"SendMessage",
 *  "params":{"message":{"messageId":"&lt;uuid&gt;","role":"user",
 *                       "parts":[{"text":"你好"}]}}}
 * </pre>
 *
 * <p>⚠️ v1.0 的 Part 没有 {@code kind} 字段 —— 内容分支本身就是判别器
 * ({@code text} / {@code raw} / {@code url} / {@code data})。
 */
public final class A2AClient {

    /** A2A v1.0 标准方法名(首字母大写;v0.3.0 是 {@code message/send})。 */
    public static final String METHOD_SEND_MESSAGE = "SendMessage";

    /** A2A v1.0 协议版本 —— 每次请求都必须带这个头。 */
    public static final String A2A_PROTOCOL_VERSION = "1.0";

    private final HttpTransport http;
    private final String baseUrl;
    private final Map<String, String> headers;

    public A2AClient(HttpTransport http, String baseUrl, Map<String, String> headers) {
        this.http = Objects.requireNonNull(http, "http must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    /** JSON-RPC 响应外壳。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JsonRpcResponse {
        @JsonProperty("result")
        public Map<String, Object> result;
        @JsonProperty("error")
        public Map<String, Object> error;
    }

    // ============================================================
    // 方法
    // ============================================================

    /**
     * 发一条 A2A 消息,返回**原始** JSON-RPC result。
     *
     * @param agentUrl  agent 的完整地址(如 {@code http://10.0.0.1:9901/})
     * @param text      消息正文
     * @param token     可选,agent 开 pairing 认证时的 Bearer token
     * @param contextId 可选,多轮对话关联
     */
    public Map<String, Object> sendMessage(String agentUrl, String text, String token, String contextId) {
        Objects.requireNonNull(agentUrl, "agentUrl must not be null");
        Objects.requireNonNull(text, "text must not be null");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", UUID.randomUUID().toString());
        message.put("role", "user");
        message.put("parts", List.of(Map.of("text", text)));
        if (contextId != null && !contextId.isEmpty()) {
            message.put("contextId", contextId);
        }

        Map<String, Object> params = Map.of("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", METHOD_SEND_MESSAGE);
        body.put("params", params);

        Map<String, String> reqHeaders = new LinkedHashMap<>(headers);
        reqHeaders.put("A2A-Version", A2A_PROTOCOL_VERSION);
        if (token != null && !token.isEmpty()) {
            reqHeaders.put("Authorization", "Bearer " + token);
        }

        JsonRpcResponse resp =
                http.postJson(agentUrl, reqHeaders, body, JsonRpcResponse.class);
        if (resp == null) {
            return Collections.emptyMap();
        }
        if (resp.error != null && !resp.error.isEmpty()) {
            Object code = resp.error.get("code");
            Object msg = resp.error.get("message");
            throw new io.wau.sdk.exception.WauServerException(
                    code instanceof Number ? ((Number) code).intValue() : -32603,
                    "a2a error: " + (msg == null ? "" : msg.toString()));
        }
        return resp.result == null ? Collections.emptyMap() : resp.result;
    }

    /**
     * 发一条 A2A 消息,直接返回回复文本。
     */
    public String sendText(String agentUrl, String text, String token, String contextId) {
        return extractText(sendMessage(agentUrl, text, token, contextId));
    }

    /**
     * 拉 agent 卡片(GET {@code {agentUrl}/.well-known/agent-card.json})。
     */
    public Map<String, Object> getAgentCard(String agentUrl) {
        String base = agentUrl.endsWith("/") ? agentUrl.substring(0, agentUrl.length() - 1) : agentUrl;
        Map<String, Object> card = http.get(
                base + "/.well-known/agent-card.json",
                headers,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        return card == null ? Collections.emptyMap() : card;
    }

    /**
     * 从 A2A 结构的任意节点里递归提取文本。
     *
     * <p>覆盖 v1.0 的三种形状:
     * <ul>
     *   <li>task 分支    : {@code {"task":{"status":{"message":{"parts":[...]}},"artifacts":[{"parts":[...]}]}}}
     *   <li>message 分支 : {@code {"message":{"parts":[...]}}}
     *   <li>legacy v0.3.0: {@code {"kind":"task","artifacts":[{"parts":[{"kind":"text","text":"..."}]}]}}
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static String extractText(Object node) {
        List<String> out = new ArrayList<>();
        walk(node, out);
        return String.join("\n", out);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, List<String> out) {
        if (node instanceof Map) {
            Map<String, Object> obj = (Map<String, Object>) node;
            // Part:v1.0 用 "text" 字段本身当判别器;legacy 用 "kind":"text"
            Object txt = obj.get("text");
            if (txt instanceof String && !((String) txt).isEmpty() && !out.contains(txt)) {
                out.add((String) txt);
            }
            for (String key : new String[] {"message", "status", "artifacts", "parts", "result", "task"}) {
                if (obj.containsKey(key)) {
                    walk(obj.get(key), out);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<Object>) node) {
                walk(item, out);
            }
        }
    }
}
