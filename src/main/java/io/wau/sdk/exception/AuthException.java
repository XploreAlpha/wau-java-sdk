package io.wau.sdk.exception;

/**
 * 鉴权异常(D79 三向校验失败 — per [[2026-07-05-D79-multi-bot-auth]])。
 *
 * <p>D79 三向校验:user_id + bot_id + owner_user_id 必须全部存在。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public class AuthException extends WauException {

    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(401, message);
    }

    public AuthException(String message, Throwable cause) {
        super(401, message, cause);
    }
}
