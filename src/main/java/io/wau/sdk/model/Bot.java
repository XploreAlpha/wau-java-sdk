package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bot DTO(per M10 N3.3)。
 *
 * <p>公开 ID 格式(per D78):{@code bot:<tenant>:<botid>} snake_case。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Bot {

    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("owner_user_id")
    private Long ownerUserId;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("backend")
    private String backend;

    @JsonProperty("enabled")
    private boolean enabled;

    public Bot() {}

    public Bot(String botId, String tenantId, Long ownerUserId,
               String displayName, String backend, boolean enabled) {
        this.botId = botId;
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.displayName = displayName;
        this.backend = backend;
        this.enabled = enabled;
    }

    public String getBotId() { return botId; }
    public String getTenantId() { return tenantId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getDisplayName() { return displayName; }
    public String getBackend() { return backend; }
    public boolean isEnabled() { return enabled; }

    public void setBotId(String v) { this.botId = v; }
    public void setTenantId(String v) { this.tenantId = v; }
    public void setOwnerUserId(Long v) { this.ownerUserId = v; }
    public void setDisplayName(String v) { this.displayName = v; }
    public void setBackend(String v) { this.backend = v; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
