package io.wau.sdk.l5;

import io.wau.sdk.WauConfig;
import io.wau.sdk.exception.AuthException;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.exception.WauNotFoundException;
import io.wau.sdk.exception.WauUnauthorizedException;
import io.wau.sdk.transport.HttpTransport;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L5Service 单测(per A3 prep 2026-07-10,v1.3.3 L5 deadline 提前)。
 *
 * 模式:MockWebServer(per OkHttp 4)拦截 HTTP 请求,验证 5 端点 + 错误码 + DTO 字段对齐。
 *
 * 5 case:
 *   1. install happy path          → 200 + agent_id
 *   2. install duplicate           → 409 + error msg(per D73 ErrL5AlreadyInstalled)
 *   3. search returns hits         → 200 + results[].trust_score
 *   4. login wrong password        → 401 + WauUnauthorizedException
 *   5. uninstall purge=false       → 200 + snapshot_path(per D73 软删除)
 */
class L5ServiceTest {

    private MockWebServer server;
    private HttpTransport http;
    private L5Service l5;
    private WauConfig cfg;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        http = new HttpTransport();
        // 用 WauConfig.Builder(per WauConfig.java line 71 Builder pattern)
        cfg = new WauConfig.Builder()
                .wauEdgeUrl(server.url("/").toString().replaceAll("/$", ""))
                .botId("test-bot")
                .ownerUserId("42")
                .build();
        l5 = new L5Service(cfg, http);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /** Test 1:install happy path — 验证 POST /v1/l5/install + JSON snake_case 字段。 */
    @Test
    void installSendsCorrectRequestAndParses200() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"agent_id\":\"agent-alice-weather-1\",\"version\":\"1.2.3\"," +
                         "\"installed_at\":\"2026-07-10T10:00:00Z\",\"duration_ms\":42.5," +
                         "\"sandbox_docker_id\":\"docker-weather-1\"}"));

        L5Service.InstallRequest req = new L5Service.InstallRequest("alice", "weather-agent", "1.2.3");
        L5Service.InstallResponse resp = l5.install(req);

        assertNotNull(resp, "resp must not be null");
        assertEquals(Boolean.TRUE, resp.getOk());
        assertEquals("agent-alice-weather-1", resp.getAgentId());
        assertEquals("1.2.3", resp.getVersion());
        assertEquals("docker-weather-1", resp.getSandboxDockerId());
        assertNull(resp.getError());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded, "no request received");
        assertEquals("POST", recorded.getMethod());
        assertTrue(recorded.getPath().endsWith("/v1/l5/install"),
                "path should hit /v1/l5/install; got " + recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"user_id\":\"alice\""), "body must have user_id=alice: " + body);
        assertTrue(body.contains("\"agent_name\":\"weather-agent\""), "body must have agent_name: " + body);
        assertTrue(body.contains("\"version\":\"1.2.3\""), "body must have version 1.2.3: " + body);
    }

    /** Test 2:install duplicate → 409 + WauException(per HttpTransport 默认分支)。 */
    @Test
    void installDuplicateReturns409WithErrorMessage() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":false,\"error\":\"l5: agent already installed: weather-agent (use purge=true to reinstall)\"}"));

        // 409 走 HttpTransport default 分支(line 196-200)→ WauException
        WauException ex = assertThrows(WauException.class, () ->
                l5.install(new L5Service.InstallRequest("alice", "weather-agent")));
        assertNotNull(ex.getMessage(), "exception message must not be empty");
        assertTrue(ex.getMessage().contains("already installed"),
                "err msg should mention 'already installed': " + ex.getMessage());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", recorded.getMethod());
        assertTrue(recorded.getPath().endsWith("/v1/l5/install"));
    }

    /** Test 3:search 返回 hits — 验证 trust_score 字段(D13 JSON 兼容)。 */
    @Test
    void searchReturnsHitsWithTrustScore() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"results\":[" +
                         "{\"name\":\"weather-agent\",\"version\":\"1.2.3\"," +
                         "\"description\":\"Get current weather\",\"author\":\"wau-team\"," +
                         "\"universe\":\"general\",\"homepage\":\"https://github.com/wau/weather-agent\"," +
                         "\"trust_score\":0.95}," +
                         "{\"name\":\"reminder-agent\",\"version\":\"0.9.1\"," +
                         "\"description\":\"Set reminders\",\"author\":\"wau-team\"," +
                         "\"universe\":\"productivity\",\"homepage\":\"https://github.com/wau/reminder-agent\"," +
                         "\"trust_score\":0.88}" +
                         "],\"total\":2}"));

        L5Service.SearchResponse resp = l5.search(
                new L5Service.SearchRequest("alice", "weather"));
        assertNotNull(resp);
        assertEquals(Boolean.TRUE, resp.getOk());
        assertEquals(2, resp.getResults().size());
        assertEquals("weather-agent", resp.getResults().get(0).getName());
        assertEquals(0.95, resp.getResults().get(0).getTrustScore(), 0.001);
        assertEquals("reminder-agent", resp.getResults().get(1).getName());
        assertEquals(Integer.valueOf(2), resp.getTotal());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"user_id\":\"alice\""), "body must contain user_id: " + body);
        assertTrue(body.contains("\"query\":\"weather\""), "body must contain query=weather: " + body);
    }

    /** Test 4:login wrong password → 401 + AuthException(per HttpTransport line 189-191)。 */
    @Test
    void loginWrongPasswordReturns401() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":false,\"error\":\"l5: invalid credentials\"}"));

        // HttpTransport line 189-191:401 → AuthException
        AuthException ex = assertThrows(AuthException.class, () ->
                l5.login(new L5Service.LoginRequest("alice", "WRONG")));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid credentials"),
                "err msg should indicate invalid credentials: " + ex.getMessage());
    }

    /** Test 5:uninstall purge=false → 200 + snapshot_path(per D73 软删除)。 */
    @Test
    void uninstallPreserveReturnsSnapshotPath() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"uninstalled_at\":\"2026-07-10T10:05:00Z\"," +
                         "\"snapshot_path\":\"/var/lib/wau-agent/snapshots/alice_weather-agent_1783655000.tar.gz\"}"));

        L5Service.UninstallRequest req = new L5Service.UninstallRequest("alice", "weather-agent", false);
        L5Service.UninstallResponse resp = l5.uninstall(req);

        assertNotNull(resp);
        assertEquals(Boolean.TRUE, resp.getOk());
        assertNotNull(resp.getSnapshotPath(),
                "D73 violation: purge=false must populate snapshot_path");
        assertTrue(resp.getSnapshotPath().contains("alice_weather-agent"),
                "snapshot path should encode user+agent: " + resp.getSnapshotPath());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"purge\":false"),
                "body must explicitly set purge=false (per D73 protection): " + body);
    }
}
