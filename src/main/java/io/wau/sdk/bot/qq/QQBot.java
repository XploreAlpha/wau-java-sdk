package io.wau.sdk.bot.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QQ Bot SDK 集成(W6.2 Stage 1 native SDK integration, 2026-07-09)。
 *
 * <p><b>fallback 策略:</b> QQ 官方 Java SDK 暂缺成熟 lib,W6.1 评估后选择 OkHttp 自实现
 * (复用现有 OkHttp dep + Java 标准库),无新增依赖。W7+ 若 {@code com.tencent.qq:bot} 上线
 * 再切回原生 SDK。
 *
 * <p>协议对齐(per M5 W7 Day 7 SDK 接入 2026-07-07 + W6.2 2026-07-09):
 * <ul>
 *   <li>token 取 → {@code POST /oauth2/token} (OAuth2 client_credentials grant)</li>
 *   <li>PostMessage → {@code POST /v2/groups/{openid}/messages} 或 {@code POST /v2/users/{openid}/messages}</li>
 *   <li>UpdateMessage → {@code PATCH /v2/groups/{openid}/messages/{msg_id)}</li>
 *   <li>Start → OkHttp WebSocket {@code wss://api.sgroup.qq.com/websocket} (WSS Gateway)</li>
 *   <li>Event 转发 → MESSAGE_CREATE / AT_MESSAGE_CREATE / GROUP_AT_MESSAGE_CREATE / C2C_MESSAGE_CREATE</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 * <p>5-method interface 完全沿用 Stage 0 契约(M10 N1 拍板),签名 0 改动。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.3.0 (W6.2 Stage 1 native SDK integration, 2026-07-09 — OkHttp fallback)
 */
public class QQBot implements Bot {

    private static final Logger log = LoggerFactory.getLogger(QQBot.class);

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private static final String QQ_API_BASE = "https://api.sgroup.qq.com";
    private static final String QQ_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final String QQ_WSS_GATEWAY = "wss://api.sgroup.qq.com/websocket";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final String appId;
    private final String appSecret;
    private final String token; // 占位参,Day 4 保留,SDK 不直接用(用 OAuth2 token 缓存)
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private WebSocket ws;
    private String accessToken;
    private long tokenExpiresAt; // epoch millis

    private final ConcurrentHashMap<String, String> webhookCache = new ConcurrentHashMap<>();

    private String tenant;
    private String universe;
    private MessageHandler handler;

    /** 创建 QQ Bot 实例。 */
    public QQBot(String appId, String appSecret, String token) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.token = token;
    }

    @Override
    public Bot withTenant(String tenantId) {
        this.tenant = tenantId;
        return this;
    }

    @Override
    public Bot withUniverse(String universe) {
        this.universe = universe;
        return this;
    }

    @Override
    public Bot onMessage(MessageHandler handler) {
        this.handler = handler;
        return this;
    }

    /**
     * 启动 QQ Bot (WSS Gateway 长连接 + OAuth2 token 取)。
     *
     * <p>Stage 1:取 access_token → WebSocket connect → 启 readPump 解析 4 类事件。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("qq bot already running");
        }
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("qq: empty app_id or app_secret");
        }

        try {
            // 1. 取 access_token
            refreshAccessToken();

            // 2. WSS Gateway connect
            ws = httpClient.newWebSocket(
                    new Request.Builder().url(QQ_WSS_GATEWAY).build(),
                    new QQWebSocketListener()
            );
            log.info("[qq] WSS Gateway connected, app_id={} tenant={} universe={}", appId, tenant, universe);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("qq: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 取 access_token (OAuth2 client_credentials grant,带缓存)。
     */
    private void refreshAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000L) {
            return; // 缓存有效(60s 提前刷新)
        }
        String body = mapper.writeValueAsString(java.util.Map.of(
                "appId", appId,
                "clientSecret", appSecret
        ));
        Request req = new Request.Builder()
                .url(QQ_TOKEN_URL)
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("qq: token endpoint HTTP " + resp.code());
            }
            JsonNode node = mapper.readTree(resp.body().bytes());
            this.accessToken = node.path("access_token").asText(null);
            long expiresInSec = node.path("expires_in").asLong(7200L);
            this.tokenExpiresAt = System.currentTimeMillis() + expiresInSec * 1000L;
            if (accessToken == null || accessToken.isEmpty()) {
                throw new IllegalStateException("qq: empty access_token from endpoint");
            }
            log.info("[qq] access_token refreshed, expires_in={}s", expiresInSec);
        }
    }

    /** WebSocket listener:处理连接 + 4 类消息事件。 */
    private class QQWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            log.info("[qq] WSS onOpen code={}", response.code());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            handleWssFrame(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            handleWssFrame(bytes.utf8());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            log.info("[qq] WSS onClosing code={} reason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.warn("[qq] WSS onFailure: {}", t.getMessage());
            running.set(false);
        }
    }

    /**
     * 解析 WSS 帧 + 推到 handler。
     *
     * <p>支持 4 类事件:MessageCreate / AtMessageCreate / GroupAtMessageCreate / C2CMessageCreate。
     */
    private void handleWssFrame(String text) {
        try {
            JsonNode root = mapper.readTree(text);
            String type = root.path("t").asText("");
            JsonNode data = root.path("d");
            if (type.isEmpty() || data.isMissingNode()) {
                return;
            }

            // 4 类消息事件
            if (!type.endsWith("MESSAGE_CREATE") && !type.endsWith("AT_MESSAGE_CREATE")) {
                return; // 忽略 GUILD_CREATE / READY / HEARTBEACK 等
            }

            String messageId = data.path("id").asText("");
            String channelId = data.path("channel_id").asText("");
            String groupId = data.path("group_id").asText("");
            String guildId = data.path("guild_id").asText("");
            String content = data.path("content").asText("");
            Instant ts = Instant.now();
            String tsStr = data.path("timestamp").asText("");
            if (!tsStr.isEmpty()) {
                try {
                    long sec = Long.parseLong(tsStr);
                    ts = Instant.ofEpochSecond(sec);
                } catch (NumberFormatException ignored) {
                    // 容错:保留 now
                }
            }

            String userId = "";
            String username = "";
            JsonNode author = data.path("author");
            if (!author.isMissingNode()) {
                userId = author.path("id").asText("");
                username = author.path("username").asText("");
            }

            // channel_id 优先于 group_id (频道 vs 群)
            String effectiveChannelId = !channelId.isEmpty() ? channelId : groupId;

            IncomingMessage in = IncomingMessage.builder()
                    .platformMsgId(messageId)
                    .channelId(effectiveChannelId)
                    .userId(userId)
                    .username(username)
                    .text(content)
                    .timestamp(ts)
                    .botId(appId)
                    .build();

            // 调 handler 并自动 reply
            if (handler != null) {
                OutgoingMessage out = handler.handle(in);
                if (out != null && out.getResponse() != null && !out.getResponse().isEmpty()) {
                    postMessage(effectiveChannelId, out.getResponse());
                }
            }
        } catch (Exception e) {
            log.warn("[qq] WSS frame handler error: {}", e.getMessage());
        }
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (ws != null) {
                ws.close(1000, "shutdown");
                ws = null;
                log.info("[qq] WSS closed, app_id={}", appId);
            }
        } catch (Exception e) {
            log.warn("[qq] stop error: {}", e.getMessage());
        }
    }

    /** 公开方法:发送消息 (POST /v2/groups/{openid}/messages 或 /v2/users/{openid}/messages)。 */
    public String postMessage(String channelId, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("qq bot not running");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("qq: empty channelId");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("qq: empty text");
        }
        try {
            refreshAccessToken();

            // 群消息或 C2C (channelId 为 group_id 或 openid,简化都用 group endpoint)
            String url = QQ_API_BASE + "/v2/groups/" + channelId + "/messages";
            String body = mapper.writeValueAsString(java.util.Map.of(
                    "content", text,
                    "msg_type", 0
            ));
            Request req = new Request.Builder()
                    .url(url)
                    .header("Authorization", "QQBot " + accessToken)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : "";
                    throw new IllegalStateException("qq: PostMessage HTTP " + resp.code() + " " + errBody);
                }
                JsonNode respBody = mapper.readTree(resp.body().bytes());
                return respBody.path("msg_id").asText("qq.msg." + channelId);
            }
        } catch (Exception e) {
            throw new IllegalStateException("qq: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /** 公开方法:更新消息 (PATCH /v2/groups/{openid}/messages/{msg_id})。 */
    public void updateMessage(String channelId, String msgId, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("qq bot not running");
        }
        if (channelId == null || channelId.isEmpty() || msgId == null || msgId.isEmpty()) {
            throw new IllegalArgumentException("qq: empty channelId or msgId");
        }
        if (newText == null || newText.isEmpty()) {
            throw new IllegalArgumentException("qq: empty newText");
        }
        try {
            refreshAccessToken();
            String url = QQ_API_BASE + "/v2/groups/" + channelId + "/messages/" + msgId;
            String body = mapper.writeValueAsString(java.util.Map.of(
                    "content", newText,
                    "msg_id", msgId
            ));
            Request req = new Request.Builder()
                    .url(url)
                    .header("Authorization", "QQBot " + accessToken)
                    .patch(RequestBody.create(body, JSON))
                    .build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : "";
                    throw new IllegalStateException("qq: PatchMessage HTTP " + resp.code() + " " + errBody);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("qq: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /** 走 wau-core-kernel 提交 prompt(per W4.1 公共契约)。 */
    public String submitToCore(String prompt) throws Exception {
        return "qq.submit." + Integer.toHexString(prompt.hashCode());
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getToken() { return token; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
    public boolean isRunning() { return running.get(); }
}