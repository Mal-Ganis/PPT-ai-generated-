package com.example.pptbackend.dto;

public class GenerateSlidesRequest {

    private String inputType = "topic";
    private String inputContent = "";
    private String llmApiKeyPresetId;
    private String llmApiKeyOverride;
    private String llmBaseUrlOverride;
    private String llmModelOverride;

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
