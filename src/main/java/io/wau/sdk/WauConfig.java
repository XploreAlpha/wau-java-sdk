package io.wau.sdk;

import java.time.Duration;
import java.util.Objects;

/**
 * wau-java-sdk 顶层配置(per D63 启动备忘 §4.1.1)。
 *
 * <p>5 核心方法共用同一份配置,builder pattern 灵活但不可变。
 *
 * <h2>必填字段</h2>
 * <ul>
 *   <li>{@link #wauEdgeUrl} — wau-edge base URL(默认 {@code http://localhost:18402})</li>
 *   <li>{@link #botId} — bot 公开 ID(per D78,格式 {@code bot:<tenant>:<botid>})</li>
 * </ul>
 *
 * <h2>可选字段</h2>
 * <ul>
 *   <li>{@link #botToken} — bot bearer token(per D79 鉴权)</li>
 *   <li>{@link #ownerUserId} — owner_user_id(per D80,业务主用户 ID)</li>
 *   <li>{@link #jwtVerifier} — JWT 校验器(D79 三向校验)</li>
 *   <li>{@link #timeoutMs} — HTTP timeout(默认 30s)</li>
 * </ul>
 *
 * <h2>不变量</h2>
 * <ul>
 *   <li>{@link #wauEdgeUrl} 必须以 {@code http://} 或 {@code https://} 开头</li>
 *   <li>{@link #botId} 非空字符串</li>
 *   <li>{@link #timeoutMs} > 0</li>
 * </ul>
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public final class WauConfig {

    private final String wauEdgeUrl;
    private final String botId;
    private final String botToken;
    private final String ownerUserId;
    private final Duration timeout;
    private final boolean debug;

    private WauConfig(Builder b) {
        this.wauEdgeUrl = Objects.requireNonNull(b.wauEdgeUrl, "wauEdgeUrl must not be null");
        this.botId = Objects.requireNonNull(b.botId, "botId must not be null");
        if (b.wauEdgeUrl.isBlank() || !(b.wauEdgeUrl.startsWith("http://") || b.wauEdgeUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("wauEdgeUrl must start with http:// or https://, got: " + b.wauEdgeUrl);
        }
        if (b.botId.isBlank()) {
            throw new IllegalArgumentException("botId must not be blank");
        }
        if (b.timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0, got: " + b.timeoutMs);
        }
        this.botToken = b.botToken;
        this.ownerUserId = b.ownerUserId;
        this.timeout = Duration.ofMillis(b.timeoutMs);
        this.debug = b.debug;
    }

    public String wauEdgeUrl() { return wauEdgeUrl; }
    public String botId() { return botId; }
    public String botToken() { return botToken; }
    public String ownerUserId() { return ownerUserId; }
    public Duration timeout() { return timeout; }
    public boolean debug() { return debug; }

    /**
     * Builder 模式(per Effective Java item 2 + D63 启动备忘 §4.1.1)。
     */
    public static final class Builder {
        private String wauEdgeUrl = "http://localhost:18402";
        private String botId;
        private String botToken;
        private String ownerUserId;
        private long timeoutMs = 30_000L;
        private boolean debug = false;

        public Builder wauEdgeUrl(String url) {
            this.wauEdgeUrl = url;
            return this;
        }

        public Builder botId(String botId) {
            this.botId = botId;
            return this;
        }

        public Builder botToken(String token) {
            this.botToken = token;
            return this;
        }

        public Builder ownerUserId(String ownerUserId) {
            this.ownerUserId = ownerUserId;
            return this;
        }

        public Builder timeoutMs(long ms) {
            this.timeoutMs = ms;
            return this;
        }

        public Builder timeout(Duration d) {
            this.timeoutMs = d.toMillis();
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public WauConfig build() {
            return new WauConfig(this);
        }
    }
}
