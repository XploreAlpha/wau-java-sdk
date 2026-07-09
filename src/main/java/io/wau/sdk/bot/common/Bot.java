package io.wau.sdk.bot.common;

import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;

/**
 * 公共 Bot interface(per M10 N1 拍板:5 SDK 100% 一致)。
 *
 * <p>5 SDK 必须实现的方法签名:
 * <ul>
 *   <li>{@link #start()} - 启动 bot</li>
 *   <li>{@link #stop()} - 优雅停止</li>
 *   <li>{@link #onMessage(MessageHandler)} - 注册消息处理 handler,支持链式调用</li>
 *   <li>{@link #withTenant(String)} - 设置 tenant_id,支持链式调用</li>
 *   <li>{@link #withUniverse(String)} - 设置 Universe 标签,支持链式调用</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public interface Bot {

    /** 启动 bot(长连接 / webhook server)。 */
    void start() throws Exception;

    /** 优雅停止 bot。 */
    void stop() throws Exception;

    /**
     * 注册消息处理 handler,返回 Bot 支持链式调用。
     */
    Bot onMessage(MessageHandler handler);

    /** 设置 tenant_id,返回 Bot 支持链式调用。 */
    Bot withTenant(String tenantId);

    /** 设置 Universe 标签,返回 Bot 支持链式调用。 */
    Bot withUniverse(String universe);

    /**
     * 消息处理 handler 函数式接口。
     */
    @FunctionalInterface
    interface MessageHandler {
        OutgoingMessage handle(IncomingMessage msg);
    }
}
