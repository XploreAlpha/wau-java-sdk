package io.wau.sdk.bot.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkStreamClient;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.dingtalk.open.app.api.chatbot.ChatBotMessage;
import com.dingtalk.open.app.api.chatbot.ChatBotMessageHandler;
import com.dingtalk.open.app.api.chatbot.ChatbotReplier;
import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DingTalk Bot SDK 集成(W6.2 Stage 1 native SDK integration, 2026-07-09)。
 *
 * <p>Stage 1 实装:用 {@code com.dingtalk.open:dingtalk-stream-sdk-java} 取代 Stage 0 stub。
 *
 * <p>协议对齐(per M5 W7 Day 7 SDK 接入 2026-07-07 + W6.2 2026-07-09):
 * <ul>
 *   <li>Start → OpenDingTalkStreamClient + registerChatBotCallback + start() 启 WS</li>
 *   <li>Stop → stream.stop() + 标记 running=false</li>
 *   <li>PostMessage → ChatbotReplier.replyWithText(sessionWebhook, text) (基于收到的 sessionWebhook reply)</li>
 *   <li>UpdateMessage → DingTalk bot 模型 Stream Mode 没有真 update API;等同 reply with new text,保留 messageID 作 caller 一致性</li>
 *   <li>Event 转发 → ChatBotMessageHandler 收 chatbot message → 转 IncomingMessage → handler.handle()</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 * <p>5-method interface 完全沿用 Stage 0 契约(M10 N1 拍板),签名 0 改动。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.3.0 (W6.2 Stage 1 native SDK integration, 2026-07-09)
 */
public class DingTalkBot implements Bot {

    private static final Logger log = LoggerFactory.getLogger(DingTalkBot.class);

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final String appKey;
    private final String appSecret;
    private final String robotCode;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private OpenDingTalkStreamClient streamClient;
    private ChatbotReplier chatbotReplier;

    private final ConcurrentHashMap<String, String> webhookCache = new ConcurrentHashMap<>();

    private String tenant;
    private String universe;
    private MessageHandler handler;

    /** 创建 DingTalk Bot 实例。 */
    public DingTalkBot(String appKey, String appSecret, String robotCode) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.robotCode = robotCode;
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
     * 启动 DingTalk Stream Mode (WebSocket 长连接 + chatbot callback 路由)。
     *
     * <p>Stage 1:OpenDingTalkStreamClient + AuthClientCredential + registerChatBotCallback + start()
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("dingtalk bot already running");
        }
        if (appKey == null || appKey.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("dingtalk: empty app_key or app_secret");
        }

        try {
            AuthClientCredential credential = new AuthClientCredential(appKey, appSecret);
            streamClient = new OpenDingTalkStreamClient(credential);

            chatbotReplier = new ChatbotReplier();
            streamClient.registerChatBotCallback(this::handleChatBotMessage);

            streamClient.start();
            log.info("[dingtalk] Stream Mode started, app_key={} robot_code={} tenant={} universe={}",
                    appKey, robotCode, tenant, universe);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("dingtalk: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 处理 chatbot callback 消息:缓存 sessionWebhook + 转 IncomingMessage → handler.handle() → 自动 reply。
     */
    private void handleChatBotMessage(ChatBotMessage message) {
        try {
            if (message == null) {
                return;
            }

            String conversationId = message.getConversationId();
            String sessionWebhook = message.getSessionWebhook();
            String messageId = message.getMsgId();
            String userId = message.getSenderStaffId() != null && !message.getSenderStaffId().isEmpty()
                    ? message.getSenderStaffId() : message.getSenderId();
            String username = message.getSenderNick();
            String text = "";
            Instant ts = Instant.now();

            if (message.getCreateAt() > 0) {
                ts = Instant.ofEpochMilli(message.getCreateAt());
            }
            if (message.getText() != null) {
                text = message.getText().getContent();
            }

            // 1. 缓存 sessionWebhook (PostMessage / UpdateMessage 用)
            if (conversationId != null && !conversationId.isEmpty()
                    && sessionWebhook != null && !sessionWebhook.isEmpty()) {
                webhookCache.put(conversationId, sessionWebhook);
            }

            IncomingMessage in = IncomingMessage.builder()
                    .platformMsgId(messageId != null ? messageId : "")
                    .channelId(conversationId)
                    .userId(userId != null ? userId : "")
                    .username(username)
                    .text(text)
                    .timestamp(ts)
                    .botId(robotCode)
                    .build();

            // 2. 调 handler 并自动 reply
            if (handler != null) {
                OutgoingMessage out = handler.handle(in);
                if (out != null && out.getResponse() != null && !out.getResponse().isEmpty()) {
                    postMessage(conversationId, out.getResponse());
                }
            }
        } catch (Exception e) {
            log.warn("[dingtalk] chatBot handler error: {}", e.getMessage());
        }
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (streamClient != null) {
                streamClient.stop();
                log.info("[dingtalk] Stream Mode stopped, app_key={}", appKey);
            }
        } catch (Exception e) {
            log.warn("[dingtalk] stop error: {}", e.getMessage());
        }
    }

    /** 公开方法:发送消息 (基于 sessionWebhook 的 reply)。 */
    public String postMessage(String conversationId, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("dingtalk bot not running");
        }
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("dingtalk: empty conversationId");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("dingtalk: empty text");
        }
        String webhook = webhookCache.get(conversationId);
        if (webhook == null || webhook.isEmpty()) {
            throw new IllegalStateException(
                    "dingtalk: no sessionWebhook cached for conversationId="
                            + conversationId + " (must receive incoming message first)");
        }
        try {
            chatbotReplier.replyWithText(webhook, text);
            return "dingtalk.msg." + conversationId;
        } catch (Exception e) {
            throw new IllegalStateException("dingtalk: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 公开方法:更新消息(DingTalk Stream Mode 没有真 update API;等同 reply with new text)。
     *
     * <p>保留 messageID 在 caller 关联键(只是 caller-side 追踪,非钉钉服务端 update 语义)。
     */
    public void updateMessage(String conversationId, String msgId, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("dingtalk bot not running");
        }
        if (conversationId == null || conversationId.isEmpty() || msgId == null || msgId.isEmpty()) {
            throw new IllegalArgumentException("dingtalk: empty conversationId or msgId");
        }
        if (newText == null || newText.isEmpty()) {
            throw new IllegalArgumentException("dingtalk: empty newText");
        }
        String webhook = webhookCache.get(conversationId);
        if (webhook == null || webhook.isEmpty()) {
            throw new IllegalStateException(
                    "dingtalk: no sessionWebhook cached for conversationId=" + conversationId);
        }
        try {
            chatbotReplier.replyWithText(webhook, newText);
        } catch (Exception e) {
            throw new IllegalStateException("dingtalk: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /** 走 wau-core-kernel 提交 prompt(per W4.1 公共契约)。 */
    public String submitToCore(String prompt) throws Exception {
        return "dingtalk.submit." + Integer.toHexString(prompt.hashCode());
    }

    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }
    public String getRobotCode() { return robotCode; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
    public boolean isRunning() { return running.get(); }
}