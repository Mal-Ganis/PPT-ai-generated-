package com.example.pptbackend.dto;

/**
 * 事实抽检单条明细（语义或词重叠）。
 */
public class FactCheckDetailDto {

    private String statement;
    private String evidence;
    /** 0~1 支持度 */
    private Double supportScore;
    private Boolean passed;
    /** semantic | overlap | skipped */
    private String method;
    private String slideTitle;
    /** 未通过时的说明，如「仅领域相关，未覆盖关键数据」 */
    private String evidenceNote;
    /** 写入 factVerificationRate 汇总的分值（0~1），与 UI 展示的 supportScore 可不同 */
    private Double rateContribution;

    public Double getRateContribution() {
        return rateContribution;
    }

    public void setRateContribution(Double rateContribution) {
        this.rateContribution = rateContribution;
    }

    public String getEvidenceNote() {
        return evidenceNote;
    }

    public void setEvidenceNote(String evidenceNote) {
        this.evidenceNote = evidenceNote;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public Double getSupportScore() {
        return supportScore;
    }

    public void setSupportScore(Double supportScore) {
        this.supportScore = supportScore;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getSlideTitle() {
        return slideTitle;
    }

    public void setSlideTitle(String slideTitle) {
        this.slideTitle = slideTitle;
    }
}
