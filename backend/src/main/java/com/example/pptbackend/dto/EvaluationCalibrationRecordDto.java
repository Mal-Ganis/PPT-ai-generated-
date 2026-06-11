package com.example.pptbackend.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class EvaluationCalibrationRecordDto {

    private Long reportId;
    private Long projectId;
    private Boolean agreeWithAuto;
    private Map<String, Integer> deltaFromAuto;
    private String userFeedback;
    private OffsetDateTime evaluationTime;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Boolean getAgreeWithAuto() {
        return agreeWithAuto;
    }

    public void setAgreeWithAuto(Boolean agreeWithAuto) {
        this.agreeWithAuto = agreeWithAuto;
    }

    public Map<String, Integer> getDeltaFromAuto() {
        return deltaFromAuto;
    }

    public void setDeltaFromAuto(Map<String, Integer> deltaFromAuto) {
        this.deltaFromAuto = deltaFromAuto;
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
