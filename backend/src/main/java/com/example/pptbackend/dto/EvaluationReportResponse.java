package com.example.pptbackend.dto;

import java.time.OffsetDateTime;

public class EvaluationReportResponse {

    private Long id;
    private Long projectId;
    private Long pageId;
    private Integer outlineLogicScore;
    private Integer factualAccuracyScore;
    private Integer infoDensityScore;
    private Integer languageExpressionScore;
    private Double totalScore;
    private Integer autoOutlineLogicScore;
    private Integer autoInfoDensityScore;
    private Integer autoFactualAccuracyScore;
    private Integer autoLanguageExpressionScore;
    private Integer autoSourceCoverageScore;
    private Double autoTotalScore;
    private Double factVerificationRate;
    private String recommendations;
    private String userFeedback;
    private OffsetDateTime evaluationTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getPageId() {
        return pageId;
    }

    public void setPageId(Long pageId) {
        this.pageId = pageId;
    }

    public Integer getOutlineLogicScore() {
        return outlineLogicScore;
    }

    public void setOutlineLogicScore(Integer outlineLogicScore) {
        this.outlineLogicScore = outlineLogicScore;
    }

    public Integer getFactualAccuracyScore() {
        return factualAccuracyScore;
    }

    public void setFactualAccuracyScore(Integer factualAccuracyScore) {
        this.factualAccuracyScore = factualAccuracyScore;
    }

    public Integer getInfoDensityScore() {
        return infoDensityScore;
    }

    public void setInfoDensityScore(Integer infoDensityScore) {
        this.infoDensityScore = infoDensityScore;
    }

    public Integer getLanguageExpressionScore() {
        return languageExpressionScore;
    }

    public void setLanguageExpressionScore(Integer languageExpressionScore) {
        this.languageExpressionScore = languageExpressionScore;
    }

    public Double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getAutoOutlineLogicScore() {
        return autoOutlineLogicScore;
    }

    public void setAutoOutlineLogicScore(Integer autoOutlineLogicScore) {
        this.autoOutlineLogicScore = autoOutlineLogicScore;
    }

    public Integer getAutoInfoDensityScore() {
        return autoInfoDensityScore;
    }

    public void setAutoInfoDensityScore(Integer autoInfoDensityScore) {
        this.autoInfoDensityScore = autoInfoDensityScore;
    }

    public Integer getAutoFactualAccuracyScore() {
        return autoFactualAccuracyScore;
    }

    public void setAutoFactualAccuracyScore(Integer autoFactualAccuracyScore) {
        this.autoFactualAccuracyScore = autoFactualAccuracyScore;
    }

    public Integer getAutoLanguageExpressionScore() {
        return autoLanguageExpressionScore;
    }

    public void setAutoLanguageExpressionScore(Integer autoLanguageExpressionScore) {
        this.autoLanguageExpressionScore = autoLanguageExpressionScore;
    }

    public Integer getAutoSourceCoverageScore() {
        return autoSourceCoverageScore;
    }

    public void setAutoSourceCoverageScore(Integer autoSourceCoverageScore) {
        this.autoSourceCoverageScore = autoSourceCoverageScore;
    }

    public Double getAutoTotalScore() {
        return autoTotalScore;
    }

    public void setAutoTotalScore(Double autoTotalScore) {
        this.autoTotalScore = autoTotalScore;
    }

    public Double getFactVerificationRate() {
        return factVerificationRate;
    }

    public void setFactVerificationRate(Double factVerificationRate) {
        this.factVerificationRate = factVerificationRate;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public String getUserFeedback() {
        return userFeedback;
    }

    public void setUserFeedback(String userFeedback) {
        this.userFeedback = userFeedback;
    }

    public OffsetDateTime getEvaluationTime() {
        return evaluationTime;
    }

    public void setEvaluationTime(OffsetDateTime evaluationTime) {
        this.evaluationTime = evaluationTime;
    }
}
