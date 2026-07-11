package io.wau.sdk.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.transport.HttpTransport;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP SSE streaming client (per D89.A.9 / D87.3 / W5+).
 *
 * <p>{@code streamMessage} / {@code subscribeToTask} 两个 W5+ SSE 流式 tool 的同步客户端。
 * 镜像 4 SDK (go/python/ts/rust) 即将统一补齐的 W5+ streaming 实现。
 *
 * <h2>2 phase 协议</h2>
 * <pre>
 *   1. POST /mcp JSON-RPC tools/call name=stream_message|subscribe_to_task
 *      → resp.result = {"stream_id":"&lt;uuid&gt;","endpoint":"/mcp/sse?stream_id=&lt;uuid&gt;"}
 *
 *   2. GET /mcp/sse?stream_id=&lt;uuid&gt;  (Authorization: Bearer ...)
 *      server 立刻发 {@code event: open}, 之后每个 {@code protocol.Event} 转 SSE 一帧:
 *      {@code event: <type>\ndata: <json>\n\n}
 *      流结束发 {@code event: close\ndata: {"reason":"..."}\n\n}.
 * </pre>
 *
 * <h2>Wire format (per D13 byte-equal, 跟 kernel server.go handleSSE 对齐)</h2>
 * <pre>
 *   - event: open                  → 立即 flush, stream_id 校验
 *   - event: message               → message_delta payload (Message JSON)
 *   - event: artifact              → artifact payload (Artifact JSON)
 *   - event: task_status           → task_update payload (Task JSON, state 字段)
 *   - event: task_complete         → task_update payload, state=completed (隐式收尾)
 *   - event: close                 → 流终止;data 含 "reason"
 *   - event: error                 → 协议层错误;data 含 code+message
 * </pre>
 *
 * <h2>Poll pattern (Java 17 同步友好)</h2>
 * <p>{@link StreamHandle#pollEvent(long)} 默认 {@code BlockingQueue<StreamEvent>};
 * 调用方在循环里 {@code pollEvent(1000)} 读 (msg|artifact|task_status|task_complete)
 * 直到 {@code pollEvent} 返回 {@code type == CLOSE} 或抛 {@link McpException}。
 *
 * <h2>D60 additive + D78/D79/D80 + D87.3</h2>
 * <p>0 改 {@link MCPClient} 8 sync tool;独立 public class 共用 {@link McpAuth} / {@link McpException} / {@link Tools}。
 *
 * @since 0.2.0 (v1.3.3 MCP SSE streaming 实装, 2026-07-11)
 */
public final class MCPStreamClient {

    /** MCP SSE endpoint 默认 path (跟 kernel server.go mux.HandleFunc 对齐)。 */
    public static final String DEFAULT_ENDPOINT = "/mcp";

    /** MCP SSE GET endpoint path (跟 kernel server.go /mcp/sse 一致)。 */
    public static final String SSE_ENDPOINT = "/mcp/sse";

    /** User-Agent (per HTTP RFC 7231)。 */
    public static final String DEFAULT_USER_AGENT = "wau-java-sdk/mcpstreamclient/v1.3.3";

    private final String baseUrl;
    private final String endpoint;
    private final String userAgent;
    private final HttpTransport transport;
    private final OkHttpClient httpClient;
    private volatile String bearerToken;
    private final ObjectMapper mapper;
    private final AtomicLong nextId = new AtomicLong(0);

    /** 默认 constructor,生产用。内部建一个 {@link HttpTransport}(共享 OkHttp client) + 默认 {@link OkHttpClient}。 */
    public MCPStreamClient(String baseUrl, String bearerToken) {
        this(baseUrl, bearerToken, new HttpTransport(), defaultSseClient());
    }

    /** 测试用 constructor,允许 caller 注入 mock HttpTransport + OkHttp client。 */
    public MCPStreamClient(String baseUrl, String bearerToken, HttpTransport transport, OkHttpClient httpClient) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("mcpstreamclient: baseUrl is required");
        }
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.endpoint = DEFAULT_ENDPOINT;
        this.bearerToken = bearerToken;
        this.userAgent = DEFAULT_USER_AGENT;
        this.transport = Objects.requireNonNull(transport, "transport");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = new ObjectMapper();
    }

    /** 简化测试 constructor:仅 OkHttpClient (内部建默认 HttpTransport)。 */
    public MCPStreamClient(String baseUrl, String bearerToken, OkHttpClient httpClient) {
        this(baseUrl, bearerToken, new HttpTransport(), httpClient);
    }

    /** 拿到一个新 client,仅 bearer token 不同。 */
    public MCPStreamClient withBearerToken(String token) {
        MCPStreamClient c = new MCPStreamClient(baseUrl, token, transport, httpClient);
        return c;
    }

    static OkHttpClient defaultSseClient() {
        return new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS) // SSE: 不超时,直到 server 关闭
                .build();
    }

    /** 暴露当前 OkHttp client (供内部 + 测试 inspect)。 */
    OkHttpClient httpClient() { return httpClient; }

    /** 暴露当前 HttpTransport (供内部 + 测试 inspect)。 */
    HttpTransport transport() { return transport; }

    // ── 2 streaming wrappers ────────────────────────────

    /** tool: stream_message. 镜像 5 SDK byte-equal JSON。 */
    public StreamHandle streamMessage(Object target, Dto.Message message, StreamOptions opts)
            throws McpException {
        if (message == null) {
            throw new IllegalArgumentException("mcpstreamclient: message is required");
        }
        if (message.parts == null || message.parts.isEmpty()) {
            throw new IllegalArgumentException("mcpstreamclient: message.parts must have at least 1 item");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("message", message);
        if (opts != null) {
            args.put("stream_options", opts.toMap());
        }
        return openStream(Tools.TOOL_STREAM_MESSAGE, args);
    }

    /** tool: subscribe_to_task. */
    public StreamHandle subscribeToTask(Object target, String taskId, StreamOptions opts)
            throws McpException {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("mcpstreamclient: task_id is required");
        }
        Map<String, Object> args = new HashMap<>();
        args.put("target", normalizeTarget(target));
        args.put("task_id", taskId);
        if (opts != null) {
            args.put("stream_options", opts.toMap());
        }
        return openStream(Tools.TOOL_SUBSCRIBE_TO_TASK, args);
    }

    // ── SSE 2-phase ─────────────────────────────────────

    private StreamHandle openStream(String toolName, Map<String, Object> arguments) throws McpException {
        // Phase 1: POST /mcp JSON-RPC tools/call → 拿 stream_id + endpoint
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
                    "MCP HTTP error opening stream: status=" + e.getStatusCode() + " " + e.getMessage(), e);
        }

        OpenStreamResult open = unwrapOpenResponse(response, toolName);

        // Phase 2: GET /mcp/sse?stream_id=<uuid> 用 OkHttp EventSource 收 frames
        StreamHandle handle = new StreamHandle(open.streamId, bearerToken);
        handle.start(open.endpoint, httpClient);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private OpenStreamResult unwrapOpenResponse(Map<String, Object> envelope, String toolName)
            throws McpException {
        if (envelope == null) {
            throw new McpException(McpException.ERR_CODE_INVALID_REQUEST, "null response envelope");
        }
        if (envelope.containsKey("error")) {
            Map<String, Object> errMap = (Map<String, Object>) envelope.get("error");
            int code = ((Number) errMap.getOrDefault("code", McpException.ERR_CODE_INTERNAL)).intValue();
            String message = String.valueOf(errMap.getOrDefault("message", ""));
            throw new McpException(code,
                    "MCP stream open (" + toolName + ") failed: " + message);
        }
        Object result = envelope.get("result");
        if (result == null) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "MCP stream open (" + toolName + "): missing 'result'");
        }
        if (!(result instanceof Map)) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "MCP stream open (" + toolName + "): result not a JSON object");
        }
        Map<String, Object> resMap = (Map<String, Object>) result;
        Object sid = resMap.get("stream_id");
        Object endpoint = resMap.get("endpoint");
        if (!(sid instanceof String) || ((String) sid).isEmpty()) {
            throw new McpException(McpException.ERR_CODE_INTERNAL,
                    "MCP stream open (" + toolName + "): missing 'stream_id' in result");
        }
        String streamId = (String) sid;
        String fullUrl;
        if (endpoint instanceof String && !((String) endpoint).isEmpty()) {
            String ep = (String) endpoint;
            // kernel 注释可能含 "GET " or "POST " prefix; strip if any
            int sp = ep.indexOf(' ');
            String path = sp > 0 ? ep.substring(sp + 1) : ep;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                fullUrl = path;
            } else {
                fullUrl = baseUrl + path;
            }
        } else {
            fullUrl = baseUrl + SSE_ENDPOINT + "?stream_id=" + streamId;
        }
        return new OpenStreamResult(streamId, fullUrl);
    }

    // ── Helpers ──────────────────────────────────────────

    private static Object normalizeTarget(Object target) {
        if (target == null) {
            throw new IllegalArgumentException("mcpstreamclient: target is required");
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
                "mcpstreamclient: target must be String or Map, got " + target.getClass().getName());
    }

    // ────────────────────────────────────────────────────────
    // StreamEventType — 7 type per D89 SSE wire spec
    // ────────────────────────────────────────────────────────

    /** 7 类型 SSE event (跟 kernel server.go handleSSE event 字段 1:1 对齐 + D89 SOP normalize)。 */
    public enum StreamEventType {
        OPEN,
        MESSAGE,
        ARTIFACT,
        TASK_STATUS,
        TASK_COMPLETE,
        CLOSE,
        ERROR
    }

    /** Single SSE frame 解析后的不可变 record-like POJO。 */
    public static final class StreamEvent {
        public final StreamEventType type;
        public final String streamId;
        public final String timestamp;
        public final Map<String, Object> data;
        public final String rawData;

        public StreamEvent(StreamEventType type, String streamId, String timestamp,
                           Map<String, Object> data, String rawData) {
            this.type = type;
            this.streamId = streamId;
            this.timestamp = timestamp;
            this.data = data;
            this.rawData = rawData;
        }

        @Override
        public String toString() {
            return "StreamEvent{" + type + " streamId=" + streamId +
                    (timestamp != null ? " ts=" + timestamp : "") + '}';
        }
    }

    // ────────────────────────────────────────────────────────
    // StreamOptions — D89 流式订阅选项
    // ────────────────────────────────────────────────────────

    /** {@code stream_options} — 字段都可 null (=server default)。 */
    public static final class StreamOptions {
        public final Boolean includeHistory;
        public final Boolean includeArtifacts;

        public StreamOptions(Boolean includeHistory, Boolean includeArtifacts) {
            this.includeHistory = includeHistory;
            this.includeArtifacts = includeArtifacts;
        }

        public static StreamOptions defaults() {
            return new StreamOptions(null, null);
        }

        public static StreamOptions of(boolean history, boolean artifacts) {
            return new StreamOptions(history, artifacts);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            if (includeHistory != null) m.put("include_history", includeHistory);
            if (includeArtifacts != null) m.put("include_artifacts", includeArtifacts);
            return m;
        }
    }

    // ────────────────────────────────────────────────────────
    // StreamHandle — polling 入口,BlockingQueue<StreamEvent> 推送
    // ────────────────────────────────────────────────────────

    /**
     * Handle 给 caller poll; 内部 OkHttp {@link EventSource} 把 frames 推到 BlockingQueue.
     * <p>由 {@link MCPStreamClient#streamMessage} / {@link MCPStreamClient#subscribeToTask} 返;
     * caller 拿到 handle 后一直在循环里 {@code pollEvent(timeoutMs)} 读直到 type ∈ {CLOSE, ERROR}.
     */
    public static final class StreamHandle implements AutoCloseable {
        private final String streamId;
        private final String bearerToken;
        private final BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile EventSource eventSource; // package-private 给测试 inspect

        StreamHandle(String streamId, String bearerToken) {
            this.streamId = streamId;
            this.bearerToken = bearerToken;
        }

        public String streamId() { return streamId; }

        /** Test helper: 内部 queue 大小 (caller 用于断言 drain)。 */
        int queueSize() { return queue.size(); }

        /** 暴露当前 EventSource (供测试 inspect SSE lifecycle)。 */
        EventSource eventSource() { return eventSource; }

        /** Poll 一个 event; timeout=0L 永远等,>0 等 timeoutMs 毫秒。返回 null 表示 timeout,抛 {@link McpException} 表示 error frame / 远端断。 */
        public StreamEvent pollEvent(long timeoutMs) throws McpException {
            if (closed.get() && queue.isEmpty()) {
                return new StreamEvent(StreamEventType.CLOSE, streamId, null,
                        Map.of("reason", "client_closed"), null);
            }
            try {
                StreamEvent ev = timeoutMs <= 0
                        ? queue.take()
                        : queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (ev == null) return null;
                if (ev.type == StreamEventType.ERROR) {
                    int code = readInt(ev.data, "code", McpException.ERR_CODE_INTERNAL);
                    String msg = String.valueOf(ev.data.getOrDefault("message", "stream error"));
                    throw new McpException(code, msg);
                }
                return ev;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpException(McpException.ERR_CODE_INTERNAL,
                        "interrupted while waiting for stream event", e);
            }
        }

        /** 立刻关闭 SSE 连接; idempotent。 */
        public void cancel() {
            if (!closed.compareAndSet(false, true)) {
                return; // idempotent
            }
            EventSource es = eventSource;
            if (es != null) {
                es.cancel();
            }
            // 给阻塞的 pollEvent 一个 wake-up 信号
            queue.offer(new StreamEvent(StreamEventType.CLOSE, streamId, null,
                    Map.of("reason", "client_cancelled"), null));
        }

        @Override
        public void close() {
            cancel();
        }

        // ── internal: 启动 SSE GET ─────────────────────

        void start(String url, OkHttpClient client) {
            Headers.Builder hb = new Headers.Builder();
            hb.add("Accept", "text/event-stream");
            hb.add("Cache-Control", "no-cache");
            if (bearerToken != null && !bearerToken.isEmpty()) {
                hb.add("Authorization", "Bearer " + bearerToken);
            }
            Request req = new Request.Builder()
                    .url(url)
                    .headers(hb.build())
                    .get()
                    .build();

            EventSourceListener listener = new EventSourceListener() {
                @Override
                public void onOpen(EventSource es, Response response) {
                    // server will push event: open next; nothing extra to do
                }

                @Override
                public void onEvent(EventSource es, String id, String type, String data) {
                    StreamEventType mappedType = mapEventType(type);
                    Map<String, Object> parsed;
                    try {
                        parsed = parseData(data);
                    } catch (Exception parseEx) {
                        parsed = Map.of("error", "invalid json: " + parseEx.getMessage(), "raw", data == null ? "" : data);
                    }
                    String ts = null;
                    String sid = streamId;
                    Object tsObj = parsed.get("timestamp");
                    if (tsObj != null) ts = String.valueOf(tsObj);
                    Object sidObj = parsed.get("stream_id");
                    if (sidObj instanceof String) sid = (String) sidObj;
                    if (!sid.equals(streamId) && mappedType == StreamEventType.OPEN) {
                        // open frame stream_id mismatch → push ERROR (per D89 SOP test 18)
                        parsed = Map.of("code", McpException.ERR_CODE_INTERNAL,
                                "message", "stream_id mismatch: expected=" + streamId + " got=" + sid);
                        queue.offer(new StreamEvent(StreamEventType.ERROR, streamId, null, parsed, data));
                        return;
                    }
                    queue.offer(new StreamEvent(mappedType, sid, ts, parsed, data));
                }

                @Override
                public void onClosed(EventSource es) {
                    closed.set(true);
                    queue.offer(new StreamEvent(StreamEventType.CLOSE, streamId, null,
                            Map.of("reason", "server_closed"), null));
                }

                @Override
                public void onFailure(EventSource es, Throwable t, Response response) {
                    closed.set(true);
                    int code = McpException.ERR_CODE_INTERNAL;
                    String msg;
                    if (response != null) {
                        int rc = response.code();
                        if (rc == 401) code = McpException.ERR_CODE_MCP_AGENT_UNREACHABLE;
                        msg = "SSE HTTP " + rc;
                        try (ResponseBody br = response.body()) {
                            if (br != null) {
                                String b = br.string();
                                if (b != null && !b.isEmpty()) msg = msg + " " + b;
                            }
                        } catch (IOException ignored) {}
                    } else if (t != null) {
                        msg = "SSE connection error: " + t.getMessage();
                    } else {
                        msg = "SSE failure (unknown)";
                    }
                    queue.offer(new StreamEvent(StreamEventType.ERROR, streamId, null,
                            Map.of("code", code, "message", msg), null));
                }
            };

            eventSource = EventSources.createFactory(client).newEventSource(req, listener);
        }

        // ── helpers ─────────────────────────────────

        static StreamEventType mapEventType(String type) {
            if (type == null) return StreamEventType.ERROR;
            switch (type) {
                case "open":
                    return StreamEventType.OPEN;
                case "message":
                case "message_delta":
                    return StreamEventType.MESSAGE;
                case "artifact":
                    return StreamEventType.ARTIFACT;
                case "task_status":
                case "task_update":
                    return StreamEventType.TASK_STATUS;
                case "task_complete":
                case "task_completed":
                    return StreamEventType.TASK_COMPLETE;
                case "close":
                    return StreamEventType.CLOSE;
                case "error":
                default:
                    return StreamEventType.ERROR;
            }
        }

        private static Map<String, Object> parseData(String data) throws IOException {
            ObjectMapper m = new ObjectMapper();
            if (data == null || data.isEmpty()) return Map.of();
            return m.readValue(data, new TypeReference<Map<String, Object>>() {});
        }

        private static int readInt(Map<String, Object> data, String key, int fallback) {
            Object v = data.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return fallback; }
        }
    }

    // ────────────────────────────────────────────────────────
    // Internal helpers
    // ────────────────────────────────────────────────────────

    private static final class OpenStreamResult {
        final String streamId;
        final String endpoint;

        OpenStreamResult(String streamId, String endpoint) {
            this.streamId = streamId;
            this.endpoint = endpoint;
        }
    }
}
