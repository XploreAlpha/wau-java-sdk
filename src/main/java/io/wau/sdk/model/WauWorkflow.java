package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * WauWorkflow (per v1.0.1 SoT doc + SDK Consumer Contract §二.2 + D78 byte-equal).
 *
 * <p>5 SDK 共享 wire format 的核心 DTO。
 *
 * <p>19 字段 (5 必填 + 14 元数据):
 * <ul>
 *   <li>必填 5 字段:agents / dependency_graph / confidence / workflow_type / harness</li>
 *   <li>标识 3 字段:workflow_id / created_at / user_id</li>
 *   <li>DAG pattern 元数据 3 optional:dag_pattern_hint / description / estimated_duration_ms</li>
 *   <li>推荐上下文 3 字段:original_query / parent_workflow_id? / retry_count?</li>
 *   <li>Server metadata 3 字段:server_version / trace_id / ttl_ms</li>
 *   <li>鉴权 2 字段:auth_user_id / auth_claim_set</li>
 * </ul>
 *
 * <p>JSON 字段 snake_case (per TS L15 + WauWorkflow msg type spec §三.3 + #14 A 拍板)。
 *
 * <p>⚠️ voice workflow 必须 harness='codex-appserver' (per #17 配错保护)。
 *
 * <p>TS canonical 锚点: typescript-sdk/src/wau/types.ts#L116-L158
 *
 * @since 1.0.1 (D78 byte-equal + v1.0.0final Phase A.3.4)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class WauWorkflow {

    // === 必填 5 字段 ===
    @JsonProperty("agents")
    private List<WauWorkflowAgent> agents = new ArrayList<>();

    @JsonProperty("dependency_graph")
    private WauWorkflowDependencyGraph dependencyGraph;

    @JsonProperty("confidence")
    private double confidence;

    @JsonProperty("workflow_type")
    private WauWorkflowType workflowType;

    /** voice workflow 必须 'codex-appserver',其它 harness 抛错 (per #17) */
    @JsonProperty("harness")
    private String harness;

    // === 标识字段 ===
    @JsonProperty("workflow_id")
    private String workflowId;

    /** unix ms */
    @JsonProperty("created_at")
    private long createdAt;

    @JsonProperty("user_id")
    private String userId;

    // === DAG pattern 元数据 (per #4 抽象 wau-dag-patterns) ===
    @JsonProperty("dag_pattern_hint")
    private String dagPatternHint;

    @JsonProperty("description")
    private String description;

    @JsonProperty("estimated_duration_ms")
    private Long estimatedDurationMs;

    // === 推荐上下文 ===
    /** 用户原始 query */
    @JsonProperty("original_query")
    private String originalQuery;

    /** 子 workflow 追溯 */
    @JsonProperty("parent_workflow_id")
    private String parentWorkflowId;

    /** 0=首次,1+=重试 */
    @JsonProperty("retry_count")
    private Integer retryCount;

    // === Server-side metadata ===
    /** wau-intent server version,byte-equal verify anchor */
    @JsonProperty("server_version")
    private String serverVersion;

    /** 跨 SDK 调试 trace */
    @JsonProperty("trace_id")
    private String traceId;

    /** workflow 有效期,过期 client 拒收 */
    @JsonProperty("ttl_ms")
    private long ttlMs;

    // === 鉴权上下文 (per D66=B JWT 4-claim) ===
    @JsonProperty("auth_user_id")
    private String authUserId;

    /** 4 claim names: sub/aud/exp/scope */
    @JsonProperty("auth_claim_set")
    private List<String> authClaimSet = new ArrayList<>();

    public WauWorkflow() {}

    // ===== Getters/Setters =====

    public List<WauWorkflowAgent> getAgents() { return agents; }
    public void setAgents(List<WauWorkflowAgent> agents) {
        this.agents = agents != null ? agents : new ArrayList<>();
    }

    public WauWorkflowDependencyGraph getDependencyGraph() { return dependencyGraph; }
    public void setDependencyGraph(WauWorkflowDependencyGraph dependencyGraph) {
        this.dependencyGraph = dependencyGraph;
    }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public WauWorkflowType getWorkflowType() { return workflowType; }
    public void setWorkflowType(WauWorkflowType workflowType) { this.workflowType = workflowType; }

    public String getHarness() { return harness; }
    public void setHarness(String harness) { this.harness = harness; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDagPatternHint() { return dagPatternHint; }
    public void setDagPatternHint(String dagPatternHint) { this.dagPatternHint = dagPatternHint; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getEstimatedDurationMs() { return estimatedDurationMs; }
    public void setEstimatedDurationMs(Long estimatedDurationMs) {
        this.estimatedDurationMs = estimatedDurationMs;
    }

    public String getOriginalQuery() { return originalQuery; }
    public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }

    public String getParentWorkflowId() { return parentWorkflowId; }
    public void setParentWorkflowId(String parentWorkflowId) {
        this.parentWorkflowId = parentWorkflowId;
    }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public long getTtlMs() { return ttlMs; }
    public void setTtlMs(long ttlMs) { this.ttlMs = ttlMs; }

    public String getAuthUserId() { return authUserId; }
    public void setAuthUserId(String authUserId) { this.authUserId = authUserId; }

    public List<String> getAuthClaimSet() { return authClaimSet; }
    public void setAuthClaimSet(List<String> authClaimSet) {
        this.authClaimSet = authClaimSet != null ? authClaimSet : new ArrayList<>();
    }
}
