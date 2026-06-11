package com.example.pptbackend.dto;

import java.util.List;

public class SystemConfigDto {

    private Long id;
    private String llmModel;
    /** 服务器默认 OpenAI 兼容接口 Base URL */
    private String llmBaseUrl;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer topK;
    private Integer retrievalLimit;
    private String outlinePromptTemplate;
    private String slidePromptTemplate;
    /** 是否强制每份大纲包含 Q&A/问答页（默认 true） */
    private Boolean outlineIncludeQaSlide;
    /** 正文生成后 Tier1/Tier2 自纠错 */
    private Boolean selfCorrectionEnabled;
    private Double selfCorrectionTier1AutoBelow;
    private Double selfCorrectionTier1FactBelow;
    private Double selfCorrectionTier2AutoBelow;
    private Double selfCorrectionTier2FactBelow;
    /** 管理员维护的 DeepSeek API 密钥预设（含完整 apiKey） */
    private List<LlmApiKeyPresetDto> llmApiKeyPresets;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getRetrievalLimit() {
        return retrievalLimit;
    }

    public void setRetrievalLimit(Integer retrievalLimit) {
        this.retrievalLimit = retrievalLimit;
    }

    public String getOutlinePromptTemplate() {
        return outlinePromptTemplate;
    }

    public void setOutlinePromptTemplate(String outlinePromptTemplate) {
        this.outlinePromptTemplate = outlinePromptTemplate;
    }

    public String getSlidePromptTemplate() {
        return slidePromptTemplate;
    }

    public void setSlidePromptTemplate(String slidePromptTemplate) {
        this.slidePromptTemplate = slidePromptTemplate;
    }

    public Boolean getOutlineIncludeQaSlide() {
        return outlineIncludeQaSlide;
    }

    public void setOutlineIncludeQaSlide(Boolean outlineIncludeQaSlide) {
        this.outlineIncludeQaSlide = outlineIncludeQaSlide;
    }

    public Boolean getSelfCorrectionEnabled() {
        return selfCorrectionEnabled;
    }

    public void setSelfCorrectionEnabled(Boolean selfCorrectionEnabled) {
        this.selfCorrectionEnabled = selfCorrectionEnabled;
    }

    public Double getSelfCorrectionTier1AutoBelow() {
        return selfCorrectionTier1AutoBelow;
    }

    public void setSelfCorrectionTier1AutoBelow(Double selfCorrectionTier1AutoBelow) {
        this.selfCorrectionTier1AutoBelow = selfCorrectionTier1AutoBelow;
    }

    public Double getSelfCorrectionTier1FactBelow() {
        return selfCorrectionTier1FactBelow;
    }

    public void setSelfCorrectionTier1FactBelow(Double selfCorrectionTier1FactBelow) {
        this.selfCorrectionTier1FactBelow = selfCorrectionTier1FactBelow;
    }

    public Double getSelfCorrectionTier2AutoBelow() {
        return selfCorrectionTier2AutoBelow;
    }

    public void setSelfCorrectionTier2AutoBelow(Double selfCorrectionTier2AutoBelow) {
        this.selfCorrectionTier2AutoBelow = selfCorrectionTier2AutoBelow;
    }

    public Double getSelfCorrectionTier2FactBelow() {
        return selfCorrectionTier2FactBelow;
    }

    public void setSelfCorrectionTier2FactBelow(Double selfCorrectionTier2FactBelow) {
        this.selfCorrectionTier2FactBelow = selfCorrectionTier2FactBelow;
    }

    public List<LlmApiKeyPresetDto> getLlmApiKeyPresets() {
        return llmApiKeyPresets;
    }

    public void setLlmApiKeyPresets(List<LlmApiKeyPresetDto> llmApiKeyPresets) {
        this.llmApiKeyPresets = llmApiKeyPresets;
    }
}
