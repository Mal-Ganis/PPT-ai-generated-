package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class EvaluationDashboardResponse {

    private int projectCount;
    private int reportCount;
    private Double avgAutoTotalScore;
    private Double avgFactVerificationRate;
    private int qualityGatePassCount;
    private int qualityGateWarnCount;
    private int qualityGateFailCount;
    private int calibrationTotal;
    private int calibrationAgreeCount;
    private int calibrationDisagreeCount;
    private List<EvaluationProjectSnapshotDto> recentProjects = new ArrayList<>();
    private List<EvaluationCalibrationRecordDto> calibrationRecords = new ArrayList<>();

    public int getProjectCount() {
        return projectCount;
    }

    public void setProjectCount(int projectCount) {
        this.projectCount = projectCount;
    }

    public int getReportCount() {
        return reportCount;
    }

    public void setReportCount(int reportCount) {
        this.reportCount = reportCount;
    }

    public Double getAvgAutoTotalScore() {
        return avgAutoTotalScore;
    }

    public void setAvgAutoTotalScore(Double avgAutoTotalScore) {
        this.avgAutoTotalScore = avgAutoTotalScore;
    }

    public Double getAvgFactVerificationRate() {
        return avgFactVerificationRate;
    }

    public void setAvgFactVerificationRate(Double avgFactVerificationRate) {
        this.avgFactVerificationRate = avgFactVerificationRate;
    }

    public int getQualityGatePassCount() {
        return qualityGatePassCount;
    }

    public void setQualityGatePassCount(int qualityGatePassCount) {
        this.qualityGatePassCount = qualityGatePassCount;
    }

    public int getQualityGateWarnCount() {
        return qualityGateWarnCount;
    }

    public void setQualityGateWarnCount(int qualityGateWarnCount) {
        this.qualityGateWarnCount = qualityGateWarnCount;
    }

    public int getQualityGateFailCount() {
        return qualityGateFailCount;
    }

    public void setQualityGateFailCount(int qualityGateFailCount) {
        this.qualityGateFailCount = qualityGateFailCount;
    }

    public int getCalibrationTotal() {
        return calibrationTotal;
    }

    public void setCalibrationTotal(int calibrationTotal) {
        this.calibrationTotal = calibrationTotal;
    }

    public int getCalibrationAgreeCount() {
        return calibrationAgreeCount;
    }

    public void setCalibrationAgreeCount(int calibrationAgreeCount) {
        this.calibrationAgreeCount = calibrationAgreeCount;
    }

    public int getCalibrationDisagreeCount() {
        return calibrationDisagreeCount;
    }

    public void setCalibrationDisagreeCount(int calibrationDisagreeCount) {
        this.calibrationDisagreeCount = calibrationDisagreeCount;
    }

    public List<EvaluationProjectSnapshotDto> getRecentProjects() {
        return recentProjects;
    }

    public void setRecentProjects(List<EvaluationProjectSnapshotDto> recentProjects) {
        this.recentProjects = recentProjects;
    }

    public List<EvaluationCalibrationRecordDto> getCalibrationRecords() {
        return calibrationRecords;
    }

    public void setCalibrationRecords(List<EvaluationCalibrationRecordDto> calibrationRecords) {
        this.calibrationRecords = calibrationRecords;
    }
}
