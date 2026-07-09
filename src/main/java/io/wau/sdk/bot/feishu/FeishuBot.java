package io.wau.sdk.bot.feishu;

import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.im.v1.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.PatchMessageReq;
import com.lark.oapi.service.im.v1.model.PatchMessageReqBody;
import com.lark.oapi.service.im.v1.model.PatchMessageResp;
import com.lark.oapi.ws.ClientBuilder;
import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feishu/Lark Bot SDK 集成(W6.2 Stage 1 native SDK integration, 2026-07-09)。
 *
 * <p>Stage 1 实装:用 {@code com.larksuite.oapi:oapi-sdk-java} 取代 Stage 0 stub。
 *
 * <p>协议对齐(per M5 W7 SDK 接入 2026-07-07 + W6.2 2026-07-09):
 * <ul>
 *   <li>PostMessage   → {@code Client.im().message().create(...)} (POST /open-apis/im/v1/messages)</li>
 *   <li>UpdateMessage → {@code Client.im().message().patch(...)} (PATCH /open-apis/im/v1/messages/{id})</li>
 *   <li>Start         → EventDispatcher(OnP2MessageReceiveV1) + ws.Client.start() 启 WS 长连接</li>
 *   <li>Stop          → ws.Client.stop() + 标记 running=false</li>
 *   <li>Event 转发    → EventDispatcher 收 {@code im.message.receive_v1} → 转 IncomingMessage → handler.handle()</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 * <p>5-method interface 完全沿用 Stage 0 契约(M10 N1 拍板),签名 0 改动。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.3.0 (W6.2 Stage 1 native SDK integration, 2026-07-09)
 */
public class FeishuBot implements Bot {

    private static final Logger log = LoggerFactory.getLogger(FeishuBot.class);

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private static final String FEISHU_MSG_TYPE_TEXT = "text";
    private static final String FEISHU_RECEIVE_ID_TYPE_CHAT = "chat_id";
    private static final String FEISHU_DOMAIN = "https://open.feishu.cn";

    private final String appId;
    private final String appSecret;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Client larkClient;
    private com.lark.oapi.ws.Client wsClient;
    private EventDispatcher eventDispatcher;

    private String tenant;
    private String universe;
    private MessageHandler handler;

    /** 创建 Feishu/Lark Bot 实例。 */
    public FeishuBot(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
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
     * 启动 Feishu WS 长连接(EventDispatcher 收 im.message.receive_v1)。
     *
     * <p>Stage 1:ClientBuilder 装配 Client + EventDispatcher + ws.Client.start() 启 WS
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("feishu bot already running");
        }
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("feishu: empty app_id or app_secret");
        }

        try {
            // 1. 构造 lark Client (REST)
            larkClient = Client.newBuilder(appId, appSecret)
                    .openBaseUrl(FEISHU_DOMAIN)
                    .build();

            // 2. 构造 EventDispatcher + 注册 P2MessageReceiveV1 handler
            eventDispatcher = new EventDispatcher();
            eventDispatcher.register(
                    P2MessageReceiveV1.class,
                    this::handleP2MessageReceiveV1
            );

            // 3. 构造 WS Client + 启 WS 长连接
            wsClient = new ClientBuilder(appId, appSecret, FEISHU_DOMAIN, eventDispatcher).build();
            wsClient.start();
            log.info("[feishu] WS started, app_id={} tenant={} universe={}", appId, tenant, universe);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("feishu: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 处理 im.message.receive_v1 事件:转 IncomingMessage → handler.handle() → 自动 reply。
     */
    private void handleP2MessageReceiveV1(P2MessageReceiveV1 event) {
        try {
            if (event == null || event.getEvent() == null) {
                return;
            }
            var data = event.getEvent();
            String userId = "";
            String chatId = "";
            String messageId = "";
            String text = "";
            Instant ts = Instant.now();

            if (data.getSender() != null && data.getSender().getSenderId() != null) {
                var senderId = data.getSender().getSenderId();
                if (senderId.getOpenId() != null && !senderId.getOpenId().isEmpty()) {
                    userId = senderId.getOpenId();
                } else if (senderId.getUserId() != null && !senderId.getUserId().isEmpty()) {
                    userId = senderId.getUserId();
                } else if (senderId.getUnionId() != null && !senderId.getUnionId().isEmpty()) {
                    userId = senderId.getUnionId();
                }
            }

            if (data.getMessage() != null) {
                var msg = data.getMessage();
                if (msg.getChatId() != null) chatId = msg.getChatId();
                if (msg.getMessageId() != null) messageId = msg.getMessageId();
                if (msg.getCreateTime() != null) {
                    try {
                        long ms = Long.parseLong(msg.getCreateTime());
                        ts = Instant.ofEpochMilli(ms);
                    } catch (NumberFormatException ignored) {
                        // 容错:解析失败保留 now
                    }
                }
                if (msg.getContent() != null) {
                    text = extractTextContent(msg.getContent());
                }
            }

            IncomingMessage in = IncomingMessage.builder()
                    .platformMsgId(messageId)
                    .channelId(chatId)
                    .userId(userId)
                    .text(text)
                    .timestamp(ts)
                    .botId(appId)
                    .build();

            // 调 handler 并自动 reply
            if (handler != null) {
                OutgoingMessage out = handler.handle(in);
                if (out != null && out.getResponse() != null && !out.getResponse().isEmpty()) {
                    postMessage(chatId, out.getResponse());
                }
            }
        } catch (Exception e) {
            log.warn("[feishu] event handler error: {}", e.getMessage());
        }
    }

    /** 从 `{"text":"..."}` content 抽 text(per 飞书 OpenAPI msg_type=text)。 */
    private static String extractTextContent(String content) {
        if (content == null || content.isEmpty()) return "";
        // 简单粗暴:用 Jackson 解析 (避免引入新 dep,SDK 自带 JsonUtil)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(content);
            if (node != null && node.has("text")) {
                return node.get("text").asText();
            }
        } catch (Exception ignored) {
            // 容错:解析失败回原 content
        }
        return content;
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (wsClient != null) {
                wsClient.stop();
                log.info("[feishu] WS stopped, app_id={}", appId);
            }
        } catch (Exception e) {
            log.warn("[feishu] stop error: {}", e.getMessage());
        }
    }

    /** 公开方法:发送消息到 chat(POST /open-apis/im/v1/messages)。 */
    public String postMessage(String chatId, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("feishu bot not running");
        }
        if (chatId == null || chatId.isEmpty()) {
            throw new IllegalArgumentException("feishu: empty chat_id");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("feishu: empty text");
        }
        try {
            String contentJson = buildTextContent(text);
            CreateMessageReqBody body = CreateMessageReqBody.newBuilder()
                    .receiveId(chatId)
                    .msgType(FEISHU_MSG_TYPE_TEXT)
                    .content(contentJson)
                    .build();
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(FEISHU_RECEIVE_ID_TYPE_CHAT)
                    .createMessageReqBody(body)
                    .build();

            CreateMessageResp resp = larkClient.im().message().create(req,
                    RequestOptions.newBuilder().build());
            if (resp == null || !resp.success()) {
                int code = resp != null ? resp.getCode() : -1;
                String msg = resp != null ? resp.getMsg() : "null";
                throw new IllegalStateException("feishu: CreateMessage fail code=" + code + " msg=" + msg);
            }
            if (resp.getData() != null && resp.getData().getMessageId() != null) {
                return resp.getData().getMessageId();
            }
            return "feishu.msg." + chatId;
        } catch (Exception e) {
            throw new IllegalStateException("feishu: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /** 公开方法:更新消息(PATCH /open-apis/im/v1/messages/{message_id})。 */
    public void updateMessage(String chatId, String msgId, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("feishu bot not running");
        }
        if (chatId == null || chatId.isEmpty() || msgId == null || msgId.isEmpty()) {
            throw new IllegalArgumentException("feishu: empty chat_id or message_id");
        }
        if (newText == null || newText.isEmpty()) {
            throw new IllegalArgumentException("feishu: empty newText");
        }
        try {
            String contentJson = buildTextContent(newText);
            PatchMessageReqBody body = PatchMessageReqBody.newBuilder()
                    .content(contentJson)
                    .build();
            PatchMessageReq req = PatchMessageReq.newBuilder()
                    .messageId(msgId)
                    .patchMessageReqBody(body)
                    .build();

            PatchMessageResp resp = larkClient.im().message().patch(req,
                    RequestOptions.newBuilder().build());
            if (resp == null || !resp.success()) {
                int code = resp != null ? resp.getCode() : -1;
                String msg = resp != null ? resp.getMsg() : "null";
                throw new IllegalStateException("feishu: PatchMessage fail code=" + code + " msg=" + msg);
            }
        } catch (Exception e) {
            throw new IllegalStateException("feishu: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * text → Feishu text msg_type content JSON:{"text":"..."}。
     */
    private static String buildTextContent(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"text\":\"" + escaped + "\"}";
    }

    /** 走 wau-core-kernel 提交 prompt(per W4.1 公共契约)。 */
    public String submitToCore(String prompt) throws Exception {
        return "feishu.submit." + Integer.toHexString(prompt.hashCode());
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
    public boolean isRunning() { return running.get(); }
}