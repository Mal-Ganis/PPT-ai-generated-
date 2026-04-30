package com.example.pptbackend.dto;

import java.time.OffsetDateTime;

public class CreateEvaluationReportRequest {

    private Long pageId;
    private Integer outlineLogicScore;
    private Integer factualAccuracyScore;
    private Integer infoDensityScore;
    private Integer languageExpressionScore;
    private String recommendations;
    private String userFeedback;
    private OffsetDateTime evaluationTime;

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
