package io.wau.sdk.l5;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.wau.sdk.transport.HttpTransport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * L5Service — wau-java-sdk ⭐L5 包管理器 client(per D72/D73/D74,2026-07-10)。
 *
 * <h2>5 核心方法对齐 wau-go-sdk L5Service</h2>
 * <table>
 *   <tr><th>#</th><th>方法</th><th>HTTP</th><th>用途</th><th>关联 decision</th></tr>
 *   <tr><td>1</td><td>{@link #install}</td><td>POST /v1/l5/install</td><td>装 agent(类比 apt install)</td><td>D72</td></tr>
 *   <tr><td>2</td><td>{@link #uninstall}</td><td>POST /v1/l5/uninstall</td><td>卸 agent(--purge)</td><td>D72+D73</td></tr>
 *   <tr><td>3</td><td>{@link #update}</td><td>POST /v1/l5/update</td><td>更新 agent(--version 锁版)</td><td>D72</td></tr>
 *   <tr><td>4</td><td>{@link #search}</td><td>POST /v1/l5/search</td><td>搜 wau-registry(--universe/--limit)</td><td>D72+D74</td></tr>
 *   <tr><td>5</td><td>{@link #login}</td><td>POST /v1/l5/login</td><td>拿 token(落 ~/.wau/credentials)</td><td>D72+D74</td></tr>
 * </table>
 *
 * <h2>5 SDK 一致</h2>
 * <p>对齐 wau-go-sdk L5Service(per D13 JSON snake_case 协议)
 * + wau-python-sdk / wau-typescript-sdk / wau-rust-sdk 同名方法。
 *
 * <h2>D60 additive</h2>
 * <p>0 改 WauClient 老 5 核心方法,纯加 l5() getter 暴露 L5Service。
 *
 * @since 0.2.0-preview (v1.3.3 L5 deadline 提前, 2026-07-10)
 */
public final class L5Service {

    private final io.wau.sdk.WauConfig cfg;
    private final HttpTransport http;

    public L5Service(io.wau.sdk.WauConfig cfg, HttpTransport http) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.http = Objects.requireNonNull(http, "http");
    }

    // ============================================================
    // 1. install — 装 agent
    // ============================================================

    /**
     * 装 agent 到当前 user(per D72 wau-toolkit v2.0 OS-level install)。
     *
     * @param req install 请求(user_id + agent_name + 可选 version + config)
     * @return install result(agent_id + version + docker sandbox id)
     */
    public InstallResponse install(InstallRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/l5/install";
        Map<String, String> headers = baseHeaders();
        if (cfg.botToken() != null) {
            headers.put("Authorization", "Bearer " + cfg.botToken());
        }
        return http.postJson(url, headers, req, InstallResponse.class);
    }

    // ============================================================
    // 2. uninstall — 卸 agent
    // ============================================================

    /**
     * 卸 agent。purge=false 保留 SnapshotPath(per D73 软删除,保护 user_skill + agent_memory 数据)。
     *
     * @param req uninstall 请求(user_id + agent_name + 可选 purge)
     * @return uninstall result(snapshot_path if purge=false)
     */
    public UninstallResponse uninstall(UninstallRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/l5/uninstall";
        return http.postJson(url, baseHeaders(), req, UninstallResponse.class);
    }

    // ============================================================
    // 3. update — 更新 agent
    // ============================================================

    /**
     * 更新 agent。agentName=null 全更新;targetVersion="" 应用最新;targetVersion="x.y.z" 锁版。
     *
     * @param req update 请求(user_id + 可选 agent_name + 可选 target_version)
     * @return update result(updated_count + updated_agents[])
     */
    public UpdateResponse update(UpdateRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/l5/update";
        return http.postJson(url, baseHeaders(), req, UpdateResponse.class);
    }

    // ============================================================
    // 4. search — 搜 wau-registry
    // ============================================================

    /**
     * 搜 wau-registry(per D72 + D74 gRPC 转发)。
     *
     * @param req search 请求(user_id + query + 可选 universe + limit)
     * @return search result(results[] + total)
     */
    public SearchResponse search(SearchRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/l5/search";
        return http.postJson(url, baseHeaders(), req, SearchResponse.class);
    }

    // ============================================================
    // 5. login — 拿 token
    // ============================================================

    /**
     * 登入拿 token(per D74,客户端建议落 ~/.wau/credentials 0600 permission)。
     *
     * <p>WauClient 端会在响应里带回 user_id,可后续调用 .userId() 拿凭证。
     *
     * @param req login 请求(username + password + 可选 endpoint)
     * @return login result(access_token + refresh_token + expires_at + user_id)
     */
    public LoginResponse login(LoginRequest req) {
        Objects.requireNonNull(req, "req");
        String url = cfg.wauEdgeUrl() + "/v1/l5/login";
        return http.postJson(url, baseHeaders(), req, LoginResponse.class);
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private Map<String, String> baseHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("X-WAU-Client", "wau-java-sdk/0.2.0-preview");
        if (cfg.botToken() != null) {
            h.put("Authorization", "Bearer " + cfg.botToken());
        }
        return h;
    }

    // ============================================================
    // === request / response DTOs (snake_case JSON per D13) ===
    // ============================================================

    /** Install 请求(per D60 additive,字段跟 wau-go-sdk L5InstallRequest 对齐)。 */
    public static final class InstallRequest {
        @JsonProperty("user_id")    private String userId;
        @JsonProperty("agent_name") private String agentName;
        @JsonProperty("version")    private String version;
        @JsonProperty("purge")      private Boolean purge;
        @JsonProperty("config")     private Map<String, String> config;

        public InstallRequest() {}

        public InstallRequest(String userId, String agentName) {
            this.userId = userId;
            this.agentName = agentName;
        }

        public InstallRequest(String userId, String agentName, String version) {
            this(userId, agentName);
            this.version = version;
        }

        // getters / setters
        public String getUserId() { return userId; }
        public void setUserId(String v) { this.userId = v; }
        public String getAgentName() { return agentName; }
        public void setAgentName(String v) { this.agentName = v; }
        public String getVersion() { return version; }
        public void setVersion(String v) { this.version = v; }
        public Boolean getPurge() { return purge; }
        public void setPurge(Boolean v) { this.purge = v; }
        public Map<String, String> getConfig() { return config; }
        public void setConfig(Map<String, String> v) { this.config = v; }
    }

    /** Install 响应。 */
    public static final class InstallResponse {
        @JsonProperty("ok")         private Boolean ok;
        @JsonProperty("agent_id")   private String agentId;
        @JsonProperty("version")    private String version;
        @JsonProperty("installed_at") private java.time.Instant installedAt;
        @JsonProperty("duration_ms") private Double durationMs;
        @JsonProperty("sandbox_docker_id") private String sandboxDockerId;
        @JsonProperty("error")      private String error;

        public Boolean getOk() { return ok; }
        public String getAgentId() { return agentId; }
        public String getVersion() { return version; }
        public java.time.Instant getInstalledAt() { return installedAt; }
        public Double getDurationMs() { return durationMs; }
        public String getSandboxDockerId() { return sandboxDockerId; }
        public String getError() { return error; }
    }

    /** Uninstall 请求。 */
    public static final class UninstallRequest {
        @JsonProperty("user_id")    private String userId;
        @JsonProperty("agent_name") private String agentName;
        @JsonProperty("purge")      private Boolean purge;

        public UninstallRequest() {}
        public UninstallRequest(String userId, String agentName, boolean purge) {
            this.userId = userId; this.agentName = agentName; this.purge = purge;
        }
        public String getUserId() { return userId; }
        public void setUserId(String v) { this.userId = v; }
        public String getAgentName() { return agentName; }
        public void setAgentName(String v) { this.agentName = v; }
        public Boolean getPurge() { return purge; }
        public void setPurge(Boolean v) { this.purge = v; }
    }

    /** Uninstall 响应。 */
    public static final class UninstallResponse {
        @JsonProperty("ok")            private Boolean ok;
        @JsonProperty("uninstalled_at") private java.time.Instant uninstalledAt;
        @JsonProperty("snapshot_path") private String snapshotPath;
        @JsonProperty("error")         private String error;

        public Boolean getOk() { return ok; }
        public java.time.Instant getUninstalledAt() { return uninstalledAt; }
        public String getSnapshotPath() { return snapshotPath; }
        public String getError() { return error; }
    }

    /** Update 请求。 */
    public static final class UpdateRequest {
        @JsonProperty("user_id")        private String userId;
        @JsonProperty("agent_name")     private String agentName;
        @JsonProperty("target_version") private String targetVersion;

        public UpdateRequest() {}
        public UpdateRequest(String userId) { this.userId = userId; }
        public UpdateRequest(String userId, String agentName) {
            this(userId); this.agentName = agentName;
        }
        public UpdateRequest(String userId, String agentName, String targetVersion) {
            this(userId, agentName); this.targetVersion = targetVersion;
        }
        public String getUserId() { return userId; }
        public void setUserId(String v) { this.userId = v; }
        public String getAgentName() { return agentName; }
        public void setAgentName(String v) { this.agentName = v; }
        public String getTargetVersion() { return targetVersion; }
        public void setTargetVersion(String v) { this.targetVersion = v; }
    }

    /** Update 响应。 */
    public static final class UpdateResponse {
        @JsonProperty("ok")             private Boolean ok;
        @JsonProperty("updated_count")  private Integer updatedCount;
        @JsonProperty("updated_agents") private List<String> updatedAgents;
        @JsonProperty("error")          private String error;

        public Boolean getOk() { return ok; }
        public Integer getUpdatedCount() { return updatedCount; }
        public List<String> getUpdatedAgents() { return updatedAgents; }
        public String getError() { return error; }
    }

    /** Search 请求。 */
    public static final class SearchRequest {
        @JsonProperty("user_id")  private String userId;
        @JsonProperty("query")    private String query;
        @JsonProperty("universe") private String universe;
        @JsonProperty("limit")    private Integer limit;

        public SearchRequest() {}
        public SearchRequest(String userId, String query) {
            this.userId = userId; this.query = query;
        }
        public String getUserId() { return userId; }
        public void setUserId(String v) { this.userId = v; }
        public String getQuery() { return query; }
        public void setQuery(String v) { this.query = v; }
        public String getUniverse() { return universe; }
        public void setUniverse(String v) { this.universe = v; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer v) { this.limit = v; }
    }

    /** Search hit(单条)。 */
    public static final class SearchHit {
        @JsonProperty("name")         private String name;
        @JsonProperty("version")      private String version;
        @JsonProperty("description")  private String description;
        @JsonProperty("author")       private String author;
        @JsonProperty("universe")     private String universe;
        @JsonProperty("homepage")     private String homepage;
        @JsonProperty("trust_score")  private Double trustScore;

        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getUniverse() { return universe; }
        public String getHomepage() { return homepage; }
        public Double getTrustScore() { return trustScore; }
    }

    /** Search 响应。 */
    public static final class SearchResponse {
        @JsonProperty("ok")      private Boolean ok;
        @JsonProperty("results") private List<SearchHit> results;
        @JsonProperty("total")   private Integer total;
        @JsonProperty("error")   private String error;

        public Boolean getOk() { return ok; }
        public List<SearchHit> getResults() { return results; }
        public Integer getTotal() { return total; }
        public String getError() { return error; }
    }

    /** Login 请求。 */
    public static final class LoginRequest {
        @JsonProperty("username") private String username;
        @JsonProperty("password") private String password;
        @JsonProperty("endpoint") private String endpoint;

        public LoginRequest() {}
        public LoginRequest(String username, String password) {
            this.username = username; this.password = password;
        }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { this.endpoint = v; }
    }

    /** Login 响应。 */
    public static final class LoginResponse {
        @JsonProperty("ok")            private Boolean ok;
        @JsonProperty("access_token")  private String accessToken;
        @JsonProperty("refresh_token") private String refreshToken;
        @JsonProperty("expires_at")    private java.time.Instant expiresAt;
        @JsonProperty("user_id")       private String userId;
        @JsonProperty("error")         private String error;

        public Boolean getOk() { return ok; }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public java.time.Instant getExpiresAt() { return expiresAt; }
        public String getUserId() { return userId; }
        public String getError() { return error; }
    }
}
