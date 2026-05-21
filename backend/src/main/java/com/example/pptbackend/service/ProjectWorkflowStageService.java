package com.example.pptbackend.service;

import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.SlideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 与前端 {@code slidesHaveGeneratedContent} / {@code slidesHaveReadyPreview} 对齐的项目进度判断。
 */
@Service
public class ProjectWorkflowStageService {

    private static final String PLACEHOLDER_BULLET = "（待编辑要点）";

    private final SlideRepository slideRepository;

    public ProjectWorkflowStageService(SlideRepository slideRepository) {
        this.slideRepository = slideRepository;
    }

    public record StageSnapshot(boolean hasGeneratedContent, boolean hasReadyPreview, String stageLabel) {
    }

    @Transactional(readOnly = true)
    public StageSnapshot evaluateProject(Long projectId) {
        return evaluateSlides(slideRepository.findByProject_IdOrderByPositionAsc(projectId));
    }

    public StageSnapshot evaluateSlides(List<Slide> slides) {
        boolean hasContent = hasGeneratedContent(slides);
        boolean hasPreview = hasReadyPreview(slides);
        return new StageSnapshot(hasContent, hasPreview, toStageLabel(hasPreview, hasContent));
    }

    public Map<Long, StageSnapshot> evaluateProjects(Map<Long, List<Slide>> slidesByProjectId) {
        Map<Long, StageSnapshot> out = new HashMap<>();
        for (Map.Entry<Long, List<Slide>> entry : slidesByProjectId.entrySet()) {
            out.put(entry.getKey(), evaluateSlides(entry.getValue()));
        }
        return out;
    }

    static String toStageLabel(boolean hasReadyPreview, boolean hasGeneratedContent) {
        if (hasReadyPreview) {
            return "可预览";
        }
        if (hasGeneratedContent) {
            return "已有正文";
        }
        return "仅大纲";
    }

    static boolean hasGeneratedContent(List<Slide> slides) {
        return slides.stream().anyMatch(ProjectWorkflowStageService::slideHasGeneratedContent);
    }

    static boolean hasReadyPreview(List<Slide> slides) {
        List<Slide> bodySlides = slides.stream()
            .filter(ProjectWorkflowStageService::isBodySlideWithScript)
            .toList();
        if (bodySlides.isEmpty()) {
            return false;
        }
        return bodySlides.stream().allMatch(ProjectWorkflowStageService::slideHasDistinctPpt);
    }

    private static boolean isBodySlideWithScript(Slide slide) {
        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            return false;
        }
        return scriptLines(slide).stream().anyMatch(ProjectWorkflowStageService::lineHasText);
    }

    private static boolean slideHasGeneratedContent(Slide slide) {
        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            return false;
        }
        if (!scriptLines(slide).stream().anyMatch(ProjectWorkflowStageService::lineHasText)) {
            return false;
        }
        return slide.getSources() != null
            && slide.getSources().stream().anyMatch(ProjectWorkflowStageService::lineHasText);
    }

    private static boolean slideHasDistinctPpt(Slide slide) {
        List<String> script = scriptLines(slide);
        List<String> ppt = slide.getPptBullets() != null ? slide.getPptBullets() : List.of();
        if (!script.stream().anyMatch(ProjectWorkflowStageService::lineHasText)) {
            return false;
        }
        if (!ppt.stream().anyMatch(ProjectWorkflowStageService::lineHasText)) {
            return false;
        }
        return !bulletsEquivalent(script, ppt);
    }

    private static List<String> scriptLines(Slide slide) {
        if (slide.getBullets() != null && !slide.getBullets().isEmpty()) {
            return slide.getBullets();
        }
        if (slide.getBody() != null && !slide.getBody().isBlank()) {
            return List.of(slide.getBody().trim());
        }
        return List.of();
    }

    private static boolean lineHasText(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        return !t.isEmpty() && !PLACEHOLDER_BULLET.equals(t);
    }

    private static boolean bulletsEquivalent(List<String> script, List<String> ppt) {
        List<String> a = script.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(String::trim)
            .toList();
        List<String> b = ppt.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(String::trim)
            .toList();
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
