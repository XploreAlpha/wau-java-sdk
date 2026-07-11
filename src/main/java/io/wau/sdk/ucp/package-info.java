/**
 * UCP client module(per D88.9,2026-07-11 W2 prep buffer Day 4 v1.3.3 closure)。
 *
 * <h2>设计</h2>
 * <ul>
 *   <li>11 commerce tool wrapper(per [[process/2026-07-11-W3-UCP-client-SDK-design]] §三)</li>
 *   <li>JSON-RPC 2.0 envelope over HTTP(POST {baseURL}/ucp)</li>
 *   <li>8 commerce DTO records + 3 filter/result 包装(per design doc §四)</li>
 *   <li>5 SDK 一致(D13 byte-equal):Go + Python + TypeScript + Rust + Java</li>
 *   <li>Stripe 透明(per D88.8 W3 stub):SDK 0 直接 Stripe SDK dep,W3 期间
 *       create_checkout_session + confirm_payment 走 kernel ErrNotImplemented → SDK 抛
 *       NotImplemented friendly path;W5+ kernel ucp_stripe.go 落地</li>
 * </ul>
 *
 * <h2>D60 additive</h2>
 * <p>本包全独立,不耦合老 {@code io.wau.sdk.WauClient} 5 核心方法。
 * 通过 {@code WauClient.ucp()} lazy getter 暴露(L5 模块同 pattern)。
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
package io.wau.sdk.ucp;
