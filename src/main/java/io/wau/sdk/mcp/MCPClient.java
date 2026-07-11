package io.wau.sdk.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.transport.HttpTransport;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP client — JSON-RPC 2.0 over HTTP, POST 8 sync tool 到 {@code <baseURL>/mcp} (per D87.9, W3 实装).
 *
 * <h2>8 sync MCP tool</h2>
 * <pre>
 *   health_check / parse_agent_card / send_message
 *   get_task / list_tasks / cancel_task
 *   create_task_push_notification_config / get_extended_agent_card
 * </pre>
 *
 * <h2>JSON-RPC envelope</h2>
 * <pre>
 *   request:  {"jsonrpc":"2.0","method":"tools/call","params":{"name":"...","arguments":{...}},"id":N}
 *   response: {"jsonrpc":"2.0","id":N,"result":{...}}     或
 *             {"jsonrpc":"2.0","id":N,"error":{"code":-32001,"message":"...","data":{...}}}
 * </pre>
 *
 * <h2>5 SDK 一致 (per D13 byte-equal)</h2>
 * <p>8 tool 名 + 8 DTO 字段 snake_case 5 SDK 共享。本类镜像
 * wau-go-sdk/mcpclient/client.go + wau-python-sdk/src/wau_sdk/mcp_client.py +
 * wau-typescript-sdk/src/mcp/client.ts + wau-rust-sdk/src/mcp/client.rs.
 *
 * <h2>W3 stub 友好</h2>
 * <p>2 SSE streaming tool (stream_message + subscribe_to_task) deferred to W5+.
 *
 * <h2>D60 additive</h2>
 * <p>0 改老 {@link io.wau.sdk.WauClient} 5 核心方法 + 老 {@code l5()} / {@code ucp()} getter;
 * 新加 {@code mcp()} getter.
 *
 * @since 0.2.0 (v1.3.2 MCP client 实装, 2026-07-11)
 */
public final class MCPClient {

    /** MCP RPC endpoint path (per D87.1 kernel server design)。 */
    public static final String DEFAULT_ENDPOINT = "/mcp";

    /** User-Agent (per HTTP RFC 7231)。 */
    public static final String DEFAULT_USER_AGENT = "wau-java-sdk/mcpclient/v1.3.2";

    private final String baseUrl;
    private final String endpoint;
    private final String userAgent;
    private final String bearerToken;
    private final HttpTransport transport;
    private final ObjectMapper mapper;
    private final AtomicLong nextId = new AtomicLong(0);

    public MCPClient(String baseUrl, String bearerToken, HttpTransport transport) {
        this(baseUrl, bearerToken, transport, DEFAULT_ENDPOINT, null);
    }

    public MCPClient(String baseUrl, String bearerToken, HttpTransport transport,
                     String endpoint, String userAgent) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("mcpclient: baseUrl is required");
        }
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.endpoint = endpoint != null ? endpoint : DEFAULT_ENDPOINT;
        this.bearerToken = bearerToken;
        this.userAgent = userAgent != null ? userAgent : DEFAULT_USER_AGENT;
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = new ObjectMapper();
    }

    /** 运行时更新 bearer token (W5+ refresh flow 用)。 */
    public MCPClient withBearerToken(String token) {
        return new MCPClient(baseUrl, token, transport, endpoint, userAgent);
    }

    // ── 8 sync tool wrappers ─────────────────────────────

    /** tool 1: health_check */
    public Dto.HealthCheckResult healthCheck(Object target) throws McpException {
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        return callTool(Tools.TOOL_HEALTH_CHECK, args, Dto.HealthCheckResult.class);
    }

    /** tool 2: parse_agent_card */
    public Dto.AgentCard parseAgentCard(Object raw) throws McpException {
        Map<String, Object> args = buildParseAgentCardArgs(raw);
        return callTool(Tools.TOOL_PARSE_AGENT_CARD, args, Dto.AgentCard.class);
    }

    /** tool 3: send_message */
    public Dto.Task sendMessage(Object target, Dto.Message message) throws McpException {
        if (message == null) {
            throw new IllegalArgumentException("mcpclient: message is required");
        }
        if (message.parts == null || message.parts.isEmpty()) {
            throw new IllegalArgumentException("mcpclient: message.parts must have at least 1 item");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("message", message);
        return callTool(Tools.TOOL_SEND_MESSAGE, args, Dto.Task.class);
    }

    /** tool 5: get_task */
    public Dto.Task getTask(Object target, String taskId) throws McpException {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("mcpclient: task_id is required");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("task_id", taskId);
        return callTool(Tools.TOOL_GET_TASK, args, Dto.Task.class);
    }

    /** tool 6: list_tasks */
    public Dto.ListTasksResult listTasks(Object target, Dto.ListTasksFilter filter) throws McpException {
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        if (filter != null) {
            args.put("filter", filter);
        }
        return callTool(Tools.TOOL_LIST_TASKS, args, Dto.ListTasksResult.class);
    }

    /** tool 7: cancel_task */
    public Dto.Task cancelTask(Object target, String taskId) throws McpException {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("mcpclient: task_id is required");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("task_id", taskId);
        return callTool(Tools.TOOL_CANCEL_TASK, args, Dto.Task.class);
    }

    /** tool 9: create_task_push_notification_config */
    public Dto.PushConfigResult createTaskPushNotificationConfig(Object target, Dto.PushConfig config)
            throws McpException {
        if (config == null || config.url == null || config.url.isEmpty()) {
            throw new IllegalArgumentException("mcpclient: config.url is required");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("config", config);
        return callTool(Tools.TOOL_CREATE_TASK_PUSH_NOTIFICATION_CONFIG, args, Dto.PushConfigResult.class);
    }

    /** tool 10: get_extended_agent_card */
    public Dto.ExtendedAgentCard getExtendedAgentCard(Object target) throws McpException {
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        return callTool(Tools.TOOL_GET_EXTENDED_AGENT_CARD, args, Dto.ExtendedAgentCard.class);
    }

    // ── JSON-RPC 2.0 dispatcher ──────────────────────────

    private <T> T callTool(String toolName, Map<String, Object> arguments, Class<T> outClass) throws McpException {
        long id = nextId.incrementAndGet();
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("method", "tools/call");
        envelope.put("params", params);
        envelope.put("id", id);

        Map<String, String> headers = McpAuth.buildHeaders(bearerToken, userAgent);

        String url = baseUrl + endpoint;
        Map<String, Object> response;
        try {
            response = transport.postJson(url, headers, envelope,
                    new TypeReference<Map<String, Object>>() {});
        } catch (WauException e) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "MCP HTTP error: status=" + e.getStatusCode() + " " + e.getMessage(), e);
        }

        return unwrapEnvelope(response, outClass);
    }

    @SuppressWarnings("unchecked")
    private <T> T unwrapEnvelope(Map<String, Object> envelope, Class<T> outClass) throws McpException {
        if (envelope == null) {
            throw new McpException(McpException.ERR_CODE_INVALID_REQUEST, "null response envelope");
        }
        if (envelope.containsKey("error")) {
            Map<String, Object> errMap = (Map<String, Object>) envelope.get("error");
            int code = ((Number) errMap.getOrDefault("code", McpException.ERR_CODE_INTERNAL)).intValue();
            String message = String.valueOf(errMap.getOrDefault("message", ""));
            throw new McpException(code, message);
        }
        if (!envelope.containsKey("result")) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "missing 'result' in response envelope");
        }
        Object result = envelope.get("result");
        if (result == null) return null;
        try {
            return mapper.convertValue(result, outClass);
        } catch (Exception e) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "result deserialization failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private static Object normalizeTarget(Object target) {
        if (target == null) {
            throw new IllegalArgumentException("mcpclient: target is required");
        }
        if (target instanceof String) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", target);
            return m;
        }
        if (target instanceof Map) {
            return target;
        }
        throw new IllegalArgumentException(
                "mcpclient: target must be String or Map, got " + target.getClass().getName());
    }

    private static Map<String, Object> buildParseAgentCardArgs(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("mcpclient: raw is required");
        }
        Map<String, Object> args = new HashMap<>();
        if (raw instanceof String) {
            args.put("raw", (String) raw);
        } else if (raw instanceof byte[]) {
            // bytes → base64 字符串(让 kernel 端可还原)
            args.put("raw", Base64.getEncoder().encodeToString((byte[]) raw));
        } else if (raw instanceof Map) {
            args.put("raw", raw);
        } else {
            throw new IllegalArgumentException(
                    "mcpclient: raw must be String|byte[]|Map, got " + raw.getClass().getName());
        }
        return args;
    }
}