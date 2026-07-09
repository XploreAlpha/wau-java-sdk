package io.wau.sdk.bot.feishu;

import io.wau.sdk.bot.common.Bot;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feishu/Lark Bot SDK 集成(W5 OSS-onboarding closure, 2026-07-13)。
 *
 * <p>完整 SDK 端实现(per W5 Q1=B 反 W4.1 拍板):
 * <ul>
 *   <li>Stage 0 stub (matches Python/TS/Rust/Go Stage 0 pattern, ~150 LoC)</li>
 *   <li>Stage 1 路径: com.larksuite.oapi:oapi-sdk-java + larkws + larkim</li>
 *   <li>公共 Bot interface 沿用 M10 N1 拍板</li>
 *   <li>仍走 wau-edge POST /v1/bots/{bot_id}/messages 注册 (per M10 N3)</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public class FeishuBot implements Bot {

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final String appId;
    private final String appSecret;
    private final AtomicBoolean running = new AtomicBoolean(false);
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
     * 启动 Feishu WS (Stage 0 stub)。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("feishu bot already running");
        }
        // Stage 1: larkws Client + dispatcher
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        running.set(false);
    }

    /** 公开方法:发送消息到 chat (Stage 0 stub)。 */
    public String postMessage(String chatId, String text) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("feishu bot not running");
        }
        // Stage 1: larkim.Message
        return "feishu.mock.msg." + chatId;
    }

    /** 公开方法:更新消息 (Stage 0 stub)。 */
    public void updateMessage(String chatId, String msgId, String newText) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("feishu bot not running");
        }
        // Stage 1: larkim.Update
    }

    /** 走 wau-core-kernel 提交 prompt。 */
    public String submitToCore(String prompt) throws Exception {
        // Stage 1: WauClient.Tasks.submit
        return "feishu.submit." + prompt.hashCode();
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
}
