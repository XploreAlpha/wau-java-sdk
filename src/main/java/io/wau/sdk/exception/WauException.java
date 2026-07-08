package io.wau.sdk.exception;

/**
 * wau-java-sdk 顶层异常基类(per D63 启动备忘 §4.1.1)。
 *
 * <p>所有 SDK 抛出的异常都继承自本类,便于 caller
 * 通过单一 catch {@code WauException} 捕获所有 SDK 错误。
 *
 * <h2>层级</h2>
 * <pre>
 *   WauException (基类,extends RuntimeException)
 *   ├── AuthException              — D79 三向校验失败
 *   ├── RateLimitException         — M3 限流(429)
 *   ├── WauNotFoundException       — 404 wallet/agent/bot miss
 *   └── WauServerException         — 5xx 上游故障
 * </pre>
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public class WauException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP status code(若适用);0 表示非 HTTP 来源(本地校验失败). */
    private final int statusCode;

    public WauException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public WauException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public WauException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public WauException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code(0 = 非 HTTP 来源). */
    public int getStatusCode() {
        return statusCode;
    }
}
