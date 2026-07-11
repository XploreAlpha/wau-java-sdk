package io.wau.sdk.ucp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * UCP client 鉴权(D78/D79/D80 OAuth 2.0 identity_linking bearer + D65 multi-tenant)。
 *
 * <p>header channel:
 * <ul>
 *   <li>{@code Authorization: Bearer <jwt>} — per D78 OAuth identity_linking</li>
 *   <li>{@code X-WAU-Tenant-ID: <tenant_id>} — per D65 multi-tenant</li>
 * </ul>
 *
 * <p>5 SDK 一致(per D13 byte-equal):
 * <ul>
 *   <li>wau-go-sdk/ucpclient/auth.go</li>
 *   <li>wau-python-sdk/src/wau_sdk/ucp_auth.py</li>
 *   <li>wau-typescript-sdk/src/ucp/auth.ts</li>
 *   <li>wau-rust-sdk/src/ucp/auth.rs</li>
 *   <li>本文件(per D88.9)</li>
 * </ul>
 *
 * <p>W3 暂不走 OAuth refresh flow;W5+ 加 OAuth refresh helper(per design doc §八)。
 *
 * @since 0.2.0 (v1.3.3 UCP client deadline 提前, 2026-07-11)
 */
public final class UcpAuth {

    /** {@code Authorization} header name(per RFC 7235)。 */
    public static final String AUTH_HEADER_NAME = "Authorization";

    /** Bearer scheme prefix。 */
    public static final String AUTH_SCHEME_PREFIX = "Bearer ";

    /** 5 SDK 共享 tenant header name(per D65 multi-tenant)。 */
    public static final String DEFAULT_TENANT_HEADER_NAME = "X-WAU-Tenant-ID";

    private final String bearerToken;
    private final String tenantId;

    public UcpAuth(String bearerToken, String tenantId) {
        this.bearerToken = bearerToken == null ? "" : bearerToken;
        this.tenantId = tenantId == null ? "" : tenantId;
    }

    public static UcpAuth empty() {
        return new UcpAuth("", "");
    }

    public String bearerToken() { return bearerToken; }
    public String tenantId() { return tenantId; }

    /** 注入 {@code Authorization} + {@code X-WAU-Tenant-ID} 到 caller 提供的 header Map。 */
    public void apply(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers");
        if (!bearerToken.isEmpty()) {
            headers.put(AUTH_HEADER_NAME, AUTH_SCHEME_PREFIX + bearerToken);
        }
        if (!tenantId.isEmpty()) {
            headers.put(DEFAULT_TENANT_HEADER_NAME, tenantId);
        }
    }

    /** {@code Authorization: Bearer <token>} 单 helper(per D78/D79/D80) — 静态便利方法。 */
    public static void setBearerToken(Map<String, String> headers, String token) {
        Objects.requireNonNull(headers, "headers");
        if (token != null && !token.isEmpty()) {
            headers.put(AUTH_HEADER_NAME, AUTH_SCHEME_PREFIX + token);
        }
    }

    /** {@code X-WAU-Tenant-ID: <tenantId>} 单 helper(per D65)。 */
    public static void setTenantId(Map<String, String> headers, String tenantId) {
        Objects.requireNonNull(headers, "headers");
        if (tenantId != null && !tenantId.isEmpty()) {
            headers.put(DEFAULT_TENANT_HEADER_NAME, tenantId);
        }
    }

    /** 新建一份含 auth header 的 Map(原 headers 内容会复制)。 */
    public Map<String, String> newHeadersWithAuth(Map<String, String> base) {
        Map<String, String> h = new HashMap<>();
        if (base != null) h.putAll(base);
        apply(h);
        return h;
    }
}
