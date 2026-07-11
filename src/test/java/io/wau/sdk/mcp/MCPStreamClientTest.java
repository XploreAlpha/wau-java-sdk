package io.wau.sdk.mcp;

import io.wau.sdk.transport.HttpTransport;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP SSE streaming client 单测 (wau-java-sdk v1.3.3, per D89.A.9).
 *
 * <p>镜像 wau-go-sdk mcpclient/stream_client_test.go 22+ 测试 pattern:
 * <ul>
 *   <li>Phase 1 POST happy path / 401 / RPC error envelope</li>
 *   <li>7 种 SSE frame (open / message / artifact / task_status / task_complete / close / error)</li>
 *   <li>Poll pattern: timeout, blocking, multi event</li>
 *   <li>StreamOptions: include_history / include_artifacts / default</li>
 *   <li>Cancel idempotency + concurrent streams</li>
 *   <li>Stream-id mismatch (D89 SOP test 18) + invalid JSON frame</li>
 * </ul>
 *
 * <p>本沙箱无 JDK → mvn test 推迟, 等 user push 后 user 本机 / CI 跑。
 * 测试用真实 OkHttp MockWebServer (per D89 SOP) 模拟 kernel SSE server。
 * Phase 1 (POST /mcp) 用 HttpTransport.Sender interface (final HttpTransport 不能 extends)。
 */
class MCPStreamClientTest {

    private MockStreamRouter router;
    private MockWebServer mws;
    private MCPStreamClient client;

    @BeforeEach
    void setUp() throws Exception {
        router = new MockStreamRouter();
        mws = new MockWebServer();
        mws.start();
        HttpTransport transport = new HttpTransport(new RouterSender(router, mws));
        client = new MCPStreamClient(urlBase(), "test-jwt",
                transport, new OkHttpClient.Builder().build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mws != null) mws.shutdown();
    }

    private String urlBase() {
        return mws.url("").toString().replaceAll("/$", "");
    }

    // ── Phase 1 (POST) tests ─────────────────────────

    @Test
    void testStreamMessageOpenFrame() throws Exception {
        router.streamId = "stream-open-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\",\"timestamp\":\"2026-07-11T10:00:00Z\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        MCPStreamClient.StreamEvent ev = h.pollEvent(2000);
        assertEquals(MCPStreamClient.StreamEventType.OPEN, ev.type);
        assertEquals(router.streamId, ev.streamId);
        assertEquals("2026-07-11T10:00:00Z", ev.timestamp);
        MCPStreamClient.StreamEvent close = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.CLOSE, close.type);
        h.cancel();
    }

    @Test
    void testStreamMessageMessageFrame() throws Exception {
        router.streamId = "stream-msg-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: message\ndata: {\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"text\":\"Hi back\"}],\"context_id\":\"ctx-1\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        h.pollEvent(1000); // open
        MCPStreamClient.StreamEvent ev = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.MESSAGE, ev.type);
        assertEquals("assistant", ev.data.get("role"));
        assertEquals("ctx-1", ev.data.get("context_id"));
        h.cancel();
    }

    @Test
    void testStreamMessageArtifactFrame() throws Exception {
        router.streamId = "stream-art-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: artifact\ndata: {\"artifact_id\":\"a-1\",\"name\":\"report.md\",\"parts\":[{\"text\":\"# hi\"}]}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("gen"), null);
        h.pollEvent(1000);
        MCPStreamClient.StreamEvent ev = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.ARTIFACT, ev.type);
        assertEquals("a-1", ev.data.get("artifact_id"));
        assertEquals("report.md", ev.data.get("name"));
        h.cancel();
    }

    @Test
    void testStreamMessageTaskCompleteFrame() throws Exception {
        router.streamId = "stream-tc-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: task_complete\ndata: {\"task_id\":\"t-1\",\"status\":\"completed\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("gen"), null);
        h.pollEvent(1000);
        MCPStreamClient.StreamEvent ev = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.TASK_COMPLETE, ev.type);
        assertEquals("t-1", ev.data.get("task_id"));
        assertEquals("completed", ev.data.get("status"));
        h.cancel();
    }

    @Test
    void testStreamMessageErrorFrame() throws Exception {
        router.streamId = "stream-err-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: error\ndata: {\"code\":-32003,\"message\":\"task not found\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("gen"), null);
        h.pollEvent(1000);
        McpException e = assertThrows(McpException.class, () -> h.pollEvent(2000));
        assertEquals(McpException.ERR_CODE_MCP_TASK_NOT_FOUND, e.code);
        h.cancel();
    }

    @Test
    void testStreamMessage401() throws Exception {
        // Phase 1 成功;Phase 2 SSE 401
        router.streamId = "stream-401";
        mws.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        McpException e = assertThrows(McpException.class, () -> h.pollEvent(2000));
        assertTrue(e.getMessage().contains("401") || e.getMessage().contains("unauthorized"),
                "expected 401 in err msg, got: " + e.getMessage());
        h.cancel();
    }

    @Test
    void testStreamMessageNilMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> client.streamMessage("fox-agent", null, null));
    }

    @Test
    void testStreamMessageEmptyParts() {
        Dto.Message msg = new Dto.Message("user", new ArrayList<>());
        assertThrows(IllegalArgumentException.class,
                () -> client.streamMessage("fox-agent", msg, null));
    }

    @Test
    void testStreamMessageCancel() throws Exception {
        router.streamId = "stream-cancel-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        h.pollEvent(1000); // open
        h.cancel();
        h.cancel(); // idempotent
        MCPStreamClient.StreamEvent ev = h.pollEvent(500);
        assertEquals(MCPStreamClient.StreamEventType.CLOSE, ev.type);
        assertEquals("client_cancelled", ev.data.get("reason"));
    }

    @Test
    void testSubscribeToTaskHappyPath() throws Exception {
        router.streamId = "stream-sub-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: task_status\ndata: {\"task_id\":\"t-1\",\"status\":\"working\"}\n\n",
                "event: task_complete\ndata: {\"task_id\":\"t-1\",\"status\":\"completed\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.subscribeToTask(
                "fox-agent", "t-1", null);
        h.pollEvent(1000); // open
        MCPStreamClient.StreamEvent s1 = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.TASK_STATUS, s1.type);
        MCPStreamClient.StreamEvent s2 = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.TASK_COMPLETE, s2.type);
        h.cancel();
    }

    @Test
    void testSubscribeToTaskTaskIdEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> client.subscribeToTask("fox-agent", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.subscribeToTask("fox-agent", null, null));
    }

    @Test
    void testSubscribeToTaskErrorFrame() throws Exception {
        router.streamId = "stream-sub-err-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: error\ndata: {\"code\":-32603,\"message\":\"upstream dead\"}\n\n");
        MCPStreamClient.StreamHandle h = client.subscribeToTask(
                "fox-agent", "t-1", null);
        h.pollEvent(1000);
        McpException e = assertThrows(McpException.class, () -> h.pollEvent(2000));
        assertEquals(McpException.ERR_CODE_INTERNAL, e.code);
        h.cancel();
    }

    @Test
    void testStreamOptionsIncludeHistory() throws Exception {
        router.streamId = "stream-opt-h";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamOptions opt = new MCPStreamClient.StreamOptions(true, null);
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), opt);
        MockCall call = router.calls.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> so = (Map<String, Object>) call.arguments.get("stream_options");
        assertNotNull(so);
        assertEquals(true, so.get("include_history"));
        assertFalse(so.containsKey("include_artifacts"));
        h.cancel();
    }

    @Test
    void testStreamOptionsIncludeArtifacts() throws Exception {
        router.streamId = "stream-opt-a";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamOptions opt = new MCPStreamClient.StreamOptions(null, true);
        MCPStreamClient.StreamHandle h = client.subscribeToTask(
                "fox-agent", "t-1", opt);
        MockCall call = router.calls.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> so = (Map<String, Object>) call.arguments.get("stream_options");
        assertNotNull(so);
        assertEquals(true, so.get("include_artifacts"));
        assertFalse(so.containsKey("include_history"));
        h.cancel();
    }

    @Test
    void testStreamOptionsDefault() throws Exception {
        router.streamId = "stream-opt-d";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        MockCall call = router.calls.get(0);
        assertFalse(call.arguments.containsKey("stream_options"));
        h.cancel();
    }

    @Test
    void testStreamHandleCancelIdempotent() throws Exception {
        router.streamId = "stream-cancel-idem";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        h.pollEvent(1000); // open
        h.cancel();
        h.cancel(); // 第 2 次
        h.cancel(); // 第 3 次
        MCPStreamClient.StreamEvent ev = h.pollEvent(500);
        assertEquals(MCPStreamClient.StreamEventType.CLOSE, ev.type);
    }

    @Test
    void testStreamMessageBearerTokenRefresh() throws Exception {
        router.streamId = "stream-token";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient newClient = client.withBearerToken("new-jwt");
        MCPStreamClient.StreamHandle h = newClient.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        RecordedRequest rec = mws.takeRequest();
        assertEquals("Bearer new-jwt", rec.getHeader("Authorization"));
        h.cancel();
    }

    @Test
    void testStreamMessageStreamIdMismatch() throws Exception {
        router.streamId = "stream-mismatch-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"different-uuid\",\"timestamp\":\"2026-07-11T10:00:00Z\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        McpException e = assertThrows(McpException.class, () -> h.pollEvent(2000));
        assertEquals(McpException.ERR_CODE_INTERNAL, e.code);
        assertTrue(e.getMessage().contains("mismatch"));
        h.cancel();
    }

    @Test
    void testStreamMessageInvalidJson() throws Exception {
        router.streamId = "stream-badjson";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: message\ndata: this-is-not-json\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        h.pollEvent(1000); // open
        MCPStreamClient.StreamEvent ev = h.pollEvent(1000);
        assertEquals(MCPStreamClient.StreamEventType.MESSAGE, ev.type);
        assertNotNull(ev.data.get("error"));
        h.cancel();
    }

    @Test
    void testStreamMessageMultipleEvents() throws Exception {
        router.streamId = "stream-multi";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\"}\n\n",
                "event: message\ndata: {\"role\":\"assistant\",\"parts\":[{\"text\":\"a\"}]}\n\n",
                "event: message\ndata: {\"role\":\"assistant\",\"parts\":[{\"text\":\"b\"}]}\n\n",
                "event: artifact\ndata: {\"artifact_id\":\"a-1\"}\n\n",
                "event: task_status\ndata: {\"task_id\":\"t-1\"}\n\n",
                "event: task_complete\ndata: {\"task_id\":\"t-1\",\"status\":\"completed\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        h.pollEvent(1000); // open
        MCPStreamClient.StreamEventType[] expected = {
                MCPStreamClient.StreamEventType.MESSAGE,
                MCPStreamClient.StreamEventType.MESSAGE,
                MCPStreamClient.StreamEventType.ARTIFACT,
                MCPStreamClient.StreamEventType.TASK_STATUS,
                MCPStreamClient.StreamEventType.TASK_COMPLETE,
                MCPStreamClient.StreamEventType.CLOSE,
        };
        for (MCPStreamClient.StreamEventType t : expected) {
            MCPStreamClient.StreamEvent ev = h.pollEvent(2000);
            assertEquals(t, ev.type, "expected " + t + " got " + ev.type);
        }
        h.cancel();
    }

    @Test
    void testStreamMessageConcurrentStreams() throws Exception {
        router.streamId = "stream-conc-1";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\",\"timestamp\":\"2026-07-11T10:00:00Z\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MockWebServer mws2 = new MockWebServer();
        mws2.start();
        try {
            MockStreamRouter router2 = new MockStreamRouter();
            router2.streamId = "stream-conc-2";
            mws2.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("event: open\ndata: {\"stream_id\":\"" + router2.streamId
                            + "\",\"timestamp\":\"2026-07-11T10:00:01Z\"}\n\n"
                            + "event: close\ndata: {\"reason\":\"server_done\"}\n\n"));
            MCPStreamClient client2 = new MCPStreamClient(
                    mws2.url("").toString().replaceAll("/$", ""), "jwt2",
                    new HttpTransport(new RouterSender(router2, mws2)),
                    new OkHttpClient.Builder().build());

            MCPStreamClient.StreamHandle h1 = client.streamMessage(
                    "fox-1", Dto.Message.user("Hi 1"), null);
            MCPStreamClient.StreamHandle h2 = client2.streamMessage(
                    "fox-2", Dto.Message.user("Hi 2"), null);

            MCPStreamClient.StreamEvent e1 = h1.pollEvent(2000);
            MCPStreamClient.StreamEvent e2 = h2.pollEvent(2000);
            assertEquals(MCPStreamClient.StreamEventType.OPEN, e1.type);
            assertEquals(MCPStreamClient.StreamEventType.OPEN, e2.type);
            assertNotEquals(e1.streamId, e2.streamId);
            h1.cancel();
            h2.cancel();
        } finally {
            mws2.shutdown();
        }
    }

    @Test
    void testStreamHandleTimestampParsing() throws Exception {
        router.streamId = "stream-ts";
        enqueueFrames(
                "event: open\ndata: {\"stream_id\":\"" + router.streamId + "\",\"timestamp\":\"2026-07-11T10:00:00Z\"}\n\n",
                "event: close\ndata: {\"reason\":\"server_done\"}\n\n");
        MCPStreamClient.StreamHandle h = client.streamMessage(
                "fox-agent", Dto.Message.user("Hi"), null);
        MCPStreamClient.StreamEvent ev = h.pollEvent(2000);
        assertEquals("2026-07-11T10:00:00Z", ev.timestamp);
        java.time.Instant.parse(ev.timestamp);
        h.cancel();
    }

    // ────────────────────────────────────────────────────────
    // Mock helpers
    // ────────────────────────────────────────────────────────

    private void enqueueFrames(String... frames) {
        StringBuilder body = new StringBuilder();
        for (String f : frames) body.append(f);
        MockResponse resp = new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body.toString());
        mws.enqueue(resp);
    }

    static class MockCall {
        String tool;
        Map<String, Object> arguments;
        MockCall(String tool, Map<String, Object> arguments) {
            this.tool = tool;
            this.arguments = arguments;
        }
    }

    static class MockStreamRouter {
        List<MockCall> calls = new ArrayList<>();
        String streamId = "mock-stream-id";
        Map<String, Object> handle(Request req, Object body, MockWebServer mws) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) body;
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) envelope.get("params");
            String toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            calls.add(new MockCall(toolName, arguments));
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("jsonrpc", "2.0");
            result.put("id", envelope.get("id"));
            String endpoint = mws.url("").toString().replaceAll("/$", "") + "/mcp/sse?stream_id=" + streamId;
            Map<String, Object> body0 = new java.util.HashMap<>();
            body0.put("stream_id", streamId);
            body0.put("endpoint", endpoint);
            result.put("result", body0);
            return result;
        }
    }

    /** HttpTransport.Sender — 测试用 mock,不再 extends final HttpTransport。 */
    static class RouterSender implements HttpTransport.Sender {
        private final MockStreamRouter router;
        private final MockWebServer mws;
        RouterSender(MockStreamRouter router, MockWebServer mws) {
            this.router = router;
            this.mws = mws;
        }
        @Override
        public Response doRequest(Request req) throws IOException {
            Object body;
            if (req.body() == null) {
                body = java.util.Map.of();
            } else {
                okio.Buffer buf = new okio.Buffer();
                req.body().writeTo(buf);
                body = parseBody(buf.readUtf8());
            }
            Map<String, Object> result = router.handle(req, body, mws);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
            return new Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(json, MediaType.parse("application/json")))
                    .build();
        }
    }

    private static Object parseBody(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("parse body failed: " + e.getMessage(), e);
        }
    }
}
