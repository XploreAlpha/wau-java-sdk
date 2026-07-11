package io.wau.sdk.ucp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * UCP commerce DTO records(8 records + 3 filter/result 包装,per [[process/2026-07-11-W3-UCP-client-SDK-design]] §三 §四)。
 *
 * <p>5 SDK 一致 snake_case 字段(per D13 byte-equal):
 * <pre>
 *   wau-go-sdk/ucpclient/types.go
 *   wau-python-sdk/src/wau_sdk/ucp_types.py
 *   wau-typescript-sdk/src/ucp/types.ts
 *   wau-rust-sdk/src/ucp/types.rs
 *   本文件(per D88.9)
 * </pre>
 *
 * <p>字段全部 nullable 对齐 kernel JSON(all fields optional,bytestream 兼容性)。
 * 所有 Optional 字段 mapping:Java 用 {@code String}/{@code Integer}/{@code Long}/{@code Double} 等 boxed type
 * + null = 缺省(OmitNullValues 由 client 端序列化时的 {@code @JsonInclude(NON_NULL)} 保证)。
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public final class Dto {

    private Dto() {}

    /** 商品信息(Product Catalog)。 */
    public static final class Product {
        @JsonProperty("product_id") private String productId;
        @JsonProperty("name") private String name;
        @JsonProperty("description") private String description;
        @JsonProperty("price_cents") private Integer priceCents;
        @JsonProperty("currency") private String currency;
        @JsonProperty("image_url") private String imageUrl;
        @JsonProperty("in_stock") private Boolean inStock;

        public Product() {}

        public String getProductId() { return productId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Integer getPriceCents() { return priceCents; }
        public String getCurrency() { return currency; }
        public String getImageUrl() { return imageUrl; }
        public Boolean getInStock() { return inStock; }

        public void setProductId(String v) { this.productId = v; }
        public void setName(String v) { this.name = v; }
        public void setDescription(String v) { this.description = v; }
        public void setPriceCents(Integer v) { this.priceCents = v; }
        public void setCurrency(String v) { this.currency = v; }
        public void setImageUrl(String v) { this.imageUrl = v; }
        public void setInStock(Boolean v) { this.inStock = v; }
    }

    /** List products 过滤条件(per design doc §三)。 */
    public static final class ListProductsFilter {
        @JsonProperty("category") private String category;
        @JsonProperty("min_price_cents") private Integer minPriceCents;
        @JsonProperty("max_price_cents") private Integer maxPriceCents;
        @JsonProperty("page") private Integer page;
        @JsonProperty("page_size") private Integer pageSize;
        @JsonProperty("sort") private String sort;

        public ListProductsFilter() {}

        public String getCategory() { return category; }
        public Integer getMinPriceCents() { return minPriceCents; }
        public Integer getMaxPriceCents() { return maxPriceCents; }
        public Integer getPage() { return page; }
        public Integer getPageSize() { return pageSize; }
        public String getSort() { return sort; }

        public void setCategory(String v) { this.category = v; }
        public void setMinPriceCents(Integer v) { this.minPriceCents = v; }
        public void setMaxPriceCents(Integer v) { this.maxPriceCents = v; }
        public void setPage(Integer v) { this.page = v; }
        public void setPageSize(Integer v) { this.pageSize = v; }
        public void setSort(String v) { this.sort = v; }
    }

    /** List products 响应。 */
    public static final class ListProductsResult {
        @JsonProperty("products") private List<Product> products;
        @JsonProperty("total") private Integer total;
        @JsonProperty("page") private Integer page;
        @JsonProperty("page_size") private Integer pageSize;

        public ListProductsResult() {}

        public List<Product> getProducts() { return products; }
        public Integer getTotal() { return total; }
        public Integer getPage() { return page; }
        public Integer getPageSize() { return pageSize; }

        public void setProducts(List<Product> v) { this.products = v; }
        public void setTotal(Integer v) { this.total = v; }
        public void setPage(Integer v) { this.page = v; }
        public void setPageSize(Integer v) { this.pageSize = v; }
    }

    /** 语义搜索结果(全文 / 关键词)。 */
    public static final class SearchProductsResult {
        @JsonProperty("products") private List<Product> products;
        @JsonProperty("total") private Integer total;
        @JsonProperty("query") private String query;

        public SearchProductsResult() {}

        public List<Product> getProducts() { return products; }
        public Integer getTotal() { return total; }
        public String getQuery() { return query; }

        public void setProducts(List<Product> v) { this.products = v; }
        public void setTotal(Integer v) { this.total = v; }
        public void setQuery(String v) { this.query = v; }
    }

    /** Cart 单条 line item。 */
    public static final class CartLineItem {
        @JsonProperty("line_item_id") private String lineItemId;
        @JsonProperty("product_id") private String productId;
        @JsonProperty("name") private String name;
        @JsonProperty("quantity") private Integer quantity;
        @JsonProperty("price_cents") private Integer priceCents;

        public CartLineItem() {}

        public String getLineItemId() { return lineItemId; }
        public String getProductId() { return productId; }
        public String getName() { return name; }
        public Integer getQuantity() { return quantity; }
        public Integer getPriceCents() { return priceCents; }

        public void setLineItemId(String v) { this.lineItemId = v; }
        public void setProductId(String v) { this.productId = v; }
        public void setName(String v) { this.name = v; }
        public void setQuantity(Integer v) { this.quantity = v; }
        public void setPriceCents(Integer v) { this.priceCents = v; }
    }

    /** 购物车。 */
    public static final class Cart {
        @JsonProperty("cart_id") private String cartId;
        @JsonProperty("user_id") private String userId;
        @JsonProperty("tenant_id") private String tenantId;  // per D65 multi-tenant
        @JsonProperty("line_items") private List<CartLineItem> lineItems;
        @JsonProperty("total_cents") private Integer totalCents;
        @JsonProperty("currency") private String currency;
        @JsonProperty("expires_at") private String expiresAt;  // ISO-8601 string

        public Cart() {}

        public String getCartId() { return cartId; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public List<CartLineItem> getLineItems() { return lineItems; }
        public Integer getTotalCents() { return totalCents; }
        public String getCurrency() { return currency; }
        public String getExpiresAt() { return expiresAt; }

        public void setCartId(String v) { this.cartId = v; }
        public void setUserId(String v) { this.userId = v; }
        public void setTenantId(String v) { this.tenantId = v; }
        public void setLineItems(List<CartLineItem> v) { this.lineItems = v; }
        public void setTotalCents(Integer v) { this.totalCents = v; }
        public void setCurrency(String v) { this.currency = v; }
        public void setExpiresAt(String v) { this.expiresAt = v; }
    }

    /** Stripe Checkout Session(per D88.8 Stripe 透明,W5+ kernel ucp_stripe.go)。 */
    public static final class CheckoutSession {
        @JsonProperty("session_id") private String sessionId;
        @JsonProperty("url") private String url;
        @JsonProperty("cart_id") private String cartId;
        @JsonProperty("amount_cents") private Integer amountCents;
        @JsonProperty("currency") private String currency;
        @JsonProperty("status") private String status;
        @JsonProperty("expires_at") private String expiresAt;

        public CheckoutSession() {}

        public String getSessionId() { return sessionId; }
        public String getUrl() { return url; }
        public String getCartId() { return cartId; }
        public Integer getAmountCents() { return amountCents; }
        public String getCurrency() { return currency; }
        public String getStatus() { return status; }
        public String getExpiresAt() { return expiresAt; }

        public void setSessionId(String v) { this.sessionId = v; }
        public void setUrl(String v) { this.url = v; }
        public void setCartId(String v) { this.cartId = v; }
        public void setAmountCents(Integer v) { this.amountCents = v; }
        public void setCurrency(String v) { this.currency = v; }
        public void setStatus(String v) { this.status = v; }
        public void setExpiresAt(String v) { this.expiresAt = v; }
    }

    /** Payment confirmation result(per Stripe transparent pattern)。 */
    public static final class PaymentConfirmation {
        @JsonProperty("order_id") private String orderId;
        @JsonProperty("status") private String status;
        @JsonProperty("amount_cents") private Integer amountCents;
        @JsonProperty("currency") private String currency;
        @JsonProperty("payment_method") private String paymentMethod;
        @JsonProperty("receipt_url") private String receiptUrl;

        public PaymentConfirmation() {}

        public String getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public Integer getAmountCents() { return amountCents; }
        public String getCurrency() { return currency; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getReceiptUrl() { return receiptUrl; }

        public void setOrderId(String v) { this.orderId = v; }
        public void setStatus(String v) { this.status = v; }
        public void setAmountCents(Integer v) { this.amountCents = v; }
        public void setCurrency(String v) { this.currency = v; }
        public void setPaymentMethod(String v) { this.paymentMethod = v; }
        public void setReceiptUrl(String v) { this.receiptUrl = v; }
    }

    /** 订单(per D65 multi-tenant 含 tenantId)。 */
    public static final class Order {
        @JsonProperty("order_id") private String orderId;
        @JsonProperty("user_id") private String userId;
        @JsonProperty("tenant_id") private String tenantId;  // per D65 multi-tenant
        @JsonProperty("status") private String status;       // pending|paid|canceled|refunded
        @JsonProperty("total_cents") private Integer totalCents;
        @JsonProperty("currency") private String currency;
        @JsonProperty("line_items") private List<CartLineItem> lineItems;
        @JsonProperty("created_at") private String createdAt;
        @JsonProperty("updated_at") private String updatedAt;

        public Order() {}

        public String getOrderId() { return orderId; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getStatus() { return status; }
        public Integer getTotalCents() { return totalCents; }
        public String getCurrency() { return currency; }
        public List<CartLineItem> getLineItems() { return lineItems; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }

        public void setOrderId(String v) { this.orderId = v; }
        public void setUserId(String v) { this.userId = v; }
        public void setTenantId(String v) { this.tenantId = v; }
        public void setStatus(String v) { this.status = v; }
        public void setTotalCents(Integer v) { this.totalCents = v; }
        public void setCurrency(String v) { this.currency = v; }
        public void setLineItems(List<CartLineItem> v) { this.lineItems = v; }
        public void setCreatedAt(String v) { this.createdAt = v; }
        public void setUpdatedAt(String v) { this.updatedAt = v; }
    }

    /** List orders 过滤条件。 */
    public static final class ListOrdersFilter {
        @JsonProperty("status") private String status;
        @JsonProperty("min_total_cents") private Integer minTotalCents;
        @JsonProperty("page") private Integer page;
        @JsonProperty("page_size") private Integer pageSize;

        public ListOrdersFilter() {}

        public String getStatus() { return status; }
        public Integer getMinTotalCents() { return minTotalCents; }
        public Integer getPage() { return page; }
        public Integer getPageSize() { return pageSize; }

        public void setStatus(String v) { this.status = v; }
        public void setMinTotalCents(Integer v) { this.minTotalCents = v; }
        public void setPage(Integer v) { this.page = v; }
        public void setPageSize(Integer v) { this.pageSize = v; }
    }

    /** List orders 响应。 */
    public static final class ListOrdersResult {
        @JsonProperty("orders") private List<Order> orders;
        @JsonProperty("total") private Integer total;
        @JsonProperty("page") private Integer page;
        @JsonProperty("page_size") private Integer pageSize;

        public ListOrdersResult() {}

        public List<Order> getOrders() { return orders; }
        public Integer getTotal() { return total; }
        public Integer getPage() { return page; }
        public Integer getPageSize() { return pageSize; }

        public void setOrders(List<Order> v) { this.orders = v; }
        public void setTotal(Integer v) { this.total = v; }
        public void setPage(Integer v) { this.page = v; }
        public void setPageSize(Integer v) { this.pageSize = v; }
    }

    /** Cancel order 响应(含 refund 状态,per design doc §四 Stripe refund 透明)。 */
    public static final class CancelOrderResult {
        @JsonProperty("order_id") private String orderId;
        @JsonProperty("status") private String status;
        @JsonProperty("refund_id") private String refundId;
        @JsonProperty("refund_status") private String refundStatus;
        @JsonProperty("canceled_at") private String canceledAt;

        public CancelOrderResult() {}

        public String getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public String getRefundId() { return refundId; }
        public String getRefundStatus() { return refundStatus; }
        public String getCanceledAt() { return canceledAt; }

        public void setOrderId(String v) { this.orderId = v; }
        public void setStatus(String v) { this.status = v; }
        public void setRefundId(String v) { this.refundId = v; }
        public void setRefundStatus(String v) { this.refundStatus = v; }
        public void setCanceledAt(String v) { this.canceledAt = v; }
    }
}
