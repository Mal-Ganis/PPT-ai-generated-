package com.example.pptbackend.dto;

/**
 * 拇指校准：认同自动分时将人工分对齐到自动启发式分；不认同时写入一条中性偏低记录并附说明。
 */
public class EvaluationCalibrationRequest {

    private boolean agreeWithAuto;
    private String note;

    public boolean isAgreeWithAuto() {
        return agreeWithAuto;
    }

    public void setAgreeWithAuto(boolean agreeWithAuto) {
        this.agreeWithAuto = agreeWithAuto;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
