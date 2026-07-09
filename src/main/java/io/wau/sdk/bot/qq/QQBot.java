package io.wau.sdk.bot.qq;

import io.wau.sdk.bot.common.Bot;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QQ Bot SDK 集成(W5 OSS-onboarding closure, 2026-07-13)。
 *
 * <p>完整 SDK 端实现(per W5 Q1=B 反 W4.1 拍板):
 * <ul>
 *   <li>Stage 0 stub (matches Python/TS/Rust/Go Stage 0 pattern)</li>
 *   <li>Stage 1 路径: qq-bot-java (待确认) + websocket</li>
 *   <li>公共 Bot interface 沿用 M10 N1 拍板</li>
 *   <li>仍走 wau-edge POST /v1/bots/{bot_id}/messages 注册 (per M10 N3)</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public class QQBot implements Bot {

    private final String appId;
    private final String appSecret;
    private final String token;
    private final AtomicBoolean running = new AtomicBoolean(false);
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
     * 启动 QQ Bot (Stage 0 stub)。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("qq bot already running");
        }
        // Stage 1: botgo New + websocket
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        running.set(false);
    }

    /** 公开方法:发送消息 (Stage 0 stub)。 */
    public String postMessage(String channelId, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("qq bot not running");
        }
        // Stage 1: PostMessage
        return "qq.mock.msg." + channelId;
    }

    /** 公开方法:更新消息 (Stage 0 stub)。 */
    public void updateMessage(String channelId, String msgId, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("qq bot not running");
        }
    }

    /** 走 wau-core-kernel 提交 prompt。 */
    public String submitToCore(String prompt) throws Exception {
        return "qq.submit." + prompt.hashCode();
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getToken() { return token; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
}
