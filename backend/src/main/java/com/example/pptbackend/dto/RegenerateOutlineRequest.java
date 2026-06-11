package com.example.pptbackend.dto;

/**
 * 在已有项目上按主题/文档上下文重新生成大纲。
 */
public class RegenerateOutlineRequest {

    /** 演示主题；缺省时使用项目已保存的 theme */
    private String topic;
    private Integer presentationDurationMinutes;
    /** topic | document，与正文生成一致 */
    private String inputType;
    private String inputContent;
    /** 演示角色；传空字符串表示清除并改由 AI 推断 */
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

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getInputContent() {
        return inputContent;
    }

    public void setInputContent(String inputContent) {
        this.inputContent = inputContent;
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
