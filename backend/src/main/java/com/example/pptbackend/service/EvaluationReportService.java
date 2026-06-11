package com.example.pptbackend.service;

import com.example.pptbackend.dto.CreateEvaluationReportRequest;
import com.example.pptbackend.dto.EvaluationCalibrationRecordDto;
import com.example.pptbackend.dto.EvaluationDashboardResponse;
import com.example.pptbackend.dto.EvaluationProjectSnapshotDto;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.FactCheckDetailDto;
import com.example.pptbackend.model.EvaluationReport;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.EvaluationReportRepository;
import com.example.pptbackend.repository.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EvaluationReportService {

    private final EvaluationReportRepository evaluationReportRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final AutoEvaluationScoringService autoEvaluationScoringService;
    private final EvaluationQualityGateService evaluationQualityGateService;
    private final EvaluationRecommendationService evaluationRecommendationService;
    private final ObjectMapper objectMapper;

    public EvaluationReportService(EvaluationReportRepository evaluationReportRepository,
                                   ProjectRepository projectRepository,
                                   ProjectAccessService projectAccessService,
                                   AutoEvaluationScoringService autoEvaluationScoringService,
                                   EvaluationQualityGateService evaluationQualityGateService,
                                   EvaluationRecommendationService evaluationRecommendationService,
                                   ObjectMapper objectMapper) {
        this.evaluationReportRepository = evaluationReportRepository;
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
        this.autoEvaluationScoringService = autoEvaluationScoringService;
        this.evaluationQualityGateService = evaluationQualityGateService;
        this.evaluationRecommendationService = evaluationRecommendationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long createAutoEvaluationReport(Long projectId) {
        return createAutoEvaluationReportAndReturn(projectId).getId();
    }

    @Transactional
    public EvaluationReportResponse createAutoEvaluationReportAndReturn(Long projectId) {
        Project project = projectAccessService.requireReadableProject(projectId);
        projectAccessService.assertWritable(project);
        EvaluationReport report = new EvaluationReport();
        report.setProjectId(projectId);
        int neutral = 50;
        report.setOutlineLogicScore(neutral);
        report.setFactualAccuracyScore(null);
        report.setInfoDensityScore(neutral);
        report.setLanguageExpressionScore(neutral);
        report.setUserFeedback("");
        enrichAndPersist(project, report);
        return toResponse(report);
    }

    @Transactional
    public EvaluationReportResponse createPageEvaluationReport(Long projectId, Long slideId) {
        Project project = projectAccessService.requireReadableProject(projectId);
        projectAccessService.assertWritable(project);
        Slide slide = project.getSlides().stream()
            .filter(s -> slideId.equals(s.getId()))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));

        EvaluationReport report = new EvaluationReport();
        report.setProjectId(projectId);
        report.setPageId(slideId);
        int neutral = 50;
        report.setOutlineLogicScore(neutral);
        report.setFactualAccuracyScore(null);
        report.setInfoDensityScore(neutral);
        report.setLanguageExpressionScore(neutral);
        report.setUserFeedback("");
        enrichAndPersist(project, report);
        return toResponse(report);
    }

    @Transactional
    public Long createCalibrationReport(Long projectId, boolean agreeWithAuto, String note) {
        Project project = projectAccessService.requireReadableProject(projectId);
        projectAccessService.assertWritable(project);
        List<EvaluationReport> list = evaluationReportRepository.findByProjectIdOrderByEvaluationTimeDesc(projectId);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("No evaluation report yet for this project");
        }
        EvaluationReport latest = list.get(0);
        EvaluationReport r = new EvaluationReport();
        r.setProjectId(projectId);
        r.setPageId(latest.getPageId());
        Map<String, Integer> delta = new LinkedHashMap<>();
        if (agreeWithAuto) {
            r.setOutlineLogicScore(clampScore(latest.getAutoOutlineLogicScore(), latest.getOutlineLogicScore()));
            r.setFactualAccuracyScore(clampScore(latest.getAutoFactualAccuracyScore(), latest.getFactualAccuracyScore()));
            r.setInfoDensityScore(clampScore(latest.getAutoInfoDensityScore(), latest.getInfoDensityScore()));
            r.setLanguageExpressionScore(clampScore(latest.getAutoLanguageExpressionScore(), latest.getLanguageExpressionScore()));
            r.setUserFeedback(note != null && !note.isBlank() ? note.trim() : "认同当前自动评估分项");
            delta.put("outline", 0);
            delta.put("factual", 0);
            delta.put("density", 0);
            delta.put("language", 0);
        } else {
            int low = 48;
            r.setOutlineLogicScore(low);
            r.setFactualAccuracyScore(low);
            r.setInfoDensityScore(low);
            r.setLanguageExpressionScore(low);
            r.setUserFeedback(note != null && !note.isBlank() ? note.trim() : "认为自动评估与主观感受偏差较大");
            delta.put("outline", low - autoOr(latest.getAutoOutlineLogicScore(), 50));
            delta.put("factual", low - autoOr(latest.getAutoFactualAccuracyScore(), 50));
            delta.put("density", low - autoOr(latest.getAutoInfoDensityScore(), 50));
            delta.put("language", low - autoOr(latest.getAutoLanguageExpressionScore(), 50));
        }
        r.setCalibrationAgreeWithAuto(agreeWithAuto);
        writeCalibrationDelta(r, delta);

        enrichAndPersist(project, r);
        if (agreeWithAuto) {
            r.setRecommendations("拇指校准：人工分数已与最新自动启发式分项对齐。");
        } else {
            r.setRecommendations("拇指校准：用户对自动分存在疑虑（未对齐启发式分），请参考 LLM 改进建议。");
        }
        evaluationReportRepository.save(r);
        return r.getId();
    }

    @Transactional
    public Long createEvaluationReport(Long projectId, CreateEvaluationReportRequest request) {
        Project project = projectAccessService.requireReadableProject(projectId);
        projectAccessService.assertWritable(project);

        EvaluationReport report = new EvaluationReport();
        report.setProjectId(project.getId());
        report.setPageId(request.getPageId());
        report.setOutlineLogicScore(requireScore(request.getOutlineLogicScore(), "outlineLogicScore"));
        if (request.getFactualAccuracyScore() != null) {
            report.setFactualAccuracyScore(requireScore(request.getFactualAccuracyScore(), "factualAccuracyScore"));
        }
        report.setInfoDensityScore(requireScore(request.getInfoDensityScore(), "infoDensityScore"));
        report.setLanguageExpressionScore(requireScore(request.getLanguageExpressionScore(), "languageExpressionScore"));
        if (request.getRecommendations() != null && !request.getRecommendations().isBlank()) {
            report.setRecommendations(request.getRecommendations());
        }
        report.setUserFeedback(request.getUserFeedback());
        report.setEvaluationTime(request.getEvaluationTime() != null ? request.getEvaluationTime() : java.time.OffsetDateTime.now());
        enrichAndPersist(project, report);
        if (request.getRecommendations() != null && !request.getRecommendations().isBlank()) {
            report.setRecommendations(request.getRecommendations());
            evaluationReportRepository.save(report);
        }
        return report.getId();
    }

    @Transactional(readOnly = true)
    public List<EvaluationReportResponse> getReportsForProject(Long projectId) {
        projectAccessService.requireReadableProject(projectId);
        return evaluationReportRepository.findByProjectIdOrderByEvaluationTimeDesc(projectId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EvaluationDashboardResponse getDashboard() {
        java.util.Set<Long> visibleProjectIds = projectAccessService.listProjectsForCurrentUser().stream()
            .map(Project::getId)
            .collect(Collectors.toSet());
        List<EvaluationReport> all = evaluationReportRepository.findAllByOrderByEvaluationTimeDesc().stream()
            .filter(report -> visibleProjectIds.contains(report.getProjectId()))
            .toList();
        EvaluationDashboardResponse dashboard = new EvaluationDashboardResponse();
        dashboard.setReportCount(all.size());

        Map<Long, EvaluationReport> latestByProject = new LinkedHashMap<>();
        for (EvaluationReport report : all) {
            if (report.getPageId() != null) {
                continue;
            }
            latestByProject.putIfAbsent(report.getProjectId(), report);
        }
        dashboard.setProjectCount(latestByProject.size());

        double autoSum = 0;
        int autoCount = 0;
        double sourceSum = 0;
        int sourceCount = 0;
        int pass = 0;
        int warn = 0;
        int fail = 0;

        List<EvaluationProjectSnapshotDto> snapshots = new ArrayList<>();
        for (EvaluationReport report : latestByProject.values()) {
            Project project = projectRepository.findById(report.getProjectId()).orElse(null);
            EvaluationProjectSnapshotDto snap = new EvaluationProjectSnapshotDto();
            snap.setProjectId(report.getProjectId());
            snap.setProjectTitle(project != null && project.getTitle() != null ? project.getTitle() : "项目 " + report.getProjectId());
            snap.setLatestReportId(report.getId());
            snap.setAutoTotalScore(report.getAutoTotalScore());
            snap.setAutoSourceCoverageScore(report.getAutoSourceCoverageScore());
            snap.setQualityGateStatus(report.getQualityGateStatus());
            snap.setEvaluationTime(report.getEvaluationTime());
            snapshots.add(snap);

            if (report.getAutoTotalScore() != null) {
                autoSum += report.getAutoTotalScore();
                autoCount++;
            }
            if (report.getAutoSourceCoverageScore() != null) {
                sourceSum += report.getAutoSourceCoverageScore();
                sourceCount++;
            }
            String gate = report.getQualityGateStatus();
            if ("PASS".equals(gate)) {
                pass++;
            } else if ("WARN".equals(gate)) {
                warn++;
            } else if ("FAIL".equals(gate)) {
                fail++;
            }
        }
        snapshots.sort(Comparator.comparing(EvaluationProjectSnapshotDto::getEvaluationTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        if (snapshots.size() > 12) {
            snapshots = snapshots.subList(0, 12);
        }
        dashboard.setRecentProjects(snapshots);
        dashboard.setAvgAutoTotalScore(autoCount > 0 ? autoSum / autoCount : null);
        dashboard.setAvgFactVerificationRate(sourceCount > 0 ? sourceSum / sourceCount : null);
        dashboard.setQualityGatePassCount(pass);
        dashboard.setQualityGateWarnCount(warn);
        dashboard.setQualityGateFailCount(fail);

        List<EvaluationReport> calibrations =
            evaluationReportRepository.findByCalibrationAgreeWithAutoIsNotNullOrderByEvaluationTimeDesc().stream()
                .filter(c -> visibleProjectIds.contains(c.getProjectId()))
                .toList();
        dashboard.setCalibrationTotal(calibrations.size());
        int agree = 0;
        List<EvaluationCalibrationRecordDto> calRecords = new ArrayList<>();
        for (EvaluationReport c : calibrations) {
            if (Boolean.TRUE.equals(c.getCalibrationAgreeWithAuto())) {
                agree++;
            }
            EvaluationCalibrationRecordDto rec = new EvaluationCalibrationRecordDto();
            rec.setReportId(c.getId());
            rec.setProjectId(c.getProjectId());
            rec.setAgreeWithAuto(c.getCalibrationAgreeWithAuto());
            rec.setDeltaFromAuto(parseDeltaMap(c.getCalibrationDeltaJson()));
            rec.setUserFeedback(c.getUserFeedback());
            rec.setEvaluationTime(c.getEvaluationTime());
            calRecords.add(rec);
            if (calRecords.size() >= 20) {
                break;
            }
        }
        dashboard.setCalibrationAgreeCount(agree);
        dashboard.setCalibrationDisagreeCount(calibrations.size() - agree);
        dashboard.setCalibrationRecords(calRecords);
        return dashboard;
    }

    private void enrichAndPersist(Project project, EvaluationReport report) {
        autoEvaluationScoringService.attachAutoScores(project.getId(), report, report.getPageId());
        evaluationQualityGateService.applyQualityGate(report);
        if (report.getRecommendations() == null || report.getRecommendations().isBlank()) {
            report.setRecommendations(evaluationRecommendationService.generateRecommendations(project, report));
        }
        report.setTotalScore(computeWeightedTotal(report));
        evaluationReportRepository.save(report);
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
        response.setFactCheckDetails(parseFactCheckDetails(report.getFactCheckDetails()));
        response.setQualityGateStatus(report.getQualityGateStatus());
        response.setQualityGateReasons(parseStringList(report.getQualityGateReasons()));
        response.setCalibrationAgreeWithAuto(report.getCalibrationAgreeWithAuto());
        response.setCalibrationDeltaFromAuto(parseDeltaMap(report.getCalibrationDeltaJson()));
        response.setRecommendations(report.getRecommendations());
        response.setUserFeedback(report.getUserFeedback());
        response.setEvaluationTime(report.getEvaluationTime());
        return response;
    }

    private List<FactCheckDetailDto> parseFactCheckDetails(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Integer> parseDeltaMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void writeCalibrationDelta(EvaluationReport report, Map<String, Integer> delta) {
        try {
            report.setCalibrationDeltaJson(objectMapper.writeValueAsString(delta));
        } catch (Exception e) {
            report.setCalibrationDeltaJson("{}");
        }
    }

    private static int autoOr(Integer auto, int fallback) {
        return auto != null ? auto : fallback;
    }

    private static int clampScore(Integer auto, Integer humanFallback) {
        if (auto != null) {
            return Math.max(0, Math.min(100, auto));
        }
        return humanFallback != null ? humanFallback : 50;
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
        double outlineWeight = 0.35;
        double densityWeight = 0.35;
        double languageWeight = 0.30;

        return nz(report.getOutlineLogicScore()) * outlineWeight
            + nz(report.getInfoDensityScore()) * densityWeight
            + nz(report.getLanguageExpressionScore()) * languageWeight;
    }

    private static int nz(Integer score) {
        return score != null ? score : 50;
    }
}
