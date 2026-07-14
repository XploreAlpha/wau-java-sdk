package io.wau.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wau.sdk.model.WauWorkflow;
import io.wau.sdk.model.WauWorkflowAgent;
import io.wau.sdk.model.WauWorkflowDependency;
import io.wau.sdk.model.WauWorkflowDependencyGraph;
import io.wau.sdk.model.WauWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.0.1 D78 byte-equal verify — wau-java-sdk WauWorkflow 19 字段 byte-equal TS canonical
 *
 * <p>测试目标 (per v1.0.0final Phase A.3.4):
 * <ul>
 *   <li>验证 19 字段都存在</li>
 *   <li>验证 JSON 序列化 snake_case (per TS L15 "JSON 字段 snake_case")</li>
 *   <li>验证 WauWorkflowType 6 enum 值 + 嵌套类型 1:1 跟 TS 一致</li>
 * </ul>
 *
 * <p>TS canonical 锚点: typescript-sdk/src/wau/types.ts#L116-L158
 */
class WauWorkflowTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void wau_workflow_field_count_is_19() throws Exception {
        // 19 字段 = 5 必填 + 3 标识 + 3 DAG + 3 推荐 + 3 Server + 2 鉴权
        Map<String, WauWorkflowDependency> deps = new HashMap<>();
        deps.put("d1", new WauWorkflowDependency(Arrays.asList("u1")));

        WauWorkflow w = new WauWorkflow();
        w.setAgents(Arrays.asList(
                new WauWorkflowAgent("a1", "http://x", Arrays.asList("s1"), 0.9)));
        w.setDependencyGraph(new WauWorkflowDependencyGraph(deps));
        w.setConfidence(0.9);
        w.setWorkflowType(WauWorkflowType.WORKFLOW_TYPE_SINGLE);
        w.setHarness("codex-appserver");
        w.setWorkflowId("wf-1");
        w.setCreatedAt(1700000000000L);
        w.setUserId("u-1");
        w.setDagPatternHint("pattern-x");
        w.setDescription("desc");
        w.setEstimatedDurationMs(5000L);
        w.setOriginalQuery("q");
        w.setParentWorkflowId("pwf-1");
        w.setRetryCount(0);
        w.setServerVersion("v1.0.0");
        w.setTraceId("t-1");
        w.setTtlMs(30000L);
        w.setAuthUserId("u-1");
        w.setAuthClaimSet(Arrays.asList("sub", "aud", "exp", "scope"));

        String json = mapper.writeValueAsString(w);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(json, Map.class);
        assertThat(map).hasSize(19);
    }

    @Test
    void wau_workflow_all_snake_case_keys() throws Exception {
        Map<String, WauWorkflowDependency> deps = new HashMap<>();
        deps.put("d1", new WauWorkflowDependency(Arrays.asList("u1")));

        WauWorkflow w = new WauWorkflow();
        w.setAgents(Arrays.asList(
                new WauWorkflowAgent("a1", "http://x", Arrays.asList("s1"), 0.9)));
        w.setDependencyGraph(new WauWorkflowDependencyGraph(deps));
        w.setConfidence(0.9);
        w.setWorkflowType(WauWorkflowType.WORKFLOW_TYPE_SINGLE);
        w.setHarness("codex-appserver");
        w.setWorkflowId("wf-1");
        w.setCreatedAt(1700000000000L);
        w.setUserId("u-1");
        w.setOriginalQuery("q");
        w.setServerVersion("v1.0.0");
        w.setTraceId("t-1");
        w.setTtlMs(30000L);
        w.setAuthUserId("u-1");
        w.setAuthClaimSet(Arrays.asList("sub", "aud", "exp", "scope"));

        String json = mapper.writeValueAsString(w);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(json, Map.class);

        // 期望 snake_case keys
        List<String> expected = Arrays.asList(
                "agents", "dependency_graph", "confidence", "workflow_type", "harness",
                "workflow_id", "created_at", "user_id",
                "original_query",
                "server_version", "trace_id", "ttl_ms",
                "auth_user_id", "auth_claim_set"
        );
        for (String k : expected) {
            assertThat(map).as("missing snake_case key: %s in %s", k, json).containsKey(k);
        }

        // 不应出现 camelCase
        List<String> forbidden = Arrays.asList(
                "workflowId", "createdAt", "userId", "dagPatternHint",
                "estimatedDurationMs", "parentWorkflowId", "retryCount",
                "serverVersion", "traceId", "ttlMs", "authUserId", "authClaimSet",
                "dependencyGraph", "workflowType"
        );
        for (String k : forbidden) {
            assertThat(map).as("unexpected camelCase key: %s in %s", k, json).doesNotContainKey(k);
        }
    }

    @Test
    void wau_workflow_type_six_enum_values() throws Exception {
        // 6 enum 值 + 各自 wire format
        assertThat(WauWorkflowType.values()).hasSize(6);
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_UNSPECIFIED))
                .isEqualTo("\"WORKFLOW_TYPE_UNSPECIFIED\"");
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_SINGLE))
                .isEqualTo("\"WORKFLOW_TYPE_SINGLE\"");
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_CHAIN))
                .isEqualTo("\"WORKFLOW_TYPE_CHAIN\"");
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_PARALLEL))
                .isEqualTo("\"WORKFLOW_TYPE_PARALLEL\"");
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_QUORUM))
                .isEqualTo("\"WORKFLOW_TYPE_QUORUM\"");
        assertThat(mapper.writeValueAsString(WauWorkflowType.WORKFLOW_TYPE_FAN_OUT))
                .isEqualTo("\"WORKFLOW_TYPE_FAN_OUT\"");
    }

    @Test
    void wau_workflow_agent_four_fields() throws Exception {
        WauWorkflowAgent a = new WauWorkflowAgent("agent-1", "http://x", Arrays.asList("s1"), 0.95);
        String json = mapper.writeValueAsString(a);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(json, Map.class);
        assertThat(map).hasSize(4);
        assertThat(map).containsKeys("name", "url", "skills", "confidence");
    }

    @Test
    void wau_workflow_dependency_one_field() throws Exception {
        WauWorkflowDependency d = new WauWorkflowDependency(Arrays.asList("u1", "u2"));
        String json = mapper.writeValueAsString(d);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(json, Map.class);
        assertThat(map).hasSize(1);
        assertThat(map).containsKey("upstream_agents");
        @SuppressWarnings("unchecked")
        List<String> upstream = (List<String>) map.get("upstream_agents");
        assertThat(upstream).containsExactly("u1", "u2");
    }

    @Test
    void wau_workflow_dependency_graph_nested() throws Exception {
        Map<String, WauWorkflowDependency> deps = new HashMap<>();
        deps.put("d1", new WauWorkflowDependency(Arrays.asList("u1")));
        WauWorkflowDependencyGraph g = new WauWorkflowDependencyGraph(deps);

        String json = mapper.writeValueAsString(g);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(json, Map.class);
        assertThat(map).containsKey("dependencies");
    }
}
