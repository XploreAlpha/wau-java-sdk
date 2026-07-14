package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * WauWorkflowDependencyGraph (per TS L119-L121).
 *
 * <p>WauWorkflow.dependency_graph 嵌套结构。
 *
 * @since 1.0.1 (D78 byte-equal + v1.0.0final Phase A.3.4)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class WauWorkflowDependencyGraph {

    @JsonProperty("dependencies")
    private Map<String, WauWorkflowDependency> dependencies = new HashMap<>();

    public WauWorkflowDependencyGraph() {}

    public WauWorkflowDependencyGraph(Map<String, WauWorkflowDependency> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new HashMap<>();
    }

    public Map<String, WauWorkflowDependency> getDependencies() { return dependencies; }
    public void setDependencies(Map<String, WauWorkflowDependency> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new HashMap<>();
    }
}
