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
@Table(name = "evaluation_reports")
public class EvaluationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column
    private Long pageId;

    @Column(nullable = false)
    private Integer outlineLogicScore;

    /** 已停用事实准确人工分；保留列兼容旧数据，新报告可为 null */
    @Column
    private Integer factualAccuracyScore;

    @Column(nullable = false)
    private Integer infoDensityScore;

    @Column(nullable = false)
    private Integer languageExpressionScore;

    @Column(nullable = false)
    private Double totalScore;

    /** 自动评估：结构完整性 / 逻辑（启发式） */
    @Column
    private Integer autoOutlineLogicScore;

    @Column
    private Integer autoInfoDensityScore;

    @Column
    private Integer autoFactualAccuracyScore;

    @Column
    private Integer autoLanguageExpressionScore;

    /** 有引用来源的幻灯片占比得分 */
    @Column
    private Integer autoSourceCoverageScore;

    /** 自动加权总分（各自动维度） */
    @Column
    private Double autoTotalScore;

    /** 事实语义抽检：新规为 0–1（证据支持度均值）；旧数据可能仍为词重叠 0–100 */
    @Column
    private Double factVerificationRate;

    /** JSON：FactCheckDetailDto[] */
    @Column(columnDefinition = "TEXT")
    private String factCheckDetails;

    /** PASS | WARN | FAIL | UNKNOWN */
    @Column(length = 16)
    private String qualityGateStatus;

    /** JSON：string[] */
    @Column(columnDefinition = "TEXT")
    private String qualityGateReasons;

    /** 拇指校准时是否认同自动分 */
    @Column
    private Boolean calibrationAgreeWithAuto;

    /** JSON：人工分与自动分的差值（校准回流） */
    @Column(columnDefinition = "TEXT")
    private String calibrationDeltaJson;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(columnDefinition = "TEXT")
    private String userFeedback;

    @Column(nullable = false)
    private OffsetDateTime evaluationTime;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

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

    public String getFactCheckDetails() {
        return factCheckDetails;
    }

    public void setFactCheckDetails(String factCheckDetails) {
        this.factCheckDetails = factCheckDetails;
    }

    public String getQualityGateStatus() {
        return qualityGateStatus;
    }

    public void setQualityGateStatus(String qualityGateStatus) {
        this.qualityGateStatus = qualityGateStatus;
    }

    public String getQualityGateReasons() {
        return qualityGateReasons;
    }

    public void setQualityGateReasons(String qualityGateReasons) {
        this.qualityGateReasons = qualityGateReasons;
    }

    public Boolean getCalibrationAgreeWithAuto() {
        return calibrationAgreeWithAuto;
    }

    public void setCalibrationAgreeWithAuto(Boolean calibrationAgreeWithAuto) {
        this.calibrationAgreeWithAuto = calibrationAgreeWithAuto;
    }

    public String getCalibrationDeltaJson() {
        return calibrationDeltaJson;
    }

    public void setCalibrationDeltaJson(String calibrationDeltaJson) {
        this.calibrationDeltaJson = calibrationDeltaJson;
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
        if (this.evaluationTime == null) {
            this.evaluationTime = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
