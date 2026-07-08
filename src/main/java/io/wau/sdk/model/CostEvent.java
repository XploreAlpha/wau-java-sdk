package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * CostEvent — 成本事件 DTO(per M3 §3.1 cost feeder)。
 *
 * <p>对应 wau-store `POST /v1/llm/cost`(per D65)。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class CostEvent {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("tokens_input")
    private long tokensInput;

    @JsonProperty("tokens_output")
    private long tokensOutput;

    @JsonProperty("cost_usd")
    private double costUsd;

    @JsonProperty("latency_ms")
    private long latencyMs;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public CostEvent() {}

    public static Builder builder() { return new Builder(); }

    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getBotId() { return botId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getTokensInput() { return tokensInput; }
    public long getTokensOutput() { return tokensOutput; }
    public double getCostUsd() { return costUsd; }
    public long getLatencyMs() { return latencyMs; }
    public Instant getTimestamp() { return timestamp; }

    public void setUserId(String v) { this.userId = v; }
    public void setTenantId(String v) { this.tenantId = v; }
    public void setBotId(String v) { this.botId = v; }
    public void setProvider(String v) { this.provider = v; }
    public void setModel(String v) { this.model = v; }
    public void setTokensInput(long v) { this.tokensInput = v; }
    public void setTokensOutput(long v) { this.tokensOutput = v; }
    public void setCostUsd(double v) { this.costUsd = v; }
    public void setLatencyMs(long v) { this.latencyMs = v; }
    public void setTimestamp(Instant v) { this.timestamp = v; }

    public static final class Builder {
        private String userId;
        private String tenantId;
        private String botId;
        private String provider;
        private String model;
        private long tokensInput;
        private long tokensOutput;
        private double costUsd;
        private long latencyMs;
        private Instant timestamp;

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder botId(String v) { this.botId = v; return this; }
        public Builder provider(String v) { this.provider = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder tokensInput(long v) { this.tokensInput = v; return this; }
        public Builder tokensOutput(long v) { this.tokensOutput = v; return this; }
        public Builder costUsd(double v) { this.costUsd = v; return this; }
        public Builder latencyMs(long v) { this.latencyMs = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }

        public CostEvent build() {
            CostEvent e = new CostEvent();
            e.userId = this.userId;
            e.tenantId = this.tenantId;
            e.botId = this.botId;
            e.provider = this.provider;
            e.model = this.model;
            e.tokensInput = this.tokensInput;
            e.tokensOutput = this.tokensOutput;
            e.costUsd = this.costUsd;
            e.latencyMs = this.latencyMs;
            e.timestamp = this.timestamp != null ? this.timestamp : Instant.now();
            return e;
        }
    }
}
