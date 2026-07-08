package io.wau.sdk;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.wau.sdk.auth.JwtVerifier;
import io.wau.sdk.exception.AuthException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * JwtVerifier D79 三向校验单元测试(per D63 启动备忘 §4.1.4)。
 *
 * <p>4 测试:正常 / 缺 user_id / 缺 bot_id(legacy 允许)/ 缺 owner_user_id.
 */
class JwtVerifierTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 byte
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private String mintToken(String userId, String botId, Long ownerUserId, long ttlSec) {
        var b = Jwts.builder().subject(userId != null ? userId : "x");
        if (userId != null) b.claim("user_id", userId);
        if (botId != null) b.claim("bot_id", botId);
        if (ownerUserId != null) b.claim("owner_user_id", ownerUserId);
        return b.issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlSec * 1000))
                .signWith(key)
                .compact();
    }

    @Test
    void verify_succeeds_withAll3Claims() {
        JwtVerifier v = new JwtVerifier(SECRET);
        String tok = mintToken("alice", "bot:default:testjava", 123L, 600);
        var claims = v.verify(tok);
        assertThat(claims.get("user_id")).isEqualTo("alice");
        assertThat(claims.get("bot_id")).isEqualTo("bot:default:testjava");
        assertThat(claims.get("owner_user_id")).isEqualTo(123);
    }

    @Test
    void verify_missingUserId_throws() {
        JwtVerifier v = new JwtVerifier(SECRET);
        String tok = mintToken(null, "bot:d:b", 123L, 600);
        assertThatThrownBy(() -> v.verify(tok))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void verify_missingBotId_legacyAllowed() {
        // legacy SDK 兼容模式(allowLegacy=true)允许 bot_id 缺失
        JwtVerifier v = new JwtVerifier(SECRET, /*allowLegacy*/ true);
        String tok = mintToken("alice", null, 123L, 600);
        var claims = v.verify(tok);
        assertThat(claims.get("user_id")).isEqualTo("alice");
    }

    @Test
    void verify_missingBotId_strictRejects() {
        // 严格模式(v1.3.0+) 默认不允许
        JwtVerifier v = new JwtVerifier(SECRET);
        String tok = mintToken("alice", null, 123L, 600);
        assertThatThrownBy(() -> v.verify(tok))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("bot_id");
    }

    @Test
    void verify_missingOwnerUserId_throws() {
        JwtVerifier v = new JwtVerifier(SECRET);
        String tok = mintToken("alice", "bot:d:b", null, 600);
        assertThatThrownBy(() -> v.verify(tok))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("owner_user_id");
    }

    @Test
    void verify_badSignature_throws() {
        JwtVerifier v = new JwtVerifier(SECRET);
        // 用不同 secret 签
        SecretKey other = Keys.hmacShaKeyFor(("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz").getBytes(StandardCharsets.UTF_8));
        String tok = Jwts.builder().subject("x")
                .claim("user_id", "alice")
                .claim("bot_id", "bot:d:b")
                .claim("owner_user_id", 1)
                .signWith(other)
                .compact();
        assertThatThrownBy(() -> v.verify(tok))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void verify_blankToken_throws() {
        JwtVerifier v = new JwtVerifier(SECRET);
        assertThatThrownBy(() -> v.verify(""))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void constructor_blankSecret_throws() {
        assertThatThrownBy(() -> new JwtVerifier(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shortSecret_throws() {
        assertThatThrownBy(() -> new JwtVerifier("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32-byte");
    }
}
