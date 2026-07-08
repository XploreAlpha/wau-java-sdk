package io.wau.sdk.exception;

/**
 * 速率限制异常(M3 §3.5 ratelimit middleware 返回 429)。
 *
 * <p>5 SDK 共享同 HTTP 429 错误码,per D63 §5.1 同步策略。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public class RateLimitException extends WauException {

    private static final long serialVersionUID = 1L;

    public RateLimitException(String message) {
        super(429, message);
    }
}
