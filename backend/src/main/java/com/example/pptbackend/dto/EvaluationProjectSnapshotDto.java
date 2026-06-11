package com.example.pptbackend.dto;

import java.time.OffsetDateTime;

public class EvaluationProjectSnapshotDto {

    private Long projectId;
    private String projectTitle;
    private Long latestReportId;
    private Double autoTotalScore;
    private Integer autoSourceCoverageScore;
    private String qualityGateStatus;
    private OffsetDateTime evaluationTime;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public Long getLatestReportId() {
        return latestReportId;
    }

    public void setLatestReportId(Long latestReportId) {
        this.latestReportId = latestReportId;
    }

    public Double getAutoTotalScore() {
        return autoTotalScore;
    }

    public void setAutoTotalScore(Double autoTotalScore) {
        this.autoTotalScore = autoTotalScore;
    }

    public Integer getAutoSourceCoverageScore() {
        return autoSourceCoverageScore;
    }

    public void setAutoSourceCoverageScore(Integer autoSourceCoverageScore) {
        this.autoSourceCoverageScore = autoSourceCoverageScore;
    }

    public String getQualityGateStatus() {
        return qualityGateStatus;
    }

    public void setQualityGateStatus(String qualityGateStatus) {
        this.qualityGateStatus = qualityGateStatus;
    }

    public OffsetDateTime getEvaluationTime() {
        return evaluationTime;
    }

    public void setEvaluationTime(OffsetDateTime evaluationTime) {
        this.evaluationTime = evaluationTime;
    }
}
