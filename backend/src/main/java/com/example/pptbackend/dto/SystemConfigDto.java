package com.example.pptbackend.dto;

public class SystemConfigDto {

    private Long id;
    private String llmModel;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer topK;
    private Integer retrievalLimit;
    private String outlinePromptTemplate;
    private String slidePromptTemplate;
    /** 是否强制每份大纲包含 Q&A/问答页（默认 true） */
    private Boolean outlineIncludeQaSlide;

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

    public Boolean getOutlineIncludeQaSlide() {
        return outlineIncludeQaSlide;
    }

    public void setOutlineIncludeQaSlide(Boolean outlineIncludeQaSlide) {
        this.outlineIncludeQaSlide = outlineIncludeQaSlide;
    }
}
