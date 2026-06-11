package com.example.pptbackend.service;

import com.example.pptbackend.model.EvaluationReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationQualityGateService {

    private final ObjectMapper objectMapper;

    /** 自动总分 ≥ 此值：门禁通过 */
    private final double passMinDefault;

    /** 自动总分 ≥ 此值且 < passMin：预警；低于此值：未通过 */
    private final double warnMinDefault;

    public EvaluationQualityGateService(
        ObjectMapper objectMapper,
        @Value("${evaluation.quality-gate-pass-min:70}") double passMinDefault,
        @Value("${evaluation.quality-gate-warn-min:50}") double warnMinDefault) {
        this.objectMapper = objectMapper;
        this.passMinDefault = passMinDefault;
        this.warnMinDefault = warnMinDefault;
    }

    public void applyQualityGate(EvaluationReport report) {
        Double autoTotal = report.getAutoTotalScore();
        List<String> reasons = new ArrayList<>();

        if (autoTotal == null) {
            report.setQualityGateStatus("UNKNOWN");
            reasons.add("尚无自动评估总分");
            writeReasons(report, reasons);
            return;
        }

        double passMin = passMinDefault;
        double warnMin = warnMinDefault;

        if (autoTotal >= passMin) {
            report.setQualityGateStatus("PASS");
            reasons.add(String.format("自动总分 %.1f ≥ %.0f 分（通过）", autoTotal, passMin));
        } else if (autoTotal >= warnMin) {
            report.setQualityGateStatus("WARN");
            reasons.add(String.format("自动总分 %.1f 处于预警区间（%.0f–%.0f 分）", autoTotal, warnMin, passMin - 1));
            reasons.add(String.format("达到 %.0f 分即为通过", passMin));
        } else {
            report.setQualityGateStatus("FAIL");
            reasons.add(String.format("自动总分 %.1f 低于 %.0f 分（未进入预警区间）", autoTotal, warnMin));
            reasons.add(String.format("%.0f–%.0f 分为预警，%.0f 分以上通过", warnMin, passMin - 1, passMin));
            reasons.add("建议补充引用、提高要点密度或重新生成薄弱页");
        }

        writeReasons(report, reasons);
    }

    private void writeReasons(EvaluationReport report, List<String> reasons) {
        try {
            report.setQualityGateReasons(objectMapper.writeValueAsString(reasons));
        } catch (JsonProcessingException e) {
            report.setQualityGateReasons("[]");
        }
    }
}
