package com.example.pptbackend.service;

import com.example.pptbackend.dto.CreateEvaluationReportRequest;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.model.EvaluationReport;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.repository.EvaluationReportRepository;
import com.example.pptbackend.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EvaluationReportService {

    private final EvaluationReportRepository evaluationReportRepository;
    private final ProjectRepository projectRepository;

    public EvaluationReportService(EvaluationReportRepository evaluationReportRepository,
                                   ProjectRepository projectRepository) {
        this.evaluationReportRepository = evaluationReportRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Long createEvaluationReport(Long projectId, CreateEvaluationReportRequest request) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));

        EvaluationReport report = new EvaluationReport();
        report.setProjectId(project.getId());
        report.setPageId(request.getPageId());
        report.setOutlineLogicScore(requireScore(request.getOutlineLogicScore(), "outlineLogicScore"));
        report.setFactualAccuracyScore(requireScore(request.getFactualAccuracyScore(), "factualAccuracyScore"));
        report.setInfoDensityScore(requireScore(request.getInfoDensityScore(), "infoDensityScore"));
        report.setLanguageExpressionScore(requireScore(request.getLanguageExpressionScore(), "languageExpressionScore"));
        report.setRecommendations(request.getRecommendations());
        report.setUserFeedback(request.getUserFeedback());
        report.setEvaluationTime(request.getEvaluationTime() != null ? request.getEvaluationTime() : java.time.OffsetDateTime.now());
        report.setTotalScore(computeWeightedTotal(report));

        return evaluationReportRepository.save(report).getId();
    }

    @Transactional(readOnly = true)
    public List<EvaluationReportResponse> getReportsForProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }

        return evaluationReportRepository.findByProjectIdOrderByEvaluationTimeDesc(projectId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private EvaluationReportResponse toResponse(EvaluationReport report) {
        EvaluationReportResponse response = new EvaluationReportResponse();
        response.setId(report.getId());
        response.setProjectId(report.getProjectId());
        response.setPageId(report.getPageId());
        response.setOutlineLogicScore(report.getOutlineLogicScore());
        response.setFactualAccuracyScore(report.getFactualAccuracyScore());
        response.setInfoDensityScore(report.getInfoDensityScore());
        response.setLanguageExpressionScore(report.getLanguageExpressionScore());
        response.setTotalScore(report.getTotalScore());
        response.setRecommendations(report.getRecommendations());
        response.setUserFeedback(report.getUserFeedback());
        response.setEvaluationTime(report.getEvaluationTime());
        return response;
    }

    private Integer requireScore(Integer score, String name) {
        if (score == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
        return score;
    }

    private Double computeWeightedTotal(EvaluationReport report) {
        double outlineWeight = 0.3;
        double factualWeight = 0.3;
        double densityWeight = 0.2;
        double languageWeight = 0.2;

        return report.getOutlineLogicScore() * outlineWeight
            + report.getFactualAccuracyScore() * factualWeight
            + report.getInfoDensityScore() * densityWeight
            + report.getLanguageExpressionScore() * languageWeight;
    }
}
