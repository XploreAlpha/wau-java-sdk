package io.wau.sdk.exception;

/**
 * 资源未找到异常(404 — wallet / agent / bot 不存在)。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public class WauNotFoundException extends WauException {

    private static final long serialVersionUID = 1L;

    public WauNotFoundException(String message) {
        super(404, message);
    }
}
