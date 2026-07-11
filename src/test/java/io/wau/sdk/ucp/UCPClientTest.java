package io.wau.sdk.ucp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.wau.sdk.transport.HttpTransport;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D88.9 (2026-07-11) UCPClient 单测,~22 测试覆盖:
 * <ul>
 *   <li>11 commerce tool happy path(callTool envelope round-trip)</li>
 *   <li>W3 stub:create_checkout_session + confirm_payment → NotImplemented</li>
 *   <li>Error path:JSON-RPC error envelope + non-2xx HTTP + malformed</li>
 *   <li>Auth helpers:setBearerToken / setTenantId / UcpAuth.apply</li>
 *   <li>Stripe helper:isStripePath + PAYMENT_STATUS constants</li>
 *   <li>JSON-RPC envelope sanity:method=tools/call, params.name=tool</li>
 * </ul>
 *
 * <p>用 {@link HttpTransport.Sender} mock(无需 mockwebserver),handler 完整循环可测。
 * Pattern 镜像 {@code BotsClientTest} +4 SDK tests(wau-go-sdk ucpclient/client_test.go /
 * wau-python-sdk tests/test_ucp_client.py / wau-typescript-sdk tests/ucp/client.test.ts /
 * wau-rust-sdk tests/ucp_client_test.rs)。
 *
 * @since 0.2.0
 */
@DisplayName("v1.3.3 UCPClient — 11 tool + W3 stub + error path + auth (22 tests)")
class UCPClientTest {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String BASE = "https://kernel.example.com";

    private ObjectMapper mapper;
    private CapturingSender sender;
    private HttpTransport transport;
    private UCPClient client;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        sender = new CapturingSender();
        transport = new HttpTransport(sender);
        client = new UCPClient(BASE, transport, new UcpAuth("oauth-jwt-test", "tenant-A"));
    }

    // ====================================================================
    // 11 commerce tool happy path
    // ====================================================================

    @Test
    @DisplayName("1. list_products — POST /ucp with method=tools/call, params.name=list_products")
    void list_products() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "products", List.of(
                        product("p-1", "Hat", 9950),
                        product("p-2", "Pin", 1990)
                ),
                "total", 2, "page", 1, "page_size", 20
        )));

        Dto.ListProductsResult got = client.listProducts(null);
        assertEquals(2, got.getTotal());
        assertEquals(2, got.getProducts().size());
        assertEquals("p-1", got.getProducts().get(0).getProductId());

        Request req = sender.lastRequest();
        assertTrue(req.url().encodedPath().endsWith("/ucp"), "path: " + req.url().encodedPath());
        String body = sender.lastBody();
        assertTrue(body.contains("\"method\":\"tools/call\""), "body: " + body);
        assertTrue(body.contains("\"name\":\"list_products\""), "body: " + body);
    }

    @Test
    @DisplayName("2. get_product — 必填 productId, 返 Product DTO")
    void get_product() {
        sender.queueJson(200, envelopeOk(1, product("p-9", "Beanie", 4900)));
        Dto.Product p = client.getProduct("p-9");
        assertEquals("p-9", p.getProductId());
        assertEquals("Beanie", p.getName());
        assertEquals(4900, p.getPriceCents());
    }

    @Test
    @DisplayName("get_product empty productId → UcpException.Config")
    void get_product_empty() {
        UcpException ex = assertThrows(UcpException.Config.class, () -> client.getProduct(""));
        assertTrue(ex.getMessage().contains("productId"));
    }

    @Test
    @DisplayName("3. search_products — query + 可选 limit")
    void search_products() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "products", List.of(product("p-7", "Coffee", 2900)),
                "total", 1, "query", "coffee"
        )));
        Dto.SearchProductsResult res = client.searchProducts("coffee", 10);
        assertEquals(1, res.getTotal());
        assertEquals("coffee", res.getQuery());
    }

    @Test
    @DisplayName("search_products empty query → UcpException.Config")
    void search_products_empty() {
        assertThrows(UcpException.Config.class, () -> client.searchProducts("", null));
    }

    @Test
    @DisplayName("4. add_to_cart — productId + quantity, 缺省 quantity=1")
    void add_to_cart() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "cart_id", "cart-1", "user_id", "u-1",
                "line_items", List.of(Map.of(
                        "line_item_id", "li-1", "product_id", "p-1", "quantity", 2)),
                "total_cents", 19900, "currency", "CNY"
        )));
        Dto.Cart cart = client.addToCart("p-1", 2);
        assertEquals("cart-1", cart.getCartId());
        assertEquals(19900, cart.getTotalCents());
        assertEquals("CNY", cart.getCurrency());
    }

    @Test
    @DisplayName("add_to_cart quantity=0 自动归 1(不传 0 给上游)")
    void add_to_cart_qty_zero_defaults_one() {
        sender.queueJson(200, envelopeOk(1, Map.of("cart_id", "cart-x")));
        client.addToCart("p-1", 0);
        String body = sender.lastBody();
        assertTrue(body.contains("\"quantity\":1"), "body must default qty to 1: " + body);
    }

    @Test
    @DisplayName("5. get_cart — cartId 必填")
    void get_cart() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "cart_id", "cart-99", "total_cents", 5000
        )));
        Dto.Cart cart = client.getCart("cart-99");
        assertEquals("cart-99", cart.getCartId());
        assertEquals(5000, cart.getTotalCents());
    }

    @Test
    @DisplayName("6. remove_from_cart — cartId + lineItemId 必填")
    void remove_from_cart() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "cart_id", "cart-1", "line_items", List.of()
        )));
        Dto.Cart cart = client.removeFromCart("cart-1", "li-1");
        assertEquals("cart-1", cart.getCartId());
    }

    @Test
    @DisplayName("remove_from_cart 空 cartId/lineItemId → Config")
    void remove_from_cart_empty() {
        assertThrows(UcpException.Config.class, () -> client.removeFromCart("", "li-1"));
        assertThrows(UcpException.Config.class, () -> client.removeFromCart("cart-1", ""));
    }

    @Test
    @DisplayName("7. create_checkout_session W3 stub → NotImplemented friendly")
    void create_checkout_session_w3_stub() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_INTERNAL,
                "W5 Stripe 集成中,当前 stub (D88.1)"));
        UcpException ex = assertThrows(UcpException.class,
                () -> client.createCheckoutSession("cart-1"));
        assertTrue(ex instanceof UcpException.NotImplemented,
                "expected NotImplemented, got: " + ex.getClass().getSimpleName());
        assertTrue(ex.getMessage().contains("W5 Stripe"), "msg: " + ex.getMessage());
    }

    @Test
    @DisplayName("8. confirm_payment W3 stub → NotImplemented friendly")
    void confirm_payment_w3_stub() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_INTERNAL,
                "W5 Stripe 集成中,当前 stub (D88.1)"));
        UcpException ex = assertThrows(UcpException.class,
                () -> client.confirmPayment("cs_xyz"));
        assertTrue(ex instanceof UcpException.NotImplemented);
    }

    @Test
    @DisplayName("9. get_order — orderId 必填")
    void get_order() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "order_id", "ord-9", "user_id", "u-1", "tenant_id", "tenant-A",
                "status", "paid", "total_cents", 9950, "currency", "CNY"
        )));
        Dto.Order order = client.getOrder("ord-9");
        assertEquals("tenant-A", order.getTenantId());
        assertEquals("paid", order.getStatus());
    }

    @Test
    @DisplayName("10. list_orders — userId + 可选 filter(status/page_size)")
    void list_orders() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "orders", List.of(Map.of("order_id", "ord-1", "status", "paid")),
                "total", 1, "page", 1, "page_size", 20
        )));
        Dto.ListOrdersFilter f = new Dto.ListOrdersFilter();
        f.setStatus("paid");
        Dto.ListOrdersResult res = client.listOrders("u-1", f);
        assertEquals(1, res.getTotal());
    }

    @Test
    @DisplayName("11. cancel_order — 含 refund_id + refund_status")
    void cancel_order() {
        sender.queueJson(200, envelopeOk(1, Map.of(
                "order_id", "ord-1", "status", "canceled",
                "refund_id", "re_xyz", "refund_status", "pending",
                "canceled_at", "2026-07-11T10:00:00Z"
        )));
        Dto.CancelOrderResult res = client.cancelOrder("ord-1");
        assertEquals("re_xyz", res.getRefundId());
        assertEquals("pending", res.getRefundStatus());
    }

    // ====================================================================
    // Error path
    // ====================================================================

    @Test
    @DisplayName("product not found error envelope (-32101) → UcpException.RpcError")
    void product_not_found_rpc_envelope() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_UCP_PRODUCT_NOT_FOUND,
                "no product p-missing"));
        UcpException ex = assertThrows(UcpException.class,
                () -> client.getProduct("p-missing"));
        assertTrue(ex instanceof UcpException.RpcError);
        assertTrue(UcpException.isNotFound(ex), "should be NotFound");
        UcpException.RpcError rpc = (UcpException.RpcError) ex;
        assertEquals(UcpException.ERR_CODE_UCP_PRODUCT_NOT_FOUND, rpc.code());
    }

    @Test
    @DisplayName("order not found error envelope (-32102) → UcpException.RpcError + isNotFound")
    void order_not_found_rpc_envelope() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_UCP_ORDER_NOT_FOUND,
                "no order"));
        UcpException ex = assertThrows(UcpException.class,
                () -> client.getOrder("ord-x"));
        assertTrue(ex instanceof UcpException.RpcError);
        assertTrue(UcpException.isNotFound(ex));
    }

    @Test
    @DisplayName("Stripe error envelope (-32105) → isStripeError + 抛 RpcError(Non-W3 wrap)")
    void stripe_error_rpc_envelope() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_UCP_STRIPE_ERROR,
                "card declined"));
        UcpException ex = assertThrows(UcpException.class,
                () -> client.cancelOrder("ord-1"));
        assertTrue(ex instanceof UcpException.RpcError);
        assertTrue(UcpException.isStripeError(ex));
    }

    @Test
    @DisplayName("HTTP 500 → HttpTransport.validateStatus → WauException → UcpException.Malformed")
    void http_5xx_to_malformed() {
        sender.queueJson(500, "{\"error\":\"upstream timeout\"}");
        UcpException ex = assertThrows(UcpException.class, () -> client.getProduct("p-1"));
        assertTrue(ex instanceof UcpException.Malformed, "expected Malformed, got: " + ex.getClass());
        assertTrue(ex.getMessage().contains("500"), "msg: " + ex.getMessage());
    }

    @Test
    @DisplayName("HTTP 404 → Malformed(getProduct 必发,只是这里测 error envelope 不覆盖)")
    void http_404_to_malformed() {
        sender.queueJson(404, "{\"error\":\"not found\"}");
        assertThrows(UcpException.class, () -> client.getProduct("p-1"));
    }

    @Test
    @DisplayName("method_not_found (-32601) → 抛 RpcError 不 wrap W3")
    void method_not_found_rpc_envelope() {
        sender.queueJson(200, envelopeError(1, UcpException.ERR_CODE_METHOD_NOT_FOUND,
                "no such tool"));
        UcpException ex = assertThrows(UcpException.class, () -> client.getProduct("p-x"));
        assertTrue(ex instanceof UcpException.RpcError);
        UcpException.RpcError rpc = (UcpException.RpcError) ex;
        assertEquals(UcpException.ERR_CODE_METHOD_NOT_FOUND, rpc.code());
    }

    // ====================================================================
    // Auth + Stripe helpers
    // ====================================================================

    @Test
    @DisplayName("UcpAuth.apply 注入 Authorization + X-WAU-Tenant-ID")
    void ucp_auth_apply() {
        UcpAuth auth = new UcpAuth("jwt-tok", "tenant-X");
        Map<String, String> headers = new HashMap<>();
        auth.apply(headers);
        assertEquals("Bearer jwt-tok", headers.get(UcpAuth.AUTH_HEADER_NAME));
        assertEquals("tenant-X", headers.get(UcpAuth.DEFAULT_TENANT_HEADER_NAME));
        assertEquals(2, headers.size());
    }

    @Test
    @DisplayName("UcpAuth 空 token/tenant → apply 0 header")
    void ucp_auth_empty() {
        UcpAuth auth = UcpAuth.empty();
        Map<String, String> headers = new HashMap<>();
        auth.apply(headers);
        assertEquals(0, headers.size());
    }

    @Test
    @DisplayName("setBearerToken / setTenantId 静态 helper")
    void auth_static_helpers() {
        Map<String, String> h1 = new HashMap<>();
        UcpAuth.setBearerToken(h1, "tok-1");
        assertEquals("Bearer tok-1", h1.get(UcpAuth.AUTH_HEADER_NAME));

        Map<String, String> h2 = new HashMap<>();
        UcpAuth.setTenantId(h2, "tenant-Z");
        assertEquals("tenant-Z", h2.get(UcpAuth.DEFAULT_TENANT_HEADER_NAME));

        Map<String, String> h3 = new HashMap<>();
        UcpAuth.setBearerToken(h3, "");
        assertEquals(0, h3.size());

        Map<String, String> h4 = new HashMap<>();
        UcpAuth.setTenantId(h4, "");
        assertEquals(0, h4.size());
    }

    @Test
    @DisplayName("Stripe.isStripePath — create_checkout_session + confirm_payment + cancel_order")
    void is_stripe_path() {
        assertTrue(Stripe.isStripePath(Tools.TOOL_CREATE_CHECKOUT_SESSION));
        assertTrue(Stripe.isStripePath(Tools.TOOL_CONFIRM_PAYMENT));
        assertTrue(Stripe.isStripePath(Tools.TOOL_CANCEL_ORDER));
        assertFalse(Stripe.isStripePath(Tools.TOOL_LIST_PRODUCTS));
        assertFalse(Stripe.isStripePath(Tools.TOOL_ADD_TO_CART));
        assertFalse(Stripe.isStripePath(null));
    }

    @Test
    @DisplayName("ALL_TOOL_NAMES 长度 11")
    void all_tool_names_count() {
        assertEquals(11, Tools.ALL_TOOL_NAMES.size());
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private static Map<String, Object> envelopeOk(long id, Object result) {
        Map<String, Object> env = new HashMap<>();
        env.put("jsonrpc", "2.0");
        env.put("id", id);
        env.put("result", result);
        return env;
    }

    private static Map<String, Object> envelopeError(long id, int code, String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> env = new HashMap<>();
        env.put("jsonrpc", "2.0");
        env.put("id", id);
        env.put("error", err);
        return env;
    }

    private static Map<String, Object> product(String id, String name, int priceCents) {
        Map<String, Object> p = new HashMap<>();
        p.put("product_id", id);
        p.put("name", name);
        p.put("price_cents", priceCents);
        p.put("currency", "CNY");
        return p;
    }

    /**
     * Sender mock — pattern 镜像 {@code BotsClientTest.CapturingSender}(per W2.1) —
     * 捕获 lastRequest/lastBody + queued JSON responses。
     */
    static class CapturingSender implements HttpTransport.Sender {
        private final List<Object> objQueue = new ArrayList<>();
        private final List<Integer> codeQueue = new ArrayList<>();
        private final AtomicReference<Request> lastReq = new AtomicReference<>();
        private final AtomicReference<String> lastBodyStr = new AtomicReference<>();

        void queueJson(int code, Object obj) {
            codeQueue.add(code);
            objQueue.add(obj);
        }

        Request lastRequest() { return lastReq.get(); }
        String lastBody() { return lastBodyStr.get(); }

        @Override
        public Response doRequest(Request req) throws IOException {
            lastReq.set(req);
            if (req.body() != null) {
                Buffer buf = new Buffer();
                req.body().writeTo(buf);
                lastBodyStr.set(buf.readUtf8());
            }
            int code = codeQueue.isEmpty() ? 200 : codeQueue.remove(0);
            Object obj = objQueue.isEmpty() ? "" : objQueue.remove(0);
            String json;
            try {
                json = obj instanceof String ? (String) obj : new ObjectMapper().writeValueAsString(obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return new Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(code == 200 ? "OK" : "Error")
                    .body(ResponseBody.create(json, JSON))
                    .build();
        }
    }
}
