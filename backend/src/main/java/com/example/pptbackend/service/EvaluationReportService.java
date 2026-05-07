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
    private final AutoEvaluationScoringService autoEvaluationScoringService;

    public EvaluationReportService(EvaluationReportRepository evaluationReportRepository,
                                   ProjectRepository projectRepository,
                                   AutoEvaluationScoringService autoEvaluationScoringService) {
        this.evaluationReportRepository = evaluationReportRepository;
        this.projectRepository = projectRepository;
        this.autoEvaluationScoringService = autoEvaluationScoringService;
    }

    /**
     * P3.1：生成流程结束后写入一条「仅占位人工分」的自动评估记录，便于前端展示 autoTotalScore。
     */
    @Transactional
    public Long createAutoEvaluationReport(Long projectId) {
        return createAutoEvaluationReportAndReturn(projectId).getId();
    }

    @Transactional
    public EvaluationReportResponse createAutoEvaluationReportAndReturn(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }
        EvaluationReport report = new EvaluationReport();
        report.setProjectId(projectId);
        int neutral = 50;
        report.setOutlineLogicScore(neutral);
        report.setFactualAccuracyScore(neutral);
        report.setInfoDensityScore(neutral);
        report.setLanguageExpressionScore(neutral);
        report.setRecommendations("系统自动评估（人工维度为中性占位，请看自动分项）。");
        report.setUserFeedback("");
        autoEvaluationScoringService.attachAutoScores(projectId, report);
        report.setTotalScore(computeWeightedTotal(report));
        EvaluationReport saved = evaluationReportRepository.save(report);
        return toResponse(saved);
    }

    /**
     * 将最新一条自动评估的启发式分同步为「人工分」，或记录用户对自动分的不认同。
     */
    @Transactional
    public Long createCalibrationReport(Long projectId, boolean agreeWithAuto, String note) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }
        List<EvaluationReport> list = evaluationReportRepository.findByProjectIdOrderByEvaluationTimeDesc(projectId);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("No evaluation report yet for this project");
        }
        EvaluationReport latest = list.get(0);
        EvaluationReport r = new EvaluationReport();
        r.setProjectId(projectId);
        if (agreeWithAuto) {
            r.setOutlineLogicScore(clampScore(latest.getAutoOutlineLogicScore(), latest.getOutlineLogicScore()));
            r.setFactualAccuracyScore(clampScore(latest.getAutoFactualAccuracyScore(), latest.getFactualAccuracyScore()));
            r.setInfoDensityScore(clampScore(latest.getAutoInfoDensityScore(), latest.getInfoDensityScore()));
            r.setLanguageExpressionScore(clampScore(latest.getAutoLanguageExpressionScore(), latest.getLanguageExpressionScore()));
            r.setRecommendations("拇指校准：人工分数已与最新自动启发式分项对齐。");
            r.setUserFeedback(note != null && !note.isBlank() ? note.trim() : "认同当前自动评估分项");
        } else {
            int low = 48;
            r.setOutlineLogicScore(low);
            r.setFactualAccuracyScore(low);
            r.setInfoDensityScore(low);
            r.setLanguageExpressionScore(low);
            r.setRecommendations("拇指校准：用户对自动分存在疑虑（未对齐启发式分），仅供参考。");
            r.setUserFeedback(note != null && !note.isBlank() ? note.trim() : "认为自动评估与主观感受偏差较大");
        }
        autoEvaluationScoringService.attachAutoScores(projectId, r);
        r.setTotalScore(computeWeightedTotal(r));
        return evaluationReportRepository.save(r).getId();
    }

    private static int clampScore(Integer auto, Integer humanFallback) {
        if (auto != null) {
            return Math.max(0, Math.min(100, auto));
        }
        return humanFallback != null ? humanFallback : 50;
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
        autoEvaluationScoringService.attachAutoScores(projectId, report);
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
        response.setAutoOutlineLogicScore(report.getAutoOutlineLogicScore());
        response.setAutoInfoDensityScore(report.getAutoInfoDensityScore());
        response.setAutoFactualAccuracyScore(report.getAutoFactualAccuracyScore());
        response.setAutoLanguageExpressionScore(report.getAutoLanguageExpressionScore());
        response.setAutoSourceCoverageScore(report.getAutoSourceCoverageScore());
        response.setAutoTotalScore(report.getAutoTotalScore());
        response.setFactVerificationRate(report.getFactVerificationRate());
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
