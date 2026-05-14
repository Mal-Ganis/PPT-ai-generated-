package com.example.pptbackend.controller;

import com.example.pptbackend.dto.CreateEvaluationReportRequest;
import com.example.pptbackend.dto.EvaluationCalibrationRequest;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.service.EvaluationReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId:\\d+}/evaluations")
@CrossOrigin(origins = "http://localhost:5173")
public class EvaluationReportController {

    private final EvaluationReportService evaluationReportService;

    public EvaluationReportController(EvaluationReportService evaluationReportService) {
        this.evaluationReportService = evaluationReportService;
    }

    @PostMapping
    public ResponseEntity<Long> createEvaluation(@PathVariable("projectId") Long projectId,
                                                 @RequestBody CreateEvaluationReportRequest request) {
        Long reportId = evaluationReportService.createEvaluationReport(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reportId);
    }

    @GetMapping
    public ResponseEntity<List<EvaluationReportResponse>> getEvaluations(@PathVariable("projectId") Long projectId) {
        List<EvaluationReportResponse> reports = evaluationReportService.getReportsForProject(projectId);
        return ResponseEntity.ok(reports);
    }

    /** 拇指校准：对齐自动启发式分项或记录不认同 */
    @PostMapping("/calibrate")
    public ResponseEntity<Long> calibrate(@PathVariable("projectId") Long projectId,
                                          @RequestBody EvaluationCalibrationRequest request) {
        Long id = evaluationReportService.createCalibrationReport(
            projectId,
            request.isAgreeWithAuto(),
            request.getNote());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }
}
