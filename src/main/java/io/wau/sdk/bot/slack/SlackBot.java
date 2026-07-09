package io.wau.sdk.bot.slack;

import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slack Bot SDK 集成(W5 OSS-onboarding closure, 2026-07-13)。
 *
 * <p>完整 SDK 端实现(per W5 Q1=B 反 W4.1 拍板):
 * <ul>
 *   <li>Stage 0 stub (matches Python/TS/Rust/Go Stage 0 pattern, ~150 LoC)</li>
 *   <li>Stage 1 路径: com.slack.api:slack-api-client + socketmode</li>
 *   <li>公共 Bot interface 沿用 M10 N1 拍板</li>
 *   <li>仍走 wau-edge POST /v1/bots/{bot_id}/messages 注册 (per M10 N3)</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public class SlackBot implements Bot {

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final String botToken;
    private final String appToken;
    private final AtomicBoolean running = new AtomicBoolean(false);
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
     * <p>Stage 0 stub:仅设置 running flag,真实 Socket Mode 连接在 Stage 1 实现。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("slack bot already running");
        }
        // Stage 1: Slack.getInstance().methods() + socketmode
    }

    /** 优雅停止 Slack Bot。 */
    @Override
    public void stop() throws Exception {
        running.set(false);
    }

    /**
     * 公开方法:发送消息到 channel。
     *
     * <p>Stage 0 stub:仅返回 mock message ID,真实 PostMessage 在 Stage 1 实现。
     */
    public String postMessage(String channel, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("slack bot not running");
        }
        // Stage 1: chatPostMessage
        return "slack.mock.msg." + channel;
    }

    /**
     * 公开方法:更新消息。
     *
     * <p>Stage 0 stub:仅返回,真实 UpdateMessage 在 Stage 1 实现。
     */
    public void updateMessage(String channel, String ts, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("slack bot not running");
        }
        // Stage 1: chatUpdate
    }

    /**
     * 走 wau-core-kernel 提交 prompt (per W4.1 公共契约)。
     */
    public String submitToCore(String prompt) throws Exception {
        // Stage 1: WauClient.Tasks.submit
        return "slack.submit." + prompt.hashCode();
    }

    public String getBotToken() { return botToken; }
    public String getAppToken() { return appToken; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
}
