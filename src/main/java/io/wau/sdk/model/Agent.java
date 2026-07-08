package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Agent DTO(per M8 W4 子项 8)— wau-registry `GET /registry/agents/{id}` 响应。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Agent {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("owner_user_id")
    private Long ownerUserId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("capability_tags")
    private List<String> capabilityTags;

    @JsonProperty("universe_label")
    private String universeLabel;

    @JsonProperty("enabled")
    private boolean enabled;

    public Agent() {}

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEndpoint() { return endpoint; }
    public List<String> getCapabilityTags() { return capabilityTags; }
    public String getUniverseLabel() { return universeLabel; }
    public boolean isEnabled() { return enabled; }

    public void setId(String v) { this.id = v; }
    public void setTenantId(String v) { this.tenantId = v; }
    public void setOwnerUserId(Long v) { this.ownerUserId = v; }
    public void setName(String v) { this.name = v; }
    public void setDescription(String v) { this.description = v; }
    public void setEndpoint(String v) { this.endpoint = v; }
    public void setCapabilityTags(List<String> v) { this.capabilityTags = v; }
    public void setUniverseLabel(String v) { this.universeLabel = v; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
