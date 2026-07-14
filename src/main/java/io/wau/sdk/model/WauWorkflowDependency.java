package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * WauWorkflowDependency (per TS L60-L62).
 *
 * <p>DAG 依赖图节点。跟 wau-intent proto WauWorkflowDependency 字段 1:1。
 *
 * @since 1.0.1 (D78 byte-equal + v1.0.0final Phase A.3.4)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class WauWorkflowDependency {

    @JsonProperty("upstream_agents")
    private List<String> upstreamAgents = new ArrayList<>();

    public WauWorkflowDependency() {}

    public WauWorkflowDependency(List<String> upstreamAgents) {
        this.upstreamAgents = upstreamAgents != null ? upstreamAgents : new ArrayList<>();
    }

    public List<String> getUpstreamAgents() { return upstreamAgents; }
    public void setUpstreamAgents(List<String> upstreamAgents) {
        this.upstreamAgents = upstreamAgents != null ? upstreamAgents : new ArrayList<>();
    }
}
