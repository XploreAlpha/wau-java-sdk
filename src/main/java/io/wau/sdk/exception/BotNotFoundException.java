package io.wau.sdk.exception;

/**
 * Bot 未注册,或查询的 bot_id/bot_uuid 不存在(per v1.3.0 W2.1 2026-07-10)。
 *
 * <p>HTTP status 404 映射到此异常(per D79 三向校验 4a)。
 *
 * @since 1.3.0
 */
public class BotNotFoundException extends WauException {
    public BotNotFoundException(String message) {
        super(404, message);
    }
}
