package io.wau.sdk.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.wau.sdk.exception.AuthException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * JWT 校验器(per D79 三向校验 — [[2026-07-05-D79-multi-bot-auth]])。
 *
 * <p>D79 三向校验:
 * <ol>
 *   <li>{@code user_id} 必须存在</li>
 *   <li>{@code bot_id} 必须存在(legacy SDK 允许 null,但 v1.3.0+ 严格)</li>
 *   <li>{@code owner_user_id} 必须存在</li>
 * </ol>
 *
 * <h2>5 SDK 一致性</h2>
 * <p>本类对齐 wau-go-sdk / wau-python-sdk / wau-typescript-sdk / wau-rust-sdk
 * 同名类行为,per D63 §5.1 同步策略。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public final class JwtVerifier {

    /** HMAC-SHA secret(必传,空 token 必 throw). */
    private final SecretKey secretKey;

    /** 当 {@code true},{@code bot_id} 可空(legacy SDK 兼容). */
    private final boolean allowLegacy;

    public JwtVerifier(String secret) {
        this(secret, false);
    }

    /**
     * @param secret HMAC-SHA secret 字符串(non-empty,non-blank)
     * @param allowLegacy {@code true}= 允许 bot_id 缺失(legacy SDK 兼容)
     */
    public JwtVerifier(String secret, boolean allowLegacy) {
        Objects.requireNonNull(secret, "secret must not be null");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("HMAC-SHA256 requires at least 32-byte secret, got " + keyBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.allowLegacy = allowLegacy;
    }

    /**
     * 校验 token(D79 三向).
     *
     * @param token JWT bearer 字符串
     * @return 解析后的 claims
     * @throws AuthException 任一校验失败(401)
     */
    public Claims verify(String token) {
        Objects.requireNonNull(token, "token must not be null");
        if (token.isBlank()) {
            throw new AuthException("token is blank");
        }
        Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
        } catch (Exception e) {
            throw new AuthException("invalid token signature or format: " + e.getMessage(), e);
        }
        Claims claims = jws.getPayload();
        validateD79(claims);
        return claims;
    }

    /**
     * D79 三向校验 — user_id / bot_id / owner_user_id.
     *
     * <p>legacy SDK(per M10 W7 之前的版本)允许 bot_id 缺失;
     * v1.3.0+(per D78)严格三向。
     */
    private void validateD79(Claims claims) {
        Object userId = claims.get("user_id");
        Object botId = claims.get("bot_id");
        Object ownerUserId = claims.get("owner_user_id");
        if (userId == null || userId.toString().isBlank()) {
            throw new AuthException("missing claim: user_id");
        }
        if (!allowLegacy && (botId == null || botId.toString().isBlank())) {
            throw new AuthException("missing claim: bot_id (legacy SDK allowed if allowLegacy=true)");
        }
        if (ownerUserId == null || ownerUserId.toString().isBlank()) {
            throw new AuthException("missing claim: owner_user_id");
        }
    }

    /**
     * Map-friendly extract(给没 JJWT dep 的 caller 用).
     */
    public Map<String, Object> verifyAsMap(String token) {
        Claims c = verify(token);
        return c;
    }
}
