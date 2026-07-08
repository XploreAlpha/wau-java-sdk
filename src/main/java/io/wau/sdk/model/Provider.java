package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Provider DTO(per M3 W3 子项 4)— LLM provider 元数据。
 *
 * <p>对应 wau-store `GET /v1/llm/providers` 响应。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Provider {

    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("provider_type")
    private String providerType;

    @JsonProperty("tier")
    private String tier;

    @JsonProperty("cost_input_per_1k")
    private double costInputPer1k;

    @JsonProperty("cost_output_per_1k")
    private double costOutputPer1k;

    @JsonProperty("max_context")
    private int maxContext;

    @JsonProperty("enabled")
    private boolean enabled;

    public Provider() {}

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getProviderType() { return providerType; }
    public String getTier() { return tier; }
    public double getCostInputPer1k() { return costInputPer1k; }
    public double getCostOutputPer1k() { return costOutputPer1k; }
    public int getMaxContext() { return maxContext; }
    public boolean isEnabled() { return enabled; }

    public void setName(String v) { this.name = v; }
    public void setDisplayName(String v) { this.displayName = v; }
    public void setProviderType(String v) { this.providerType = v; }
    public void setTier(String v) { this.tier = v; }
    public void setCostInputPer1k(double v) { this.costInputPer1k = v; }
    public void setCostOutputPer1k(double v) { this.costOutputPer1k = v; }
    public void setMaxContext(int v) { this.maxContext = v; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
