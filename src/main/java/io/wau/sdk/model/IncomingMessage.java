package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * IncomingMessage — wau-channel 下游入站消息 DTO。
 *
 * <p>字段对齐(per D13 + D78 + D80):
 * <ul>
 *   <li>D13 7 字段 + D78/D80 v1.3.0 +2 字段 = 9 字段,与 4 SDK 100% 一致</li>
 *   <li>JSON 序列化全部 snake_case(per D13 §3.1 协议标准)</li>
 *   <li>可空字段用 {@code @JsonInclude(NON_EMPTY)} — omitempty 语义</li>
 * </ul>
 *
 * <h2>字段表</h2>
 * <table>
 *   <tr><th>JSON</th><th>Java</th><th>类型</th><th>必填</th><th>来源</th></tr>
 *   <tr><td>{@code platform_msg_id}</td><td>{@link #platformMsgId}</td><td>String</td><td>yes</td><td>D13</td></tr>
 *   <tr><td>{@code channel_id}</td><td>{@link #channelId}</td><td>String</td><td>yes</td><td>D13</td></tr>
 *   <tr><td>{@code user_id}</td><td>{@link #userId}</td><td>String</td><td>yes</td><td>D13</td></tr>
 *   <tr><td>{@code username}</td><td>{@link #username}</td><td>String</td><td>no</td><td>D13 (omitempty)</td></tr>
 *   <tr><td>{@code text}</td><td>{@link #text}</td><td>String</td><td>yes</td><td>D13</td></tr>
 *   <tr><td>{@code reply_to}</td><td>{@link #replyTo}</td><td>String</td><td>no</td><td>D13 (omitempty)</td></tr>
 *   <tr><td>{@code timestamp}</td><td>{@link #timestamp}</td><td>Instant</td><td>yes</td><td>D13</td></tr>
 *   <tr><td>{@code bot_id}</td><td>{@link #botId}</td><td>String</td><td>no</td><td><b>D78</b> (v1.3.0 snake_case)</td></tr>
 *   <tr><td>{@code owner_user_id}</td><td>{@link #ownerUserId}</td><td>Long</td><td>no</td><td><b>D80</b> (v1.3.0)</td></tr>
 * </table>
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class IncomingMessage {

    @JsonProperty("platform_msg_id")
    private String platformMsgId;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("text")
    private String text;

    @JsonProperty("reply_to")
    private String replyTo;

    @JsonProperty("timestamp")
    private Instant timestamp;

    // === v1.3.0 新增字段(per D78 + D80) ===
    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("owner_user_id")
    private Long ownerUserId;

    public IncomingMessage() {}

    private IncomingMessage(Builder b) {
        this.platformMsgId = Objects.requireNonNull(b.platformMsgId, "platformMsgId required");
        this.channelId = Objects.requireNonNull(b.channelId, "channelId required");
        this.userId = Objects.requireNonNull(b.userId, "userId required");
        this.username = b.username;
        this.text = Objects.requireNonNull(b.text, "text required");
        this.replyTo = b.replyTo;
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now();
        this.botId = b.botId;
        this.ownerUserId = b.ownerUserId;
    }

    // === Getters ===
    public String getPlatformMsgId() { return platformMsgId; }
    public String getChannelId() { return channelId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getText() { return text; }
    public String getReplyTo() { return replyTo; }
    public Instant getTimestamp() { return timestamp; }
    public String getBotId() { return botId; }
    public Long getOwnerUserId() { return ownerUserId; }

    // === Setters (Jackson 反序列化需要) ===
    public void setPlatformMsgId(String v) { this.platformMsgId = v; }
    public void setChannelId(String v) { this.channelId = v; }
    public void setUserId(String v) { this.userId = v; }
    public void setUsername(String v) { this.username = v; }
    public void setText(String v) { this.text = v; }
    public void setReplyTo(String v) { this.replyTo = v; }
    public void setTimestamp(Instant v) { this.timestamp = v; }
    public void setBotId(String v) { this.botId = v; }
    public void setOwnerUserId(Long v) { this.ownerUserId = v; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String platformMsgId;
        private String channelId;
        private String userId;
        private String username;
        private String text;
        private String replyTo;
        private Instant timestamp;
        private String botId;
        private Long ownerUserId;

        public Builder platformMsgId(String v) { this.platformMsgId = v; return this; }
        public Builder channelId(String v) { this.channelId = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder username(String v) { this.username = v; return this; }
        public Builder text(String v) { this.text = v; return this; }
        public Builder replyTo(String v) { this.replyTo = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public Builder botId(String v) { this.botId = v; return this; }
        public Builder ownerUserId(Long v) { this.ownerUserId = v; return this; }

        public IncomingMessage build() { return new IncomingMessage(this); }
    }
}
