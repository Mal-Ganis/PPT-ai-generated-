package com.example.pptbackend.dto;

public class DocumentProjectRequest {

    private String title;
    private String text;
    private Integer presentationDurationMinutes;
    private String presenterRole;
    private String llmApiKeyPresetId;
    private String llmApiKeyOverride;
    private String llmBaseUrlOverride;
    private String llmModelOverride;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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
