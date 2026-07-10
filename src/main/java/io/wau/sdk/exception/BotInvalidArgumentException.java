package io.wau.sdk.exception;

/**
 * Bot 注册 / 更新请求缺关键字段(per v1.3.0 W2.1 2026-07-10)。
 *
 * <p>如 bot_id / tenant_id / owner_user_id 任一为空,或 bot_uuid 格式非法。
 * HTTP status 400 映射到此异常(per D62 RPC error code mapping)。
 *
 * @since 1.3.0
 */
public class BotInvalidArgumentException extends WauException {
    public BotInvalidArgumentException(String message) {
        super(400, message);
    }
}
