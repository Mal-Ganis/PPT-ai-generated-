package com.example.pptbackend.service;

import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从讲稿要点（slide.bullets）提炼适合投影的短句（slide.pptBullets）。
 */
@Service
public class PptDisplayExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PptDisplayExtractionService.class);
    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;
    private final ProjectRepository projectRepository;
    private final SlideRepository slideRepository;
    private final String extractionModel;

    public PptDisplayExtractionService(ObjectMapper objectMapper,
                                       SystemConfigService systemConfigService,
                                       DeepseekChatClient deepseekChatClient,
                                       ProjectRepository projectRepository,
                                       SlideRepository slideRepository,
                                       @Value("${generation.ppt-display-model:deepseek-chat}") String extractionModel) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
        this.projectRepository = projectRepository;
        this.slideRepository = slideRepository;
        this.extractionModel = extractionModel != null && !extractionModel.isBlank()
            ? extractionModel.trim() : "deepseek-chat";
    }

    @FunctionalInterface
    public interface ExtractionProgressListener {
        void onProgress(int completedSlides, int totalSlides);
    }

    @Transactional
    public void extractAllForProject(Long projectId) {
        extractAllForProject(projectId, false, null);
    }

    @Transactional
    public void extractAllForProject(Long projectId,
                                     boolean force,
                                     ExtractionProgressListener progress) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }
        int duration = PresentationDurationPlanner.clampMinutes(
            projectRepository.findById(projectId)
                .map(p -> p.getPresentationDurationMinutes())
                .orElse(PresentationDurationPlanner.DEFAULT_MINUTES));

        List<Slide> slides = slideRepository.findByProject_IdOrderByPositionAsc(projectId);
        int total = slides.size();
        for (int i = 0; i < slides.size(); i++) {
            Slide slide = slides.get(i);
            if (!force && slide.getPptBullets() != null && !slide.getPptBullets().isEmpty()
                && !bulletsEquivalent(slide.getBullets(), slide.getPptBullets())) {
                if (progress != null) {
                    progress.onProgress(i + 1, total);
                }
                continue;
            }
            List<String> extracted = extractForSlide(slide, duration);
            slide.setPptBullets(extracted);
            slideRepository.save(slide);
            if (progress != null) {
                progress.onProgress(i + 1, total);
            }
        }
        log.info("PPT display extraction completed for project {}, slides={}", projectId, total);
    }

    @Transactional
    public List<String> extractAndSaveSlide(Long projectId, Long slideId) {
        Slide slide = slideRepository.findByIdAndProject_Id(slideId, projectId)
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));
        int duration = PresentationDurationPlanner.clampMinutes(
            projectRepository.findById(projectId)
                .map(p -> p.getPresentationDurationMinutes())
                .orElse(PresentationDurationPlanner.DEFAULT_MINUTES));
        List<String> extracted = extractForSlide(slide, duration);
        slide.setPptBullets(extracted);
        slideRepository.save(slide);
        return extracted;
    }

    List<String> extractForSlide(Slide slide, int durationMinutes) {
        List<String> script = slide.getBullets() != null ? slide.getBullets() : List.of();
        if (script.isEmpty()) {
            String body = slide.getBody();
            if (body != null && !body.isBlank()) {
                script = List.of(body.trim());
            }
        }
        if (script.isEmpty()) {
            return List.of();
        }

        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            return StructuralSlideDetector.pptBulletsForStructural(
                slide.getTitle(), slide.getChapter(), script);
        }

        String title = slide.getTitle() != null ? slide.getTitle() : "";
        String chapter = slide.getChapter() != null ? slide.getChapter() : "";
        String scriptBlock = script.stream()
            .map(s -> "- " + s)
            .collect(Collectors.joining("\n"));

        String prompt = """
            你是 PPT 投影文案编辑。任务：把「讲稿要点」改写成适合打在幻灯片上的短句（观众用眼睛扫读，不是念稿）。

            ## 页信息
            标题：%s
            章节：%s
            全稿目标演讲时长：约 %d 分钟

            ## 讲稿要点（输入）
            %s

            ## 改写规则（必须遵守）
            1. 删除口头衔接与语气词：如「然而」「因此」「综上」「承接上一页」「接下来」「其次」等；不要写「本页将介绍」。
            2. 每条为**短语或极短句**（建议 8–28 字），保留关键数字、机构名、结论词；禁止长段落。
            3. 条数：正文页 2–5 条，不得超过讲稿条数。问答页若出现，勿把高频问题、互动收尾等长句放上屏（由系统处理为仅标题）。
            4. 禁止编造讲稿中没有的事实；可合并重复信息。
            5. 只输出合法 JSON：{"ppt_bullets":["…","…"]}
            """.formatted(
            title,
            chapter.isBlank() ? "（无）" : chapter,
            durationMinutes,
            scriptBlock);

        SystemConfigDto config = systemConfigService.getSystemConfig();
        String body = buildRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(90));
        return parsePptBullets(responseText, script);
    }

    private String buildRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", extractionModel);
        payload.put("messages", List.of(
            Map.of("role", "system", "content", "你只输出 JSON 对象，首字符为 {，末字符为 }。"),
            Map.of("role", "user", "content", prompt)));
        payload.put("temperature", Math.min(0.5, config.getTemperature() != null ? config.getTemperature() : 0.5));
        payload.put("max_tokens", 512);
        payload.put("response_format", Map.of("type", "json_object"));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize extraction request", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parsePptBullets(String responseText, List<String> fallbackScript) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                return heuristicFallback(fallbackScript);
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                return heuristicFallback(fallbackScript);
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                return heuristicFallback(fallbackScript);
            }
            String json = stripFence(content.trim());
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {});
            Object raw = payload.get("ppt_bullets");
            if (raw == null) {
                raw = payload.get("pptBullets");
            }
            if (raw instanceof List<?> list) {
                List<String> out = list.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (IOException e) {
            log.warn("PPT display JSON parse failed: {}", e.getMessage());
        }
        return heuristicFallback(fallbackScript);
    }

    private static List<String> heuristicFallback(List<String> script) {
        return script.stream()
            .map(PptDisplayExtractionService::stripOralLead)
            .filter(s -> !s.isEmpty())
            .limit(5)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String stripOralLead(String line) {
        if (line == null) {
            return "";
        }
        String s = line.trim();
        s = s.replaceFirst("^(然而|因此|所以|其次|再次|总之|综上|此外|接下来|最后|首先|同时|另一方面)[，,、]?\\s*", "");
        s = s.replaceFirst("^承接(上一页|前文)[^，,。]*[，,、]?\\s*", "");
        if (s.length() > 36) {
            s = s.substring(0, 35) + "…";
        }
        return s.trim();
    }

    private static String stripFence(String content) {
        Matcher m = JSON_FENCE.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content;
    }

    /** 与讲稿完全相同的 ppt 要点视为未提炼，需重新调用模型 */
    private static boolean bulletsEquivalent(List<String> script, List<String> ppt) {
        if (script == null || ppt == null) {
            return false;
        }
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
