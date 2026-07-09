package io.wau.sdk.bot.slack;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.request.SocketModeRequest;
import com.slack.api.socket_mode.response.SocketModeResponse;
import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slack Bot SDK 集成(W6.2 Stage 1 native SDK integration, 2026-07-09)。
 *
 * <p>Stage 1 实装:用 {@code com.slack.api:slack-api-client} + SocketModeApp 取代 Stage 0 stub。
 *
 * <p>协议对齐(per M5 W7 SDK 接入 2026-07-07 + W6.2 2026-07-09):
 * <ul>
 *   <li>PostMessage   → {@code Slack.methods().chatPostMessage(...)} (chat.postMessage)</li>
 *   <li>UpdateMessage → {@code Slack.methods().chatUpdate(...)} (chat.update)</li>
 *   <li>Start         → AuthTest 拿 botID + SocketModeApp.start() 启 WS 长连接</li>
 *   <li>Stop          → SocketModeApp.stop() + 关 events queue</li>
 *   <li>Event 转发    → SocketModeApp 监听 EventsAPI message event → 转 IncomingMessage → handler.handle()</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 * <p>5-method interface 完全沿用 Stage 0 契约(M10 N1 拍板),签名 0 改动。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.3.0 (W6.2 Stage 1 native SDK integration, 2026-07-09)
 */
public class SlackBot implements Bot {

    private static final Logger log = LoggerFactory.getLogger(SlackBot.class);

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final String botToken;
    private final String appToken;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Slack slack;
    private SocketModeApp socketModeApp;

    private String botId;
    private String botName;

    private String tenant;
    private String universe;
    private MessageHandler handler;

    /**
     * 创建 Slack Bot 实例。
     *
     * @param botToken Bot User OAuth Token (xoxb-...)
     * @param appToken App-Level Token for Socket Mode (xapp-...)
     */
    public SlackBot(String botToken, String appToken) {
        this.botToken = botToken;
        this.appToken = appToken;
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
     * 启动 Slack Socket Mode (WS 长连接,接收事件)。
     *
     * <p>Stage 1:AuthTest 拿 botID + SocketModeApp.start() 启 WS + 转发 eventsAPI message event → handler
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("slack bot already running");
        }
        if (botToken == null || botToken.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("slack: empty bot token");
        }
        if (appToken == null || appToken.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("slack: empty app token (Socket Mode requires xapp-)");
        }

        try {
            // 1. 构造 Slack client
            slack = Slack.getInstance();

            // 2. AuthTest 拿 botID(per D80 透传 + bot 日志用)
            AuthTestResponse auth = slack.methods(botToken).authTest(r -> r);
            if (auth == null || !auth.isOk()) {
                running.set(false);
                throw new IllegalStateException("slack: auth.test failed: "
                        + (auth != null ? auth.getError() : "null response"));
            }
            this.botId = auth.getBotId();
            this.botName = auth.getUser();
            log.info("[slack] AuthTest OK: bot_id={} bot_name={}", botId, botName);

            // 3. SocketModeApp 注册 events handler
            socketModeApp = new SocketModeApp(appToken, slack);
            socketModeApp.event("message", this::handleEventsApiMessage);

            // 4. 启动 WS(异步,start() 立即返回)
            socketModeApp.start();
            log.info("[slack] Socket Mode started, bot={} tenant={} universe={}", botName, tenant, universe);
        } catch (IOException | SlackApiException e) {
            running.set(false);
            throw new IllegalStateException("slack: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 处理 EventsAPI message event:转 IncomingMessage → handler.handle() → OutgoingMessage 自动 post reply。
     */
    private void handleEventsApiMessage(SocketModeRequest req, SocketModeResponse resp) {
        try {
            if (req.getPayload() == null || req.getPayload().getEvent() == null) {
                return;
            }
            var payload = req.getPayload();
            if (!"message".equals(payload.getEvent().getType())) {
                return;
            }
            // Socket Mode payload 是 Slack 自己的内部类型;直接 unmarshal event JSON
            MessageEvent msg = (MessageEvent) payload.getEvent();
            if (msg == null || msg.getText() == null) {
                return;
            }
            // 跳过 bot 自身消息(防止 echo loop)
            if (msg.getBotId() != null && !msg.getBotId().isEmpty()) {
                return;
            }

            IncomingMessage in = IncomingMessage.builder()
                    .platformMsgId(msg.getTs() != null ? msg.getTs() : "0")
                    .channelId(msg.getChannel())
                    .userId(msg.getUser() != null ? msg.getUser() : "")
                    .username(msg.getUsername())
                    .text(msg.getText())
                    .replyTo(msg.getThreadTs())
                    .timestamp(Instant.now())
                    .botId(botId)
                    .build();

            // 调 handler 并自动 reply
            if (handler != null) {
                OutgoingMessage out = handler.handle(in);
                if (out != null && out.getResponse() != null && !out.getResponse().isEmpty()) {
                    postMessage(in.getChannelId(),
                            out.getResponse().isEmpty() ? in.getText() : out.getResponse());
                }
            }
        } catch (Exception e) {
            log.warn("[slack] event handler error: {}", e.getMessage());
        }
    }

    /** 优雅停止 Slack Bot。 */
    @Override
    public void stop() throws Exception {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (socketModeApp != null) {
                socketModeApp.stop();
                log.info("[slack] Socket Mode stopped, bot={}", botName);
            }
        } catch (Exception e) {
            log.warn("[slack] stop error: {}", e.getMessage());
        }
    }

    /**
     * 公开方法:发送消息到 channel(Slack chat.postMessage API)。
     */
    public String postMessage(String channel, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("slack bot not running");
        }
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("slack: empty channel");
        }
        try {
            ChatPostMessageResponse resp = slack.methods(botToken).chatPostMessage(r -> r
                    .channel(channel)
                    .text(text != null ? text : ""));
            if (resp == null || !resp.isOk()) {
                throw new IllegalStateException("slack: chat.postMessage failed: "
                        + (resp != null ? resp.getError() : "null"));
            }
            return resp.getTs();
        } catch (IOException | SlackApiException e) {
            throw new IllegalStateException("slack: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 公开方法:更新消息(Slack chat.update API)。
     */
    public void updateMessage(String channel, String ts, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("slack bot not running");
        }
        if (channel == null || channel.isEmpty() || ts == null || ts.isEmpty()) {
            throw new IllegalArgumentException("slack: empty channel or ts");
        }
        try {
            ChatUpdateResponse resp = slack.methods(botToken).chatUpdate(r -> r
                    .channel(channel)
                    .ts(ts)
                    .text(newText != null ? newText : ""));
            if (resp == null || !resp.isOk()) {
                throw new IllegalStateException("slack: chat.update failed: "
                        + (resp != null ? resp.getError() : "null"));
            }
        } catch (IOException | SlackApiException e) {
            throw new IllegalStateException("slack: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 走 wau-core-kernel 提交 prompt (per W4.1 公共契约)。
     *
     * <p>Stage 1 caller 占位:实际 SubmitToCore 由 caller 注入 WauClient 走 client.tasks().submit()。
     * 这里保留 stub 接口对齐 Go SDK,实装在 W7+ wau-java-sdk-caller 桥接层。
     */
    public String submitToCore(String prompt) throws Exception {
        // Stage 1: 注入 WauClient 后调 WauClient.tasks().submit()
        return "slack.submit." + Integer.toHexString(prompt.hashCode());
    }

    public String getBotToken() { return botToken; }
    public String getAppToken() { return appToken; }
    public String getBotId() { return botId; }
    public String getBotName() { return botName; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
    public boolean isRunning() { return running.get(); }
}