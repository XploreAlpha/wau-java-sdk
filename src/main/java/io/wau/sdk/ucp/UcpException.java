package io.wau.sdk.ucp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.wau.sdk.exception.WauException;

import java.util.Map;
import java.util.Objects;

/**
 * UCP client 异常 + RPCError(per D88.9, W3 实装)。
 *
 * <h2>层级</h2>
 * <pre>
 *   UcpException (extends WauException,root)
 *   ├── UcpException.RpcError  — JSON-RPC error envelope(code + message + data)
 *   ├── UcpException.NotImplemented — W3 stub 友好路径
 *   └── UcpException.Config    — 本地校验(product_id 空等)
 * </pre>
 *
 * <h2>Error code(10 数字常量,5 SDK 一致)</h2>
 * <ul>
 *   <li>JSON-RPC spec 5 个:-32700 PARSE / -32600 INVALID_REQUEST / -32601 METHOD_NOT_FOUND /
 *       -32602 INVALID_PARAMS / -32603 INTERNAL</li>
 *   <li>UCP-specific 5 个:-32101 UCP_PRODUCT_NOT_FOUND / -32102 UCP_ORDER_NOT_FOUND /
 *       -32103 UCP_PAYMENT_FAILED / -32104 UCP_CART_EXPIRED / -32105 UCP_STRIPE_ERROR</li>
 * </ul>
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public class UcpException extends WauException {

    private static final long serialVersionUID = 1L;

    // === JSON-RPC 2.0 spec error codes ===
    public static final int ERR_CODE_PARSE = -32700;
    public static final int ERR_CODE_INVALID_REQUEST = -32600;
    public static final int ERR_CODE_METHOD_NOT_FOUND = -32601;
    public static final int ERR_CODE_INVALID_PARAMS = -32602;
    public static final int ERR_CODE_INTERNAL = -32603;

    // === UCP-specific error codes ===
    public static final int ERR_CODE_UCP_PRODUCT_NOT_FOUND = -32101;
    public static final int ERR_CODE_UCP_ORDER_NOT_FOUND = -32102;
    public static final int ERR_CODE_UCP_PAYMENT_FAILED = -32103;
    public static final int ERR_CODE_UCP_CART_EXPIRED = -32104;
    public static final int ERR_CODE_UCP_STRIPE_ERROR = -32105;

    public UcpException(String message) {
        super(message);
    }

    public UcpException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 本地校验失败(productId 空 / cartId null 等)。 */
    public static class Config extends UcpException {
        private static final long serialVersionUID = 1L;
        public Config(String message) { super(message); }
    }

    /** W3 stub:create_checkout_session / confirm_payment kernel 返 INTERNAL → SDK 抛此。 */
    public static class NotImplemented extends UcpException {
        private static final long serialVersionUID = 1L;
        public NotImplemented(String message) { super(message); }
    }

    /** JSON-RPC error envelope(code + message + data)。 */
    public static class RpcError extends UcpException {
        private static final long serialVersionUID = 1L;
        private final int code;
        private final Map<String, Object> data;

        public RpcError(int code, String message, Map<String, Object> data) {
            super("UCP RPC error " + code + ": " + message);
            this.code = code;
            this.data = data;
        }

        public int code() { return code; }
        public Map<String, Object> data() { return data; }
    }

    /**
     * JSON-RPC 2.0 error envelope DTO(对应 kernel {@code ucp_types.go} RPCError)。
     */
    public static final class RPCError {
        @JsonProperty("code")
        private int code;
        @JsonProperty("message")
        private String message;
        @JsonProperty("data")
        @SuppressWarnings("rawtypes")
        private Map data;

        public RPCError() {}

        public RPCError(int code, String message, Map data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int code() { return code; }
        public String message() { return message; }
        public Map data() { return data; }
    }

    /** JSON-RPC envelope 外层 dispatch 错误(message 非空但 code 0)。 */
    public static class Malformed extends UcpException {
        private static final long serialVersionUID = 1L;
        public Malformed(String message) { super(message); }
    }

    // === helpers(静态方法,per design doc §八)===

    /** {@code isNotFound(err)} — 是否 Product/Order NotFound error。 */
    public static boolean isNotFound(Throwable err) {
        Objects.requireNonNull(err, "err");
        if (err instanceof RpcError) {
            int c = ((RpcError) err).code();
            return c == ERR_CODE_UCP_PRODUCT_NOT_FOUND || c == ERR_CODE_UCP_ORDER_NOT_FOUND;
        }
        return false;
    }

    /** {@code isStripeError(err)} — 是否 Stripe / 支付失败错误。 */
    public static boolean isStripeError(Throwable err) {
        Objects.requireNonNull(err, "err");
        if (err instanceof RpcError) {
            int c = ((RpcError) err).code();
            return c == ERR_CODE_UCP_STRIPE_ERROR || c == ERR_CODE_UCP_PAYMENT_FAILED;
        }
        return false;
    }

    /** 把服务端 INTERNAL + "W5 Stripe 集成中" 转 NotImplemented friendly。 */
    public static UcpException wrapW3Stub(RpcError original) {
        Objects.requireNonNull(original, "original");
        // 匹配 W3 stub: code == INTERNAL 且 message 含 "W5 Stripe"
        if (original.code() == ERR_CODE_INTERNAL
                && original.message() != null
                && original.message().contains("W5 Stripe")) {
            return new NotImplemented(
                    "W5 Stripe 集成中,当前 stub (" + original.message() + ")"
            );
        }
        return original;
    }
}
