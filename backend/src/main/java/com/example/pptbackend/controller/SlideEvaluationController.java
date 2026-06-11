package com.example.pptbackend.controller;

import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.service.EvaluationReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId:\\d+}/slides/{slideId:\\d+}/evaluations")
@CrossOrigin(origins = "http://localhost:5173")
public class SlideEvaluationController {

    private final EvaluationReportService evaluationReportService;

    public SlideEvaluationController(EvaluationReportService evaluationReportService) {
        this.evaluationReportService = evaluationReportService;
    }

    /** 单页（pageId）自动评估 + LLM 改进建议 */
    @PostMapping
    public ResponseEntity<EvaluationReportResponse> evaluateSlide(@PathVariable("projectId") Long projectId,
                                                                  @PathVariable("slideId") Long slideId) {
        EvaluationReportResponse response = evaluationReportService.createPageEvaluationReport(projectId, slideId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
