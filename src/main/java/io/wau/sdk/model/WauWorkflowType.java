package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WauWorkflowType enum (6 值, per TS L34-L40).
 *
 * <p>跟 wau-intent / afp-protocol proto WorkflowType 一致。5 SDK byte-equal,
 * wire format 是 snake_case uppercase 字符串。
 *
 * @since 1.0.1 (D78 byte-equal + v1.0.0final Phase A.3.4)
 */
public enum WauWorkflowType {
    @JsonProperty("WORKFLOW_TYPE_UNSPECIFIED")
    WORKFLOW_TYPE_UNSPECIFIED,
    @JsonProperty("WORKFLOW_TYPE_SINGLE")
    WORKFLOW_TYPE_SINGLE,
    @JsonProperty("WORKFLOW_TYPE_CHAIN")
    WORKFLOW_TYPE_CHAIN,
    @JsonProperty("WORKFLOW_TYPE_PARALLEL")
    WORKFLOW_TYPE_PARALLEL,
    @JsonProperty("WORKFLOW_TYPE_QUORUM")
    WORKFLOW_TYPE_QUORUM,
    @JsonProperty("WORKFLOW_TYPE_FAN_OUT")
    WORKFLOW_TYPE_FAN_OUT
}
