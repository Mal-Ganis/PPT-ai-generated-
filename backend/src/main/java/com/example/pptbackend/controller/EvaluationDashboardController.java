package com.example.pptbackend.controller;

import com.example.pptbackend.dto.EvaluationDashboardResponse;
import com.example.pptbackend.service.EvaluationReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
@CrossOrigin(origins = "http://localhost:5173")
public class EvaluationDashboardController {

    private final EvaluationReportService evaluationReportService;

    public EvaluationDashboardController(EvaluationReportService evaluationReportService) {
        this.evaluationReportService = evaluationReportService;
    }

    /** 跨项目质量看板 + 校准回流摘要 */
    @GetMapping("/dashboard")
    public ResponseEntity<EvaluationDashboardResponse> dashboard() {
        return ResponseEntity.ok(evaluationReportService.getDashboard());
    }
}
