package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OutgoingMessage — wau-channel 上游出站消息。
 *
 * <p>per D13 协议,字段 snake_case + D78 bot_id + D80 owner_user_id 9 字段对齐。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class OutgoingMessage {

    @JsonProperty("response")
    private String response;

    @JsonProperty("context_id")
    private String contextId;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("tokens_used")
    private int tokensUsed;

    @JsonProperty("elapsed_ms")
    private int elapsedMs;

    @JsonProperty("bot_id")
    private String botId;

    public OutgoingMessage() {}

    public OutgoingMessage(String response, String contextId, String provider,
                           int tokensUsed, int elapsedMs, String botId) {
        this.response = response;
        this.contextId = contextId;
        this.provider = provider;
        this.tokensUsed = tokensUsed;
        this.elapsedMs = elapsedMs;
        this.botId = botId;
    }

    public String getResponse() { return response; }
    public String getContextId() { return contextId; }
    public String getProvider() { return provider; }
    public int getTokensUsed() { return tokensUsed; }
    public int getElapsedMs() { return elapsedMs; }
    public String getBotId() { return botId; }

    public void setResponse(String v) { this.response = v; }
    public void setContextId(String v) { this.contextId = v; }
    public void setProvider(String v) { this.provider = v; }
    public void setTokensUsed(int v) { this.tokensUsed = v; }
    public void setElapsedMs(int v) { this.elapsedMs = v; }
    public void setBotId(String v) { this.botId = v; }
}
