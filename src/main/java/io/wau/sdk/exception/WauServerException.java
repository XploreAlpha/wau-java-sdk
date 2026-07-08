package io.wau.sdk.exception;

/**
 * 上游 5xx 故障(wau-edge / wau-core / wau-store 任何上游返 5xx)。
 *
 * <p>五 SDK 共享同 HTTP 5xx 错误码,per D63 §5.1 同步策略。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public class WauServerException extends WauException {

    private static final long serialVersionUID = 1L;

    public WauServerException(int statusCode, String message) {
        super(statusCode, message);
    }

    public WauServerException(int statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
