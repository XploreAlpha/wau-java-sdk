package io.wau.sdk.mcp;

/**
 * MCP tool name constants — 10 tool constants aligned byte-equal to
 * WAU-core-kernel {@code internal/protocol/mcp/tools.go} ToolXxx + handler routeToProtocol.
 *
 * <p>W3 D87.1 实装 8 sync tool, 2 SSE tool (stream_message + subscribe_to_task) deferred to W5+.
 *
 * <p>⭐ v1.0.0 D87.9 W3 + W4-W5 实装 (2026-07-11).
 */
public final class Tools {

    private Tools() {}

    // 8 sync tools (W3 实装, 镜像 wau-go-sdk mcpclient/tools.go)
    public static final String TOOL_HEALTH_CHECK = "health_check";
    public static final String TOOL_PARSE_AGENT_CARD = "parse_agent_card";
    public static final String TOOL_SEND_MESSAGE = "send_message";
    public static final String TOOL_GET_TASK = "get_task";
    public static final String TOOL_LIST_TASKS = "list_tasks";
    public static final String TOOL_CANCEL_TASK = "cancel_task";
    public static final String TOOL_CREATE_TASK_PUSH_NOTIFICATION_CONFIG = "create_task_push_notification_config";
    public static final String TOOL_GET_EXTENDED_AGENT_CARD = "get_extended_agent_card";

    // 2 SSE tools (W5+ deferred, 本 SDK 暂不暴露 typed wrapper)
    public static final String TOOL_STREAM_MESSAGE = "stream_message";
    public static final String TOOL_SUBSCRIBE_TO_TASK = "subscribe_to_task";

    /** 10 个 tool name 字面量(便于测试断言 / debugging)。 */
    public static final String[] ALL_TOOL_NAMES = new String[] {
        TOOL_HEALTH_CHECK,
        TOOL_PARSE_AGENT_CARD,
        TOOL_SEND_MESSAGE,
        TOOL_STREAM_MESSAGE,
        TOOL_GET_TASK,
        TOOL_LIST_TASKS,
        TOOL_CANCEL_TASK,
        TOOL_SUBSCRIBE_TO_TASK,
        TOOL_CREATE_TASK_PUSH_NOTIFICATION_CONFIG,
        TOOL_GET_EXTENDED_AGENT_CARD,
    };

    /** 判断 tool 是不是 SSE 流式 tool (W5+ streaming)。 */
    public static boolean isStreamingTool(String toolName) {
        return TOOL_STREAM_MESSAGE.equals(toolName) || TOOL_SUBSCRIBE_TO_TASK.equals(toolName);
    }
}