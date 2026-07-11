package io.wau.sdk.ucp;

/**
 * UCP commerce tool 名称常量(11 commerce tools,per [[process/2026-07-11-W3-UCP-client-SDK-design]] §三)。
 *
 * <p>5 SDK 一致 snake_case 字符串字面量(per D13 byte-equal):
 * <ul>
 *   <li>Product tools (3):{@link #TOOL_LIST_PRODUCTS}, {@link #TOOL_GET_PRODUCT}, {@link #TOOL_SEARCH_PRODUCTS}</li>
 *   <li>Cart tools (3):{@link #TOOL_ADD_TO_CART}, {@link #TOOL_GET_CART}, {@link #TOOL_REMOVE_FROM_CART}</li>
 *   <li>Payment tools (3):{@link #TOOL_CREATE_CHECKOUT_SESSION}, {@link #TOOL_CONFIRM_PAYMENT}, {@link #TOOL_CANCEL_ORDER}</li>
 *   <li>Order tools (2):{@link #TOOL_GET_ORDER}, {@link #TOOL_LIST_ORDERS}</li>
 * </ul>
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public final class Tools {

    private Tools() {}

    public static final String TOOL_LIST_PRODUCTS = "list_products";
    public static final String TOOL_GET_PRODUCT = "get_product";
    public static final String TOOL_SEARCH_PRODUCTS = "search_products";
    public static final String TOOL_ADD_TO_CART = "add_to_cart";
    public static final String TOOL_GET_CART = "get_cart";
    public static final String TOOL_REMOVE_FROM_CART = "remove_from_cart";
    public static final String TOOL_CREATE_CHECKOUT_SESSION = "create_checkout_session";
    public static final String TOOL_CONFIRM_PAYMENT = "confirm_payment";
    public static final String TOOL_GET_ORDER = "get_order";
    public static final String TOOL_LIST_ORDERS = "list_orders";
    public static final String TOOL_CANCEL_ORDER = "cancel_order";

    /** 11 commerce tool 名,per design doc §八校验。 */
    public static final java.util.List<String> ALL_TOOL_NAMES = java.util.List.of(
            TOOL_LIST_PRODUCTS,
            TOOL_GET_PRODUCT,
            TOOL_SEARCH_PRODUCTS,
            TOOL_ADD_TO_CART,
            TOOL_GET_CART,
            TOOL_REMOVE_FROM_CART,
            TOOL_CREATE_CHECKOUT_SESSION,
            TOOL_CONFIRM_PAYMENT,
            TOOL_GET_ORDER,
            TOOL_LIST_ORDERS,
            TOOL_CANCEL_ORDER
    );
}
