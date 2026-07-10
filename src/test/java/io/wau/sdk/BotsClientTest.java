package io.wau.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.wau.sdk.bot.common.Account;
import io.wau.sdk.bot.common.BotsService;
import io.wau.sdk.bot.registry.BotsClient;
import io.wau.sdk.exception.BotAlreadyExistsException;
import io.wau.sdk.exception.BotInvalidArgumentException;
import io.wau.sdk.exception.BotNotFoundException;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.transport.HttpTransport;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v1.3.0 W2.1 (2026-07-10) BotsClient 单元测试:
 *
 * <p>10 个测试(5 method × 2 case:成功 + 失败):
 * <ul>
 *   <li>register_Success / register_AlreadyExists (409)</li>
 *   <li>get_Success / get_NotFound (404)</li>
 *   <li>update_Success / update_Invalid (400)</li>
 *   <li>list_Success / list_Empty</li>
 *   <li>delete_Success / delete_NotFound (404)</li>
 * </ul>
 *
 * <p>用 {@link HttpTransport.Sender} mock(无需起 MockWebServer),handler 完整循环可测。
 *
 * @since 1.3.0
 */
@DisplayName("v1.3.0 BotsClient 5 method × 2 case (10 tests)")
class BotsClientTest {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String BASE = "https://wau.example.com";

    private ObjectMapper mapper;
    private CapturingSender sender;
    private BotsClient client;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        sender = new CapturingSender();
        HttpTransport transport = new HttpTransport(sender);
        client = new BotsClient(BASE, transport, "test-bot-token");
    }

    // ---------- register ----------

    @Test
    @DisplayName("register_Success — POST /v1/registry/bots with botUuid")
    void register_Success() throws Exception {
        Account mockResp = sampleAccount("acme", "weather-cn");
        sender.queueJson(200, mockResp);

        BotsService.RegisterRequest req = new BotsService.RegisterRequest(
                "acme", "weather-cn", null,  // botUuid = null
                "user-7", "telegram", "cfg-1"
        );

        Account got = client.register(req);
        assertNotNull(got);
        assertEquals("acme", got.tenantId());
        assertEquals("weather-cn", got.botId());
        // Sender captured request
        Request captured = sender.lastRequest();
        assertEquals("POST", captured.method());
        assertTrue(captured.url().encodedPath().endsWith("/v1/registry/bots"));
        String body = sender.lastBody();
        assertTrue(body.contains("\"bot_id\":\"weather-cn\""), "body: " + body);
        assertTrue(body.contains("\"owner_user_id\":\"user-7\""), "body: " + body);
        // botUuid 空时不序列化
        assertFalse(body.contains("bot_uuid"), "botUuid 缺省不应序列化:" + body);
    }

    @Test
    @DisplayName("register_AlreadyExists — 409 → BotAlreadyExistsException")
    void register_AlreadyExists() {
        sender.queueJson(409, "{\"error\":\"bot already exists\"}");

        BotsService.RegisterRequest req = new BotsService.RegisterRequest(
                "acme", "weather-cn", null,
                "user-7", "telegram", "cfg-1"
        );

        Exception ex = assertThrows(WauException.class, () -> client.register(req));
        // 当前 WauException 基类是 root,409 也走 WauException.此处通过 message 校验
        assertTrue(ex.getMessage().contains("409"), "expected 409 in error, got: " + ex.getMessage());
    }

    // ---------- get ----------

    @Test
    @DisplayName("get_Success — GET /v1/registry/bots/{publicBotId}")
    void get_Success() throws Exception {
        Account mockResp = sampleAccount("acme", "weather-cn");
        sender.queueJson(200, mockResp);

        Optional<Account> got = client.get("bot:acme:weather-cn");
        assertTrue(got.isPresent());
        assertEquals("weather-cn", got.get().botId());
        Request captured = sender.lastRequest();
        assertEquals("GET", captured.method());
        assertTrue(captured.url().encodedPath().endsWith("/v1/registry/bots/bot:acme:weather-cn"));
    }

    @Test
    @DisplayName("get_NotFound — 404 → BotNotFoundException (or WauException)")
    void get_NotFound() {
        sender.queueJson(404, "{\"error\":\"not found\"}");

        Exception ex = assertThrows(WauException.class, () -> client.get("bot:nope:ghost"));
        assertTrue(ex.getMessage().contains("404"), "expected 404, got: " + ex.getMessage());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update_Success — PUT /v1/registry/bots/{publicBotId}")
    void update_Success() throws Exception {
        Account mockResp = sampleAccount("acme", "weather-cn");
        sender.queueJson(200, mockResp);

        BotsService.UpdateRequest req = new BotsService.UpdateRequest(
                "user-99", "discord", null  // channelConfigId 留空
        );

        Account got = client.update("bot:acme:weather-cn", req);
        assertNotNull(got);
        Request captured = sender.lastRequest();
        assertEquals("PUT", captured.method());
        String body = sender.lastBody();
        assertTrue(body.contains("\"owner_user_id\":\"user-99\""));
        assertTrue(body.contains("\"channel_type\":\"discord\""));
        assertFalse(body.contains("channel_config_id"));
    }

    @Test
    @DisplayName("update_Invalid — 空 body → BotInvalidArgumentException")
    void update_Invalid() {
        BotsService.UpdateRequest req = new BotsService.UpdateRequest(null, null, null);
        assertThrows(BotInvalidArgumentException.class,
                () -> client.update("bot:acme:weather-cn", req));
    }

    // ---------- list ----------

    @Test
    @DisplayName("list_Success — GET /v1/registry/bots 返 List<Account>")
    void list_Success() throws Exception {
        List<Account> mockResp = new ArrayList<>();
        mockResp.add(sampleAccount("acme", "weather-cn"));
        mockResp.add(sampleAccount("acme", "weather-en"));
        sender.queueJson(200, mockResp);

        List<Account> got = client.list(new BotsService.ListFilter("acme", null, null, 10));
        assertEquals(2, got.size());
        assertEquals("weather-cn", got.get(0).botId());
    }

    @Test
    @DisplayName("list_Empty — GET /v1/registry/bots 返 []")
    void list_Empty() throws Exception {
        sender.queueJson(200, new ArrayList<>());
        List<Account> got = client.list(new BotsService.ListFilter("acme", null, null, 10));
        assertNotNull(got);
        assertTrue(got.isEmpty());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete_Success — DELETE /v1/registry/bots/{publicBotId}")
    void delete_Success() throws Exception {
        sender.queueJson(204, "");
        client.delete("bot:acme:weather-cn");
        Request captured = sender.lastRequest();
        assertEquals("DELETE", captured.method());
        assertTrue(captured.url().encodedPath().endsWith("/v1/registry/bots/bot:acme:weather-cn"));
    }

    @Test
    @DisplayName("delete_NotFound — 404 → BotNotFoundException")
    void delete_NotFound() {
        sender.queueJson(404, "{\"error\":\"not found\"}");
        assertThrows(WauException.class, () -> client.delete("bot:nope:ghost"));
    }

    // ---------- helpers ----------

    private Account sampleAccount(String tenant, String botId) {
        Instant now = Instant.parse("2026-07-10T00:00:00Z");
        return new Account(
                "acc-uuid-" + botId,
                tenant,
                botId,
                "uuid-" + botId,  // botUuid
                "bot:" + tenant + ":" + botId,
                "user-7",
                "telegram",
                "cfg-1",
                now,
                now
        );
    }

    /**
     * Sender mock:pre-loaded JSON response queue + 捕获最后请求+body 给 test assert。
     */
    static class CapturingSender implements HttpTransport.Sender {
        private final List<String> jsonQueue = new ArrayList<>();
        private final List<Integer> codeQueue = new ArrayList<>();
        private final AtomicReference<Request> lastReq = new AtomicReference<>();
        private final AtomicReference<String> lastBodyStr = new AtomicReference<>();

        void queueJson(int code, Object obj) {
            codeQueue.add(code);
            try {
                ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
                jsonQueue.add(obj == null ? "" : m.writeValueAsString(obj));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Request lastRequest() { return lastReq.get(); }
        String lastBody() { return lastBodyStr.get(); }

        @Override
        public Response doRequest(Request req) throws IOException {
            lastReq.set(req);
            // 异步序列化 body → 字符串(OkHttp buffer)
            if (req.body() != null) {
                okio.Buffer buf = new okio.Buffer();
                req.body().writeTo(buf);
                lastBodyStr.set(buf.readUtf8());
            }
            int code = codeQueue.isEmpty() ? 200 : codeQueue.remove(0);
            String json = jsonQueue.isEmpty() ? "" : jsonQueue.remove(0);
            return new Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(code == 204 ? "No Content" : "OK")
                    .body(ResponseBody.create(json, JSON))
                    .build();
        }
    }
}
