package io.wau.sdk.ucp;

/**
 * UCP Stripe helper + status 常量(per D88.8, W3 实装;W5+ kernel {@code ucp_stripe.go} 落地)。
 *
 * <p>{@link #isStripePath(String)} 校验 tool 名是否需要走 Stripe 透明转发
 * (SDK 0 直接 Stripe SDK dep,所有交互走 kernel ucp_stripe.go,W3 stub 期间
 * create_checkout_session + confirm_payment + cancel_order kernel 返 INTERNAL
 * 错误→SDK 抛 NotImplementedException)。
 *
 * <p>5 SDK 一致(per D13 byte-equal):Go `wau-go-sdk/ucpclient/stripe.go` ·
 * Python `wau_python_sdk/ucp_stripe.py` · TypeScript `src/ucp/stripe.ts` ·
 * Rust `src/ucp/stripe.rs` · 本文件。
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public final class Stripe {

    private Stripe() {}

    /** Stripe 透明转发 tool 名单(per design doc §四)。 */
    public static final java.util.Set<String> STRIPE_TOOLS = java.util.Set.of(
            Tools.TOOL_CREATE_CHECKOUT_SESSION,
            Tools.TOOL_CONFIRM_PAYMENT,
            Tools.TOOL_CANCEL_ORDER
    );

    /** 判断 tool 是否走 Stripe 透明转发。 */
    public static boolean isStripePath(String toolName) {
        return toolName != null && STRIPE_TOOLS.contains(toolName);
    }

    // === Payment status enum (per design doc §四, 5 SDK 共享 const string)===

    /** Payment succeeded (Stripe Checkout session paid). */
    public static final String PAYMENT_STATUS_SUCCEEDED = "succeeded";

    /** Payment failed (card declined / 3DS auth fail). */
    public static final String PAYMENT_STATUS_FAILED = "failed";

    /** Payment processing (Stripe webhook in flight). */
    public static final String PAYMENT_STATUS_PROCESSING = "processing";

    /** Payment pending (未确认 / requires_action). */
    public static final String PAYMENT_STATUS_PENDING = "pending";
}
