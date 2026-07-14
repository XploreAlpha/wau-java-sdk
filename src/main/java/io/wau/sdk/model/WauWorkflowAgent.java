package io.wau.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * WauWorkflowAgent (per TS L49-L55).
 *
 * <p>WauWorkflow 里单个 agent 推荐块。跟 wau-intent proto WauWorkflowAgent 字段 1:1。
 *
 * @since 1.0.1 (D78 byte-equal + v1.0.0final Phase A.3.4)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class WauWorkflowAgent {

    @JsonProperty("name")
    private String name;

    @JsonProperty("url")
    private String url;

    @JsonProperty("skills")
    private List<String> skills = new ArrayList<>();

    @JsonProperty("confidence")
    private double confidence;

    public WauWorkflowAgent() {}

    public WauWorkflowAgent(String name, String url, List<String> skills, double confidence) {
        this.name = name;
        this.url = url;
        this.skills = skills != null ? skills : new ArrayList<>();
        this.confidence = confidence;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills != null ? skills : new ArrayList<>(); }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
