package io.wau.sdk.bot.common;

/**
 * 通用 Bot Builder(per `feedback-dev-style` 偏好 builder,5 SDK 100% 一致)。
 *
 * <p>W6 (2026-07-09) W5 缺口补全:Java SDK 补 BotBuilder.java,接口与
 * wau-go-sdk/bot/common/options.go BotBuilder 一字不差。
 *
 * <p>Stage 0 脚手架:基本 builder 骨架。
 * <p>Stage 1 M1 子项 6 实装 BotBuilder 完整选项 + 内部状态管理。
 *
 * <p>用法(对齐 Go SDK):
 * <pre>
 * Bot bot = BotBuilder.newBuilder()
 *     .withTenant("acme")
 *     .withUniverse("us-prod")
 *     .onMessage(msg -&gt; new OutgoingMessage("echo: " + msg.text(), msg.contextId()))
 *     .build();
 * </pre>
 *
 * @since 0.2.1 (W6 W5 缺口补全, 2026-07-09)
 */
public class BotBuilder {

    private String tenantId;
    private String universe;
    private Bot.MessageHandler handler;

    /** 创建 BotBuilder。 */
    public static BotBuilder newBuilder() {
        return new BotBuilder();
    }

    /** 设置 tenant_id,支持链式调用。 */
    public BotBuilder withTenant(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /** 设置 Universe 标签,支持链式调用。 */
    public BotBuilder withUniverse(String universe) {
        this.universe = universe;
        return this;
    }

    /** 注册消息处理 handler,支持链式调用。 */
    public BotBuilder onMessage(Bot.MessageHandler handler) {
        this.handler = handler;
        return this;
    }

    /** 返回已设置的 tenant_id(供具体 adapter 读取)。 */
    public String tenantId() {
        return tenantId;
    }

    /** 返回已设置的 universe。 */
    public String universe() {
        return universe;
    }

    /** 返回已注册的 handler。 */
    public Bot.MessageHandler handler() {
        return handler;
    }

    /**
     * 占位 Build 入口(Stage 1 雏形期具体 adapter 实现)。
     *
     * <p>Stage 0:返回 null(没有具体 Bot)。
     * <p>Stage 1:各 platform(telegram/discord/webhook) 的 newBot() 函数用 BotBuilder 构建具体 Bot。
     */
    public Bot build() {
        // TODO(stage1-m1): 具体 adapter 接管 build()
        return null;
    }
}