package com.example.pptbackend.dto;

public class TopicProjectRequest {

    private String topic;
    /** 目标演讲时长（分钟），5–60；缺省 15。 */
    private Integer presentationDurationMinutes;
    /** 演示角色；留空则 AI 根据输入推断 */
    private String presenterRole;
    private String llmApiKeyPresetId;
    private String llmApiKeyOverride;
    private String llmBaseUrlOverride;
    private String llmModelOverride;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getPresentationDurationMinutes() {
        return presentationDurationMinutes;
    }

    public void setPresentationDurationMinutes(Integer presentationDurationMinutes) {
        this.presentationDurationMinutes = presentationDurationMinutes;
    }

    public String getPresenterRole() {
        return presenterRole;
    }

    public void setPresenterRole(String presenterRole) {
        this.presenterRole = presenterRole;
    }

    public String getLlmApiKeyPresetId() {
        return llmApiKeyPresetId;
    }

    public void setLlmApiKeyPresetId(String llmApiKeyPresetId) {
        this.llmApiKeyPresetId = llmApiKeyPresetId;
    }

    public String getLlmApiKeyOverride() {
        return llmApiKeyOverride;
    }

    public void setLlmApiKeyOverride(String llmApiKeyOverride) {
        this.llmApiKeyOverride = llmApiKeyOverride;
    }

    public String getLlmBaseUrlOverride() {
        return llmBaseUrlOverride;
    }

    public void setLlmBaseUrlOverride(String llmBaseUrlOverride) {
        this.llmBaseUrlOverride = llmBaseUrlOverride;
    }

    public String getLlmModelOverride() {
        return llmModelOverride;
    }

    public void setLlmModelOverride(String llmModelOverride) {
        this.llmModelOverride = llmModelOverride;
    }
}
