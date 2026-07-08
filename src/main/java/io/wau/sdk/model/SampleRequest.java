package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * SampleRequest — LLM 决策采样请求 DTO(per M3 W3 cost-aware Thompson Sampling)。
 *
 * <p>对应 wau-llm-router `POST /v1/llm/sample`。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class SampleRequest {

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("universe_label")
    private String universeLabel;

    @JsonProperty("max_cost_per_1k")
    private Double maxCostPer1k;

    @JsonProperty("required_capabilities")
    private java.util.List<String> requiredCapabilities;

    @JsonProperty("metadata")
    private Map<String, Object> metadata = new HashMap<>();

    public SampleRequest() {}

    public static Builder builder() { return new Builder(); }

    public String getPrompt() { return prompt; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getUniverseLabel() { return universeLabel; }
    public Double getMaxCostPer1k() { return maxCostPer1k; }
    public java.util.List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setPrompt(String v) { this.prompt = v; }
    public void setUserId(String v) { this.userId = v; }
    public void setTenantId(String v) { this.tenantId = v; }
    public void setUniverseLabel(String v) { this.universeLabel = v; }
    public void setMaxCostPer1k(Double v) { this.maxCostPer1k = v; }
    public void setRequiredCapabilities(java.util.List<String> v) { this.requiredCapabilities = v; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }

    public static final class Builder {
        private String prompt;
        private String userId;
        private String tenantId;
        private String universeLabel;
        private Double maxCostPer1k;
        private java.util.List<String> requiredCapabilities;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder prompt(String v) { this.prompt = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder universeLabel(String v) { this.universeLabel = v; return this; }
        public Builder maxCostPer1k(Double v) { this.maxCostPer1k = v; return this; }
        public Builder requiredCapabilities(java.util.List<String> v) { this.requiredCapabilities = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public SampleRequest build() {
            SampleRequest r = new SampleRequest();
            r.prompt = this.prompt;
            r.userId = this.userId;
            r.tenantId = this.tenantId;
            r.universeLabel = this.universeLabel;
            r.maxCostPer1k = this.maxCostPer1k;
            r.requiredCapabilities = this.requiredCapabilities;
            if (this.metadata != null && !this.metadata.isEmpty()) r.metadata = this.metadata;
            return r;
        }
    }
}
