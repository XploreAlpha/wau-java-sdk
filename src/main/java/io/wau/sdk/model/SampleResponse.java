package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SampleResponse — LLM 决策采样响应 DTO(per M3 W3)。
 *
 * <p>对应 wau-llm-router `POST /v1/llm/sample` 响应(per M3 子项 2 + D65 拍的 6 字段)。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class SampleResponse {

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("estimated_cost_usd")
    private double estimatedCostUsd;

    @JsonProperty("tokens_estimate")
    private int tokensEstimate;

    @JsonProperty("arm_id")
    private String armId;

    @JsonProperty("reason")
    private String reason;

    public SampleResponse() {}

    public SampleResponse(String provider, String model, double estimatedCostUsd,
                          int tokensEstimate, String armId, String reason) {
        this.provider = provider;
        this.model = model;
        this.estimatedCostUsd = estimatedCostUsd;
        this.tokensEstimate = tokensEstimate;
        this.armId = armId;
        this.reason = reason;
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public double getEstimatedCostUsd() { return estimatedCostUsd; }
    public int getTokensEstimate() { return tokensEstimate; }
    public String getArmId() { return armId; }
    public String getReason() { return reason; }

    public void setProvider(String v) { this.provider = v; }
    public void setModel(String v) { this.model = v; }
    public void setEstimatedCostUsd(double v) { this.estimatedCostUsd = v; }
    public void setTokensEstimate(int v) { this.tokensEstimate = v; }
    public void setArmId(String v) { this.armId = v; }
    public void setReason(String v) { this.reason = v; }
}
