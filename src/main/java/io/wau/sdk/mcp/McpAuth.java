package io.wau.sdk.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP auth helpers (wau-java-sdk v1.3.2, per D87.9).
 *
 * <p>Bearer token 注入 helper (per D78/D79/D80 + W3-MCP-auth-SDK-design §三)。
 */
public final class McpAuth {

    public static final String AUTH_HEADER_NAME = "Authorization";
    public static final String AUTH_SCHEME_PREFIX = "Bearer ";
    public static final String DEFAULT_USER_AGENT = "wau-java-sdk/mcpclient/v1.3.2";

    private McpAuth() {}

    /** 注入 {@code Authorization: Bearer <token>} 到 headers。 */
    public static Map<String, String> applyBearerToken(Map<String, String> headers, String token) {
        Map<String, String> h = (headers == null) ? new HashMap<>() : new HashMap<>(headers);
        if (token != null && !token.isEmpty()) {
            h.put(AUTH_HEADER_NAME, AUTH_SCHEME_PREFIX + token);
        } else {
            h.remove(AUTH_HEADER_NAME);
        }
        return h;
    }

    /** 构造 MCP request headers (Content-Type + User-Agent + Authorization)。 */
    public static Map<String, String> buildHeaders(String bearerToken, String userAgent) {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        h.put("User-Agent", userAgent != null ? userAgent : DEFAULT_USER_AGENT);
        if (bearerToken != null && !bearerToken.isEmpty()) {
            h.put(AUTH_HEADER_NAME, AUTH_SCHEME_PREFIX + bearerToken);
        }
        return h;
    }
}