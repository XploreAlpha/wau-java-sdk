package io.wau.sdk.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * MCP DTOs (wau-java-sdk v1.3.2, per D87.9).
 *
 * <p>8 DTOs aligned byte-equal to:
 * <ul>
 *   <li>kernel {@code internal/protocol/mcp/handler.go} response shapes
 *   <li>wau-go-sdk {@code mcpclient/types.go} (v1.3.2, cross-SDK D13 byte-equal)
 *   <li>design doc [[process/2026-07-10-W3-MCP-client-SDK-design]] §二
 * </ul>
 *
 * <p>JSON 字段 snake_case (per MCP spec + {@code @JsonProperty("snake_case")}).
 */
public final class Dto {

    private Dto() {}

    // ────────────────────────────────────────────────────────
    // send_message / parse_agent_card input
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Part {
        @JsonProperty("type")
        public String type = "text";

        @JsonProperty("text")
        public String text;

        @JsonProperty("file")
        public Map<String, Object> file;

        @JsonProperty("data")
        public Map<String, Object> data;

        public Part() {}

        public Part(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public static Part text(String text) {
            return new Part("text", text);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Message {
        @JsonProperty("role")
        public String role = "user";

        @JsonProperty("parts")
        public List<Part> parts;

        @JsonProperty("context_id")
        public String contextId;

        @JsonProperty("metadata")
        public Map<String, Object> metadata;

        public Message() {}

        public Message(String role, List<Part> parts) {
            this.role = role;
            this.parts = parts;
        }

        public static Message user(String text) {
            return new Message("user", List.of(Part.text(text)));
        }
    }

    // ────────────────────────────────────────────────────────
    // Task (send_message / get_task / cancel_task output)
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Artifact {
        @JsonProperty("type")
        public String type;

        @JsonProperty("text")
        public String text;

        @JsonProperty("file")
        public Map<String, Object> file;

        @JsonProperty("data")
        public Map<String, Object> data;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Task {
        @JsonProperty("task_id")
        public String taskId;

        @JsonProperty("context_id")
        public String contextId;

        @JsonProperty("status")
        public String status;

        @JsonProperty("artifacts")
        public List<Artifact> artifacts;

        @JsonProperty("canceled_at")
        public String canceledAt;

        @JsonProperty("history")
        public List<Map<String, Object>> history;
    }

    // ────────────────────────────────────────────────────────
    // Agent card (parse_agent_card / get_extended_agent_card output)
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class AgentCard {
        @JsonProperty("name")
        public String name;

        @JsonProperty("version")
        public String version;

        @JsonProperty("description")
        public String description;

        @JsonProperty("supported_interfaces")
        public List<String> supportedInterfaces;

        @JsonProperty("skills")
        public List<String> skills;

        @JsonProperty("url")
        public String url;

        @JsonProperty("provider")
        public String provider;

        @JsonProperty("documentation_url")
        public String documentationUrl;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ExtendedAgentCard {
        @JsonProperty("name")
        public String name;

        @JsonProperty("version")
        public String version;

        @JsonProperty("description")
        public String description;

        @JsonProperty("supported_interfaces")
        public List<String> supportedInterfaces;

        @JsonProperty("skills")
        public List<String> skills;

        @JsonProperty("trust_score")
        public Double trustScore;

        @JsonProperty("private_skills")
        public List<String> privateSkills;

        @JsonProperty("owner_user_id")
        public String ownerUserId; // per D66=B RBAC
    }

    // ────────────────────────────────────────────────────────
    // Health check (health_check output)
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class HealthCheckResult {
        @JsonProperty("status")
        public String status;

        @JsonProperty("version")
        public String version;

        @JsonProperty("uptime_seconds")
        public Long uptimeSeconds;
    }

    // ────────────────────────────────────────────────────────
    // List tasks (list_tasks output)
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ListTasksFilter {
        @JsonProperty("status")
        public List<String> status;

        @JsonProperty("context_id")
        public String contextId;

        @JsonProperty("limit")
        public Integer limit;

        @JsonProperty("offset")
        public Integer offset;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ListTasksResult {
        @JsonProperty("tasks")
        public List<Task> tasks;

        @JsonProperty("next_offset")
        public Long nextOffset;
    }

    // ────────────────────────────────────────────────────────
    // Push notification config (create_task_push_notification_config)
    // ────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PushConfig {
        @JsonProperty("url")
        public String url;

        @JsonProperty("events")
        public List<String> events;

        @JsonProperty("secret")
        public String secret;

        public PushConfig() {}

        public PushConfig(String url, List<String> events, String secret) {
            this.url = url;
            this.events = events;
            this.secret = secret;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PushConfigResult {
        @JsonProperty("config_id")
        public String configId;
    }
}