package io.wau.sdk.exception;

/**
 * Bot 已存在(register 重名,409)或 BotUUID 冲突(per v1.3.0 W2.1 2026-07-10)。
 *
 * <p>HTTP status 409 映射到此异常。
 *
 * @since 1.3.0
 */
public class BotAlreadyExistsException extends WauException {
    public BotAlreadyExistsException(String message) {
        super(409, message);
    }
}
