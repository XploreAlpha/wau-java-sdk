package io.wau.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.exception.AuthException;
import io.wau.sdk.exception.RateLimitException;
import io.wau.sdk.exception.WauNotFoundException;
import io.wau.sdk.exception.WauServerException;
import io.wau.sdk.model.*;
import io.wau.sdk.transport.HttpTransport;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WauClient 5 方法单元测试(per D63 §5.1, 5 × 2 = 10 测试)。
 *
 * <p>用 {@link HttpTransport.Sender} mock — 验证 HTTP 透传 + body 编码 + 4 错误码语义。
 */
class WauClientTest {

    private HttpTransport.Sender mockSender;
    private WauClient client;
    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef"; // 32-byte

    @BeforeEach
    void setUp() {
        mockSender = mock(HttpTransport.Sender.class);
        HttpTransport transport = new HttpTransport(mockSender);
        WauConfig cfg = new WauConfig.Builder()
                .wauEdgeUrl("http://localhost:18402")
                .botId("bot:default:testjava")
                .ownerUserId("123")
                .build();
        client = new WauClient(cfg, transport);
    }

    private Response ok(String json) {
        return new Response.Builder()
                .code(200).message("OK")
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://x").build())
                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                .build();
    }

    private Response err(int code, String json) {
        return new Response.Builder()
                .code(code).message("ERR")
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://x").build())
                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                .build();
    }

    // === 1. sendMessage ===

    @Test
    void sendMessage_succeeds() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(ok("{\"response\":\"hi\",\"context_id\":\"c1\",\"provider\":\"deepseek\",\"tokens_used\":42,\"elapsed_ms\":120,\"bot_id\":\"bot:default:testjava\"}"));
        IncomingMessage msg = IncomingMessage.builder()
                .platformMsgId("p1").channelId("telegram").userId("alice").text("hello")
                .timestamp(Instant.now()).build();
        OutgoingMessage out = client.sendMessage(msg);
        assertThat(out.getResponse()).isEqualTo("hi");
        assertThat(out.getProvider()).isEqualTo("deepseek");
        assertThat(out.getTokensUsed()).isEqualTo(42);

        // 验证 HTTP 透传 — POST /v1/bots/...
        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(mockSender).doRequest(cap.capture());
        assertThat(cap.getValue().method()).isEqualTo("POST");
        assertThat(cap.getValue().url().toString()).contains("/v1/bots/bot:default:testjava/messages");
        // 验证 X-WAU-Bot header
        assertThat(cap.getValue().header("X-WAU-Bot")).isEqualTo("bot:default:testjava");
        assertThat(cap.getValue().header("X-WAU-User")).isEqualTo("alice");
        assertThat(cap.getValue().header("X-WAU-Owner")).isEqualTo("123");
        // 验证 body snake_case
        String body = new String(cap.getValue().body().writeToByteArray());
        assertThat(body).contains("\"platform_msg_id\":\"p1\"");
        assertThat(body).contains("\"channel_id\":\"telegram\"");
        assertThat(body).contains("\"user_id\":\"alice\"");
    }

    @Test
    void sendMessage_notFound_throws() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(err(404, "{\"error\":\"bot not found\"}"));
        IncomingMessage msg = IncomingMessage.builder()
                .platformMsgId("p1").channelId("x").userId("u").text("hi")
                .timestamp(Instant.now()).build();
        assertThatThrownBy(() -> client.sendMessage(msg))
                .isInstanceOf(WauNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // === 2. registerBot ===

    @Test
    void registerBot_succeeds() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(ok("{\"bot_id\":\"bot:default:new\",\"tenant_id\":\"default\",\"owner_user_id\":123,\"display_name\":\"New\",\"backend\":\"agent-runner\",\"enabled\":true}"));
        Bot b = client.registerBot("bot:default:new", "tok-abc");
        assertThat(b.getBotId()).isEqualTo("bot:default:new");
        assertThat(b.getOwnerUserId()).isEqualTo(123L);
        assertThat(b.isEnabled()).isTrue();
    }

    @Test
    void registerBot_invalidFormat_throwsImmediately() {
        // 在 HTTP 调用前校验(per D78)
        assertThatThrownBy(() -> client.registerBot("bad-format", "tok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bot:default:botid");
    }

    // === 3. getAgent ===

    @Test
    void getAgent_succeeds() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(ok("{\"id\":\"agent-001\",\"name\":\"Test Agent\",\"description\":\"d\",\"tenant_id\":\"default\",\"endpoint\":\"http://x\",\"enabled\":true}"));
        Agent a = client.getAgent("agent-001");
        assertThat(a.getId()).isEqualTo("agent-001");
        assertThat(a.getTenantId()).isEqualTo("default");
    }

    @Test
    void getAgent_serverError_throws5xx() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(err(503, "{\"error\":\"upstream down\"}"));
        assertThatThrownBy(() -> client.getAgent("a1"))
                .isInstanceOf(WauServerException.class)
                .hasFieldOrPropertyWithValue("statusCode", 503);
    }

    // === 4. sampleLLM ===

    @Test
    void sampleLLM_succeeds() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(ok("{\"provider\":\"deepseek-v4-flash\",\"model\":\"deepseek-v4-flash\",\"estimated_cost_usd\":0.001,\"tokens_estimate\":120,\"arm_id\":\"arm-de-1\",\"reason\":\"cheapest\"}"));
        SampleResponse r = client.sampleLLM(SampleRequest.builder()
                .prompt("hi").userId("u").tenantId("default").universeLabel("earth")
                .build());
        assertThat(r.getProvider()).isEqualTo("deepseek-v4-flash");
        assertThat(r.getEstimatedCostUsd()).isEqualTo(0.001);
        assertThat(r.getArmId()).isEqualTo("arm-de-1");
        assertThat(r.getReason()).isEqualTo("cheapest");
    }

    @Test
    void sampleLLM_rateLimited_throws429() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(err(429, "{\"error\":\"too many\"}"));
        assertThatThrownBy(() -> client.sampleLLM(SampleRequest.builder().prompt("x").build()))
                .isInstanceOf(RateLimitException.class);
    }

    // === 5. recordCost ===

    @Test
    void recordCost_succeeds() throws Exception {
        when(mockSender.doRequest(any())).thenReturn(ok("{\"accepted\":true}"));
        client.recordCost(CostEvent.builder()
                .userId("u").tenantId("default")
                .provider("deepseek").model("deepseek-v4-flash")
                .tokensInput(100).tokensOutput(50).costUsd(0.001)
                .latencyMs(120).timestamp(Instant.now())
                .build());
        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(mockSender).doRequest(cap.capture());
        assertThat(cap.getValue().url().toString()).contains("/v1/llm/cost");
        assertThat(cap.getValue().method()).isEqualTo("POST");
    }

    @Test
    void recordCost_nullEvent_throws() {
        assertThatThrownBy(() -> client.recordCost(null))
                .isInstanceOf(NullPointerException.class);
    }

    // === exception / edge cases ===

    @Test
    void sendMessage_httpIOError_throwsWauException() throws Exception {
        when(mockSender.doRequest(any())).thenThrow(new IOException("conn refused"));
        IncomingMessage msg = IncomingMessage.builder()
                .platformMsgId("p").channelId("x").userId("u").text("hi")
                .timestamp(Instant.now()).build();
        assertThatThrownBy(() -> client.sendMessage(msg))
                .isInstanceOf(io.wau.sdk.exception.WauException.class)
                .hasMessageContaining("http call failed");
    }

    @Test
    void config_blankUrl_throws() {
        assertThatThrownBy(() -> new WauConfig.Builder()
                .wauEdgeUrl("not-a-url").botId("bot:d:b").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_blankBotId_throws() {
        assertThatThrownBy(() -> new WauConfig.Builder()
                .wauEdgeUrl("http://x").botId("").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
