package io.wau.sdk.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.wau.sdk.exception.WauException;

import java.util.Map;

/**
 * MCP RPC error types (wau-java-sdk v1.3.2, per D87.9).
 *
 * <p>跟 wau-go-sdk {@code mcpclient/errors.go} 字段 1:1 对齐 (cross-SDK D13 byte-equal)。
 * JSON-RPC 2.0 spec 5 code + 3 MCP-specific code (-32001 ~ -32003, 跟 UCP -32101 ~ -32105 错开)。
 */
public class McpException extends WauException {

    private static final long serialVersionUID = 1L;

    // ────────────────────────────────────────────────────────
    // JSON-RPC 2.0 spec error codes (跟 kernel mcp.ErrCode* 一致)
    // ────────────────────────────────────────────────────────

    public static final int ERR_CODE_PARSE = -32700;
    public static final int ERR_CODE_INVALID_REQUEST = -32600;
    public static final int ERR_CODE_METHOD_NOT_FOUND = -32601;
    public static final int ERR_CODE_INVALID_PARAMS = -32602;
    public static final int ERR_CODE_INTERNAL = -32603;

    // MCP-specific (-32001 ~ -32003, 跟 UCP -32101 ~ -32105 错开)
    public static final int ERR_CODE_MCP_AGENT_UNREACHABLE = -32001;
    public static final int ERR_CODE_MCP_INVALID_AGENT_CARD = -32002;
    public static final int ERR_CODE_MCP_TASK_NOT_FOUND = -32003;

    /** JSON-RPC 2.0 error envelope inner DTO。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RPCError {
        @JsonProperty("code")
        public int code;

        @JsonProperty("message")
        public String message;

        @JsonProperty("data")
        public Map<String, Object> data;

        public RPCError() {}
    }

    public final int code;

    public McpException(int code, String message) {
        super(formatError(code, message));
        this.code = code;
    }

    public McpException(int code, String message, Throwable cause) {
        super(formatError(code, message), cause);
        this.code = code;
    }

    private static String formatError(int code, String message) {
        return "mcp rpc error: code=" + code + " message=" + message;
    }

    /** 判断 err 是不是 agent unreachable 语义错误 (MCP spec)。 */
    public static boolean isAgentUnreachable(Throwable err) {
        return err instanceof McpException && ((McpException) err).code == ERR_CODE_MCP_AGENT_UNREACHABLE;
    }

    /** 判断 err 是不是 task 'not found' 语义错误 (MCP spec)。 */
    public static boolean isTaskNotFound(Throwable err) {
        return err instanceof McpException && ((McpException) err).code == ERR_CODE_MCP_TASK_NOT_FOUND;
    }
}