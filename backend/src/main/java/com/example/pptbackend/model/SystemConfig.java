package com.example.pptbackend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "llm_model", nullable = false)
    private String llmModel;

    /** OpenAI 兼容 Chat Completions 默认 Base URL */
    @Column(name = "llm_base_url")
    private String llmBaseUrl;

    @Column(nullable = false)
    private Double temperature;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens;

    /** DB column is top_p; Hibernate would otherwise map topP → topp and leave top_p null. */
    @Column(name = "top_p", nullable = false)
    private Double topP;

    @Column(name = "top_k", nullable = false)
    private Integer topK;

    @Column(name = "retrieval_limit", nullable = false)
    private Integer retrievalLimit;

    @Column(name = "outline_prompt_template", columnDefinition = "TEXT")
    private String outlinePromptTemplate;

    @Column(name = "slide_prompt_template", columnDefinition = "TEXT")
    private String slidePromptTemplate;

    /** 为 true 时每份大纲须含 Q&A/问答页（模型未生成时后端自动补页） */
    @Column(name = "outline_include_qa_slide")
    private Boolean outlineIncludeQaSlide = true;

    /** 正文生成后是否启用 Tier1/Tier2 自纠错重生成 */
    @Column(name = "self_correction_enabled")
    private Boolean selfCorrectionEnabled = false;

    @Column(name = "self_correction_tier1_auto_below")
    private Double selfCorrectionTier1AutoBelow = 76.0;

    @Column(name = "self_correction_tier1_fact_below")
    private Double selfCorrectionTier1FactBelow = 0.58;

    @Column(name = "self_correction_tier2_auto_below")
    private Double selfCorrectionTier2AutoBelow = 70.0;

    @Column(name = "self_correction_tier2_fact_below")
    private Double selfCorrectionTier2FactBelow = 0.48;

    /** JSON：管理员配置的 LLM API 密钥预设列表 */
    @Column(name = "llm_api_key_presets_json", columnDefinition = "TEXT")
    private String llmApiKeyPresetsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public String getLlmApiKeyPresetsJson() {
        return llmApiKeyPresetsJson;
    }

    public void setLlmApiKeyPresetsJson(String llmApiKeyPresetsJson) {
        this.llmApiKeyPresetsJson = llmApiKeyPresetsJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
