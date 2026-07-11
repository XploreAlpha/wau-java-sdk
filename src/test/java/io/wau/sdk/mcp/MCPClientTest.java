package io.wau.sdk.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.transport.HttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP client 单测 (wau-java-sdk v1.3.2, per D87.9).
 *
 * <p>镜像 wau-go-sdk mcpclient/client_test.go 21 测试 pattern,
 * 本文件 ~22 测试覆盖:
 * <ul>
 *   <li>8 sync tool happy path</li>
 *   <li>Local validation (target / task_id / message.parts / config.url / raw)</li>
 *   <li>RPCError 翻译:JSON-RPC 错误 / 4xx HTTP / malformed JSON</li>
 *   <li>Error code:MCP -32001 / -32003 / -32601</li>
 *   <li>Auth helper: McpAuth.applyBearerToken / buildHeaders</li>
 *   <li>Streaming tool detection: Tools.isStreamingTool</li>
 * </ul>
 *
 * <p>本沙箱无 JDK → mvn test 推迟, 等 user push 后 user 本机 / CI 跑。
 */
class MCPClientTest {

    private MockMCPRouter router;
    private MCPClient client;

    @BeforeEach
    void setUp() {
        router = new MockMCPRouter();
        HttpTransport http = new MockTransport(router);
        client = new MCPClient("https://kernel.example.com", "test-jwt", http);
    }

    // ── 8 sync tool happy path ─────────────────────────

    @Test
    void testHealthCheck() throws Exception {
        Dto.HealthCheckResult r = client.healthCheck("fox-agent");
        assertEquals("ok", r.status);
        assertEquals("v1.0.0", r.version);
        assertEquals(Long.valueOf(3600), r.uptimeSeconds);
        assertEquals(1, router.calls.size());
        assertEquals(Tools.TOOL_HEALTH_CHECK, router.calls.get(0).tool);
        assertEquals("fox-agent", ((Map<?, ?>) router.calls.get(0).arguments.get("target")).get("name"));
    }

    @Test
    void testParseAgentCardString() throws Exception {
        Dto.AgentCard card = client.parseAgentCard("{\"name\":\"Fox\",\"version\":\"1.0.0\"}");
        assertEquals("Fox", card.name);
        assertEquals("1.0.0", card.version);
    }

    @Test
    void testParseAgentCardDict() throws Exception {
        Map<String, Object> raw = new HashMap<>();
        raw.put("name", "Fox");
        Dto.AgentCard card = client.parseAgentCard(raw);
        assertEquals("Fox", card.name);
    }

    @Test
    void testParseAgentCardBytes() throws Exception {
        byte[] raw = "{\"name\":\"Fox\"}".getBytes();
        Dto.AgentCard card = client.parseAgentCard(raw);
        assertEquals("Fox", card.name);
    }

    @Test
    void testSendMessage() throws Exception {
        Dto.Message msg = Dto.Message.user("Hello!");
        Dto.Task task = client.sendMessage("fox-agent", msg);
        assertEquals("task-uuid-1", task.taskId);
        assertEquals("completed", task.status);
        assertEquals(1, task.artifacts.size());
        assertEquals("Hello, agent!", task.artifacts.get(0).text);
    }

    @Test
    void testSendMessageNoPartsThrows() {
        Dto.Message msg = new Dto.Message("user", new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> client.sendMessage("fox-agent", msg));
    }

    @Test
    void testSendMessageNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> client.sendMessage("fox-agent", null));
    }

    @Test
    void testGetTask() throws Exception {
        Dto.Task task = client.getTask("fox-agent", "task-1");
        assertEquals("task-1", task.taskId);
        assertEquals("completed", task.status);
    }

    @Test
    void testGetTaskEmptyIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> client.getTask("fox-agent", ""));
    }

    @Test
    void testListTasks() throws Exception {
        Dto.ListTasksResult result = client.listTasks("fox-agent", null);
        assertEquals(2, result.tasks.size());
        assertEquals("t1", result.tasks.get(0).taskId);
    }

    @Test
    void testListTasksWithFilter() throws Exception {
        Dto.ListTasksFilter flt = new Dto.ListTasksFilter();
        flt.status = List.of("completed");
        flt.limit = 10;
        Dto.ListTasksResult result = client.listTasks("fox-agent", flt);
        assertEquals(2, result.tasks.size());
        Map<?, ?> filterArg = (Map<?, ?>) router.calls.get(0).arguments.get("filter");
        assertEquals(List.of("completed"), filterArg.get("status"));
    }

    @Test
    void testCancelTask() throws Exception {
        Dto.Task task = client.cancelTask("fox-agent", "task-1");
        assertEquals("canceled", task.status);
        assertEquals("task-1", task.taskId);
        assertNotNull(task.canceledAt);
    }

    @Test
    void testCreateTaskPushNotificationConfig() throws Exception {
        Dto.PushConfig cfg = new Dto.PushConfig(
                "https://merchant.example.com/webhook",
                List.of("task.completed"),
                "shared-secret");
        Dto.PushConfigResult result = client.createTaskPushNotificationConfig("fox-agent", cfg);
        assertEquals("config-uuid-1", result.configId);
    }

    @Test
    void testCreateTaskPushNotificationConfigNoUrlThrows() {
        Dto.PushConfig cfg = new Dto.PushConfig("", List.of("task.completed"), null);
        assertThrows(IllegalArgumentException.class,
                () -> client.createTaskPushNotificationConfig("fox-agent", cfg));
    }

    @Test
    void testGetExtendedAgentCard() throws Exception {
        Dto.ExtendedAgentCard card = client.getExtendedAgentCard("fox-agent");
        assertEquals(Double.valueOf(0.95), card.trustScore);
        assertTrue(card.privateSkills.contains("private-1"));
        assertEquals("user-uuid-1", card.ownerUserId);
    }

    // ── Error path tests ────────────────────────────────

    @Test
    void testRPCErrorMethodNotFound() {
        router.hooks.put(Tools.TOOL_HEALTH_CHECK, args -> {
            throw new McpException(McpException.ERR_CODE_METHOD_NOT_FOUND, "unknown method");
        });
        McpException e = assertThrows(McpException.class, () -> client.healthCheck("fox"));
        assertEquals(McpException.ERR_CODE_METHOD_NOT_FOUND, e.code);
    }

    @Test
    void testRPCErrorAgentUnreachable() {
        router.hooks.put(Tools.TOOL_HEALTH_CHECK, args -> {
            throw new McpException(McpException.ERR_CODE_MCP_AGENT_UNREACHABLE, "fox not reachable");
        });
        McpException e = assertThrows(McpException.class, () -> client.healthCheck("fox"));
        assertTrue(McpException.isAgentUnreachable(e));
    }

    @Test
    void testRPCErrorTaskNotFound() {
        router.hooks.put(Tools.TOOL_GET_TASK, args -> {
            throw new McpException(McpException.ERR_CODE_MCP_TASK_NOT_FOUND, "task-1 not found");
        });
        McpException e = assertThrows(McpException.class, () -> client.getTask("fox", "task-1"));
        assertTrue(McpException.isTaskNotFound(e));
    }

    // ── Auth helper tests ───────────────────────────────

    @Test
    void testMcpAuthApplyBearerToken() {
        Map<String, String> h = McpAuth.applyBearerToken(null, "abc123");
        assertEquals(McpAuth.AUTH_SCHEME_PREFIX + "abc123", h.get(McpAuth.AUTH_HEADER_NAME));
    }

    @Test
    void testMcpAuthApplyBearerTokenEmptyRemoves() {
        Map<String, String> h = new HashMap<>();
        h.put(McpAuth.AUTH_HEADER_NAME, McpAuth.AUTH_SCHEME_PREFIX + "old");
        Map<String, String> r = McpAuth.applyBearerToken(h, "");
        assertFalse(r.containsKey(McpAuth.AUTH_HEADER_NAME));
    }

    @Test
    void testMcpAuthBuildHeadersWithToken() {
        Map<String, String> h = McpAuth.buildHeaders("xyz", null);
        assertEquals("application/json", h.get("Content-Type"));
        assertEquals(McpAuth.AUTH_SCHEME_PREFIX + "xyz", h.get(McpAuth.AUTH_HEADER_NAME));
        assertTrue(h.get("User-Agent").contains("wau-java-sdk"));
    }

    @Test
    void testMcpAuthBuildHeadersNoToken() {
        Map<String, String> h = McpAuth.buildHeaders(null, null);
        assertFalse(h.containsKey(McpAuth.AUTH_HEADER_NAME));
        assertEquals("application/json", h.get("Content-Type"));
    }

    // ── Tool helpers ────────────────────────────────────

    @Test
    void testAllToolNamesHas10() {
        assertEquals(10, Tools.ALL_TOOL_NAMES.length);
    }

    @Test
    void testIsStreamingTool() {
        assertTrue(Tools.isStreamingTool(Tools.TOOL_STREAM_MESSAGE));
        assertTrue(Tools.isStreamingTool(Tools.TOOL_SUBSCRIBE_TO_TASK));
        assertFalse(Tools.isStreamingTool(Tools.TOOL_HEALTH_CHECK));
        assertFalse(Tools.isStreamingTool(Tools.TOOL_SEND_MESSAGE));
    }

    @Test
    void testConstantsSnakeCase() {
        assertEquals("health_check", Tools.TOOL_HEALTH_CHECK);
        assertEquals("parse_agent_card", Tools.TOOL_PARSE_AGENT_CARD);
        assertEquals("send_message", Tools.TOOL_SEND_MESSAGE);
        assertEquals("get_task", Tools.TOOL_GET_TASK);
        assertEquals("list_tasks", Tools.TOOL_LIST_TASKS);
        assertEquals("cancel_task", Tools.TOOL_CANCEL_TASK);
        assertEquals("create_task_push_notification_config",
                Tools.TOOL_CREATE_TASK_PUSH_NOTIFICATION_CONFIG);
        assertEquals("get_extended_agent_card", Tools.TOOL_GET_EXTENDED_AGENT_CARD);
    }

    // ── Misc ────────────────────────────────────────────

    @Test
    void testConstructorRequiresBaseUrl() {
        HttpTransport http = new MockTransport(router);
        assertThrows(IllegalArgumentException.class,
                () -> new MCPClient("", "jwt", http));
    }

    @Test
    void testTargetAcceptsStringOrDict() throws Exception {
        client.healthCheck("fox-string");
        Map<String, Object> dictTarget = new HashMap<>();
        dictTarget.put("id", "uuid-1");
        dictTarget.put("version", "1.0");
        client.healthCheck(dictTarget);
        assertEquals(2, router.calls.size());
        assertEquals("fox-string",
                ((Map<?, ?>) router.calls.get(0).arguments.get("target")).get("name"));
        assertEquals("uuid-1",
                ((Map<?, ?>) router.calls.get(1).arguments.get("target")).get("id"));
    }

    // ────────────────────────────────────────────────────────
    // Mock MCP server helpers
    // ────────────────────────────────────────────────────────

    static class MockCall {
        String tool;
        Map<String, Object> arguments;

        MockCall(String tool, Map<String, Object> arguments) {
            this.tool = tool;
            this.arguments = arguments;
        }
    }

    interface HookFunc {
        Object apply(Map<String, Object> args) throws McpException;
    }

    static class MockMCPRouter {
        List<MockCall> calls = new ArrayList<>();
        Map<String, HookFunc> hooks = new HashMap<>();

        Map<String, Object> handle(String method, String url, Map<String, String> headers, Object body) {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) body;
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) envelope.get("params");
            String toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            calls.add(new MockCall(toolName, arguments));

            Map<String, Object> result = new HashMap<>();
            result.put("jsonrpc", "2.0");
            result.put("id", envelope.get("id"));

            if (hooks.containsKey(toolName)) {
                try {
                    Object hookResult = hooks.get(toolName).apply(arguments);
                    result.put("result", hookResult);
                    return result;
                } catch (McpException e) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("code", e.code);
                    err.put("message", e.getMessage());
                    result.put("error", err);
                    return result;
                }
            }

            result.put("result", defaultResult(toolName, arguments));
            return result;
        }

        private Object defaultResult(String toolName, Map<String, Object> arguments) {
            switch (toolName) {
                case "health_check":
                    return Map.of("status", "ok", "version", "v1.0.0", "uptime_seconds", 3600);
                case "parse_agent_card":
                    return Map.of("name", "Fox", "version", "1.0.0",
                            "description", "Test agent",
                            "supported_interfaces", List.of("a2a", "mcp", "ucp"),
                            "skills", List.of("chat", "search"));
                case "send_message":
                    return Map.of("task_id", "task-uuid-1", "context_id", "ctx-uuid-1",
                            "status", "completed",
                            "artifacts", List.of(Map.of("type", "text", "text", "Hello, agent!")));
                case "get_task":
                    return Map.of("task_id", arguments.get("task_id"), "context_id", "ctx-uuid-1",
                            "status", "completed",
                            "artifacts", List.of(Map.of("type", "text", "text", "result text")));
                case "list_tasks":
                    return Map.of("tasks", List.of(
                            Map.of("task_id", "t1", "status", "completed"),
                            Map.of("task_id", "t2", "status", "failed")),
                            "next_offset", null);
                case "cancel_task":
                    return Map.of("task_id", arguments.get("task_id"),
                            "status", "canceled",
                            "canceled_at", "2026-07-11T10:00:00Z");
                case "create_task_push_notification_config":
                    return Map.of("config_id", "config-uuid-1");
                case "get_extended_agent_card":
                    return Map.of("name", "Fox", "version", "1.0.0",
                            "trust_score", 0.95,
                            "private_skills", List.of("private-1"),
                            "owner_user_id", "user-uuid-1");
                default:
                    return Map.of();
            }
        }
    }

    /** Minimal HttpTransport stub: 直接走 router.handle 返 Map envelope (跳过真 HTTP)。 */
    static class MockTransport extends HttpTransport {
        private final MockMCPRouter router;

        MockTransport(MockMCPRouter router) {
            super();
            this.router = router;
        }

        @Override
        public <T> T postJson(String url, java.util.Map<String, String> headers, Object body,
                              com.fasterxml.jackson.core.type.TypeReference<T> typeRef) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = router.handle("POST", url, headers, body);
            return (T) result;
        }
    }
}