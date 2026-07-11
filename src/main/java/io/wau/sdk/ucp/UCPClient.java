package io.wau.sdk.ucp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.transport.HttpTransport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UCP client — JSON-RPC 2.0 over HTTP,POST 11 commerce tool 到 {@code <baseURL>/ucp}(per D88.9, W3 实装)。
 *
 * <h2>11 commerce tool</h2>
 * <pre>
 *   Product: list_products / get_product / search_products
 *   Cart:    add_to_cart / get_cart / remove_from_cart
 *   Payment: create_checkout_session / confirm_payment / cancel_order
 *   Order:   get_order / list_orders
 * </pre>
 *
 * <h2>JSON-RPC envelope</h2>
 * <pre>
 *   request:  {"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_products","arguments":{...}},"id":N}
 *   response: {"jsonrpc":"2.0","id":N,"result":{...}}     或
 *             {"jsonrpc":"2.0","id":N,"error":{"code":-32101,"message":"...","data":{...}}}
 * </pre>
 *
 * <h2>5 SDK 一致(per D13 byte-equal)</h2>
 * <p>11 tool 名 + 8 DTO 字段 snake_case 5 SDK 共享。本文件镜像
 * wau-go-sdk/ucpclient/client.go + wau-python-sdk/src/wau_sdk/ucp_client.py +
 * wau-typescript-sdk/src/ucp/client.ts + wau-rust-sdk/src/ucp/client.rs。
 *
 * <h2>W3 stub</h2>
 * <p>{@code create_checkout_session} + {@code confirm_payment} W3 期间 kernel 返 {@code INTERNAL}
 * 错误("W5 Stripe 集成中")→ SDK 抛 {@link UcpException.NotImplemented}。
 *
 * <h2>D60 additive</h2>
 * <p>0 改老 {@link io.wau.sdk.WauClient} 5 核心方法 + 老 {@code l5()} getter;新加 {@code ucp()} getter。
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public final class UCPClient {

    /** UCP RPC endpoint path (per D88.1 kernel server design)。 */
    public static final String DEFAULT_ENDPOINT = "/ucp";

    /** User-Agent(per HTTP RFC 7231)。 */
    public static final String DEFAULT_USER_AGENT = "wau-java-sdk/0.2.0-preview (ucp/1.0.0)";

    private final String baseUrl;
    private final String endpoint;
    private final String userAgent;
    private final UcpAuth auth;
    private final HttpTransport transport;
    private final ObjectMapper mapper;
    private final AtomicLong nextId = new AtomicLong(1L);

    public UCPClient(String baseUrl, HttpTransport transport, UcpAuth auth) {
        this(baseUrl, DEFAULT_ENDPOINT, DEFAULT_USER_AGENT, transport, auth);
    }

    public UCPClient(String baseUrl,
                     String endpoint,
                     String userAgent,
                     HttpTransport transport,
                     UcpAuth auth) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        if (this.baseUrl.isEmpty()) {
            throw new UcpException.Config("baseUrl 不能为空");
        }
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (this.endpoint.isEmpty()) {
            throw new UcpException.Config("endpoint 不能为空");
        }
        this.userAgent = userAgent == null ? DEFAULT_USER_AGENT : userAgent;
        this.transport = Objects.requireNonNull(transport, "transport");
        this.auth = auth == null ? UcpAuth.empty() : auth;
        // Reuse transport's mapper(Java 8 time module + disable FAIL_ON_UNKNOWN_PROPERTIES)
        this.mapper = new ObjectMapper();
        this.mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String baseUrl() { return baseUrl; }
    public String endpoint() { return endpoint; }
    public String userAgent() { return userAgent; }
    public UcpAuth auth() { return auth; }

    // ====================================================================
    // 11 commerce tool typed wrapper methods
    // ====================================================================

    /** 1. list_products(per design doc §三)。 */
    public Dto.ListProductsResult listProducts(Dto.ListProductsFilter filter) throws UcpException {
        return callTool(Tools.TOOL_LIST_PRODUCTS, filter, Dto.ListProductsResult.class);
    }

    /** 2. get_product(per design doc §三)。 */
    public Dto.Product getProduct(String productId) throws UcpException {
        requireNonEmpty(productId, "productId");
        Map<String, Object> args = new HashMap<>();
        args.put("product_id", productId);
        return callTool(Tools.TOOL_GET_PRODUCT, args, Dto.Product.class);
    }

    /** 3. search_products(全文搜索,per design doc §三)。 */
    public Dto.SearchProductsResult searchProducts(String query, Integer limit) throws UcpException {
        requireNonEmpty(query, "query");
        Map<String, Object> args = new HashMap<>();
        args.put("query", query);
        if (limit != null) {
            args.put("limit", limit);
        }
        return callTool(Tools.TOOL_SEARCH_PRODUCTS, args, Dto.SearchProductsResult.class);
    }

    /** 4. add_to_cart(per design doc §三)。 */
    public Dto.Cart addToCart(String productId, Integer quantity) throws UcpException {
        requireNonEmpty(productId, "productId");
        int q = (quantity == null || quantity <= 0) ? 1 : quantity;
        Map<String, Object> args = new HashMap<>();
        args.put("product_id", productId);
        args.put("quantity", q);
        return callTool(Tools.TOOL_ADD_TO_CART, args, Dto.Cart.class);
    }

    /** 5. get_cart(per design doc §三)。 */
    public Dto.Cart getCart(String cartId) throws UcpException {
        requireNonEmpty(cartId, "cartId");
        Map<String, Object> args = new HashMap<>();
        args.put("cart_id", cartId);
        return callTool(Tools.TOOL_GET_CART, args, Dto.Cart.class);
    }

    /** 6. remove_from_cart(per design doc §三)。 */
    public Dto.Cart removeFromCart(String cartId, String lineItemId) throws UcpException {
        requireNonEmpty(cartId, "cartId");
        requireNonEmpty(lineItemId, "lineItemId");
        Map<String, Object> args = new HashMap<>();
        args.put("cart_id", cartId);
        args.put("line_item_id", lineItemId);
        return callTool(Tools.TOOL_REMOVE_FROM_CART, args, Dto.Cart.class);
    }

    /**
     * 7. create_checkout_session — W3 stub 友好路径。
     *
     * <p>W3 期间 kernel 返 {@code INTERNAL} 错误("W5 Stripe 集成中")→ SDK 抛
     * {@link UcpException.NotImplemented};W5+ kernel {@code ucp_stripe.go} 落地后
     * 走 {@link Dto.CheckoutSession} 透明转发。
     */
    public Dto.CheckoutSession createCheckoutSession(String cartId) throws UcpException {
        requireNonEmpty(cartId, "cartId");
        Map<String, Object> args = new HashMap<>();
        args.put("cart_id", cartId);
        try {
            return callTool(Tools.TOOL_CREATE_CHECKOUT_SESSION, args, Dto.CheckoutSession.class);
        } catch (UcpException.RpcError e) {
            UcpException wrapped = UcpException.wrapW3Stub(e);
            if (wrapped instanceof UcpException.NotImplemented) throw wrapped;
            throw e;
        }
    }

    /**
     * 8. confirm_payment — W3 stub 友好路径(同上)。
     */
    public Dto.PaymentConfirmation confirmPayment(String sessionId) throws UcpException {
        requireNonEmpty(sessionId, "sessionId");
        Map<String, Object> args = new HashMap<>();
        args.put("session_id", sessionId);
        try {
            return callTool(Tools.TOOL_CONFIRM_PAYMENT, args, Dto.PaymentConfirmation.class);
        } catch (UcpException.RpcError e) {
            UcpException wrapped = UcpException.wrapW3Stub(e);
            if (wrapped instanceof UcpException.NotImplemented) throw wrapped;
            throw e;
        }
    }

    /** 9. get_order(per design doc §三)。 */
    public Dto.Order getOrder(String orderId) throws UcpException {
        requireNonEmpty(orderId, "orderId");
        Map<String, Object> args = new HashMap<>();
        args.put("order_id", orderId);
        return callTool(Tools.TOOL_GET_ORDER, args, Dto.Order.class);
    }

    /** 10. list_orders(per design doc §三)。 */
    public Dto.ListOrdersResult listOrders(String userId, Dto.ListOrdersFilter filter) throws UcpException {
        requireNonEmpty(userId, "userId");
        Map<String, Object> args = new HashMap<>();
        args.put("user_id", userId);
        if (filter != null) {
            if (filter.getStatus() != null) args.put("status", filter.getStatus());
            if (filter.getMinTotalCents() != null) args.put("min_total_cents", filter.getMinTotalCents());
            if (filter.getPage() != null) args.put("page", filter.getPage());
            if (filter.getPageSize() != null) args.put("page_size", filter.getPageSize());
        }
        return callTool(Tools.TOOL_LIST_ORDERS, args, Dto.ListOrdersResult.class);
    }

    /** 11. cancel_order(per design doc §三 + Stripe refund 透明)。 */
    public Dto.CancelOrderResult cancelOrder(String orderId) throws UcpException {
        requireNonEmpty(orderId, "orderId");
        Map<String, Object> args = new HashMap<>();
        args.put("order_id", orderId);
        return callTool(Tools.TOOL_CANCEL_ORDER, args, Dto.CancelOrderResult.class);
    }

    // ====================================================================
    // === private JSON-RPC dispatcher ===
    // ====================================================================

    /**
     * 通用 JSON-RPC dispatch:build envelope + POST + 解析 envelope.response.
     *
     * @param toolName 11 commerce tool 之一(per {@link Tools})
     * @param arguments 调用参数(Map / Record),可空(get_product 等会传 Map)
     * @param resultType {@link Class}&lt;T&gt; 响应 DTO class
     * @param <T> 响应 DTO 类型
     * @return 响应 DTO 实例
     * @throws UcpException.RpcError  当服务端返 JSON-RPC error envelope
     * @throws UcpException.Malformed 当 envelope 缺 result/error / status 非 2xx
     */
    @SuppressWarnings("unchecked")
    private <T> T callTool(String toolName, Object arguments, Class<T> resultType) throws UcpException {
        Objects.requireNonNull(toolName, "toolName");
        long id = nextId.getAndIncrement();

        // Build JSON-RPC envelope
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        if (arguments != null) {
            params.put("arguments", arguments);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", "tools/call");
        body.put("params", params);
        body.put("id", id);

        // Build headers (Accept + auth + user-agent)
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", userAgent);
        auth.apply(headers);

        // Execute via HttpTransport — JSON-RPC layer can return 200 + error envelope,
        // so we capture raw Map and unwrap. Non-2xx HTTP → WauException → rethrow.
        String url = baseUrl + endpoint;
        Map<String, Object> envelope;
        try {
            envelope = transport.postJson(url, headers, body,
                    new TypeReference<Map<String, Object>>() {});
        } catch (WauException e) {
            // Non-2xx HTTP response (404/5xx/409 etc) — HttpTransport.validateStatus
            // mapped it to a typed exception. Re-surface as Malformed with status.
            throw new UcpException.Malformed(
                    "UCP HTTP error status=" + e.getStatusCode() + ": " + e.getMessage());
        }
        return unwrapEnvelope(envelope, resultType);
    }

    /**
     * Unwrap JSON-RPC envelope response:
     * <ul>
     *   <li>"result" → decode into resultType DTO</li>
     *   <li>"error" → build UcpException.RpcError</li>
     *   <li>neither → throw Malformed</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private <T> T unwrapEnvelope(Map<String, Object> envelope, Class<T> resultType) {
        if (envelope == null) {
            throw new UcpException.Malformed("empty envelope");
        }
        if (envelope.containsKey("error")) {
            Object errObj = envelope.get("error");
            if (!(errObj instanceof Map)) {
                throw new UcpException.Malformed("error field not a JSON object: " + errObj);
            }
            Map<String, Object> err = (Map<String, Object>) errObj;
            int code = intFromObj(err.get("code"));
            String msg = strFromObj(err.get("message"));
            Object data = err.get("data");
            Map<String, Object> dataMap = (data instanceof Map) ? (Map<String, Object>) data : null;
            UcpException.RpcError rpc = new UcpException.RpcError(code, msg, dataMap);
            UcpException wrapped = UcpException.wrapW3Stub(rpc);
            if (wrapped instanceof UcpException.NotImplemented) throw (UcpException.NotImplemented) wrapped;
            throw rpc;
        }
        if (envelope.containsKey("result")) {
            Object result = envelope.get("result");
            if (result == null) {
                return null;
            }
            try {
                return mapper.convertValue(result, resultType);
            } catch (Exception e) {
                throw new UcpException.Malformed(
                        "failed to deserialize result into " + resultType.getSimpleName() + ": " + e.getMessage());
            }
        }
        throw new UcpException.Malformed("envelope 缺 result/error: " + envelope.keySet());
    }

    // === helpers ===

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new UcpException.Config(name + " 不能为空");
        }
    }

    private static int intFromObj(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private static String strFromObj(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
