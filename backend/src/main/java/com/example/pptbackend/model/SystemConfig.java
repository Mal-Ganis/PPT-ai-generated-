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
