package com.example.pptbackend.service;

import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.GenerateSlidesRequest;
import com.example.pptbackend.dto.SearchRequest;
import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.dto.SlideContentResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.Project;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SlideGenerationService {

    private enum CorrectionTier {
        NONE("", 0),
        TIER1(
            "\n\n【一级修订】要点不足或引用偏弱：请至少输出 3 条要点，每条补充可追溯 sources（检索为空写常识归纳）。",
            2),
        TIER2(
            "\n\n【二级修订】整体质量仍不达标：请显著提高信息密度、备注长度与引用覆盖，要点表述尽量对齐上方检索片段。",
            6);

        final String suffix;
        final int ragBoost;

        CorrectionTier(String suffix, int ragBoost) {
            this.suffix = suffix;
            this.ragBoost = ragBoost;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SlideGenerationService.class);

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 与默认 slidePromptTemplate 配合；避免与模板中的叙事/禁止项重复，仅做 JSON 与检索提醒。 */
    private static final String SLIDE_QUALITY_RULES = """

【系统提醒】下方若已含「检索上下文」段落，须优先用其中事实与数字；无命中时写常识并标 [待核实]。
【格式】严格返回合法 JSON；content 至少 3 条；notes 须含上一页/下一页衔接；sources 至少 1 条（字符串或 {title,url,type} 对象均可）。
""";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;
    private final IndexSegmentService indexSegmentService;
    private final EmbeddingService embeddingService;
    private final SlideRepository slideRepository;
    private final ProjectRepository projectRepository;
    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;
    private final EvaluationReportService evaluationReportService;

    private final int slideMaxTokens;
    private final boolean slideFallbackTavily;
    private final int slideFallbackLimit;
    private final boolean selfCorrectionEnabled;
    private final double tier1AutoBelow;
    private final double tier1FactBelow;
    private final double tier2AutoBelow;
    private final double tier2FactBelow;

    public SlideGenerationService(ObjectMapper objectMapper,
                                  SystemConfigService systemConfigService,
                                  DeepseekChatClient deepseekChatClient,
                                  IndexSegmentService indexSegmentService,
                                  EmbeddingService embeddingService,
                                  SlideRepository slideRepository,
                                  ProjectRepository projectRepository,
                                  ExternalKnowledgeSourceService externalKnowledgeSourceService,
                                  EvaluationReportService evaluationReportService,
                                  @Value("${generation.slide-max-tokens:2048}") int slideMaxTokens,
                                  @Value("${generation.slide-fallback-tavily:true}") boolean slideFallbackTavily,
                                  @Value("${generation.slide-fallback-result-limit:2}") int slideFallbackLimit,
                                  @Value("${generation.self-correction-enabled:true}") boolean selfCorrectionEnabled,
                                  @Value("${generation.self-correction-tier1-auto-below:76}") double tier1AutoBelow,
                                  @Value("${generation.self-correction-tier1-fact-below:0.58}") double tier1FactBelow,
                                  @Value("${generation.self-correction-tier2-auto-below:70}") double tier2AutoBelow,
                                  @Value("${generation.self-correction-tier2-fact-below:0.48}") double tier2FactBelow) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
        this.indexSegmentService = indexSegmentService;
        this.embeddingService = embeddingService;
        this.slideRepository = slideRepository;
        this.projectRepository = projectRepository;
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
        this.evaluationReportService = evaluationReportService;
        this.slideMaxTokens = Math.max(512, slideMaxTokens);
        this.slideFallbackTavily = slideFallbackTavily;
        this.slideFallbackLimit = Math.max(1, slideFallbackLimit);
        this.selfCorrectionEnabled = selfCorrectionEnabled;
        this.tier1AutoBelow = tier1AutoBelow;
        this.tier1FactBelow = tier1FactBelow;
        this.tier2AutoBelow = tier2AutoBelow;
        this.tier2FactBelow = tier2FactBelow;
    }

    @Transactional
    public SlideContentResponse regenerateSlide(Long projectId, Long slideId, String inputType, String inputContent) {
        return regenerateSlide(projectId, slideId, inputType, inputContent, CorrectionTier.NONE);
    }

    private SlideContentResponse regenerateSlide(Long projectId, Long slideId, String inputType, String inputContent,
                                                 CorrectionTier tier) {
        Slide slide = slideRepository.findByIdAndProject_Id(slideId, projectId)
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));

        SystemConfigDto config = systemConfigService.getSystemConfig();
        String ragContext = buildRagContext(projectId, slide.getTitle(), config.getRetrievalLimit(), tier);

        Project projectForNav = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        List<Slide> ordered = orderedSlides(projectForNav);
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (slide.getId().equals(ordered.get(i).getId())) {
                idx = i;
                break;
            }
        }
        String prevSlideTitle = idx <= 0 ? "无" : nzTitle(ordered.get(idx - 1).getTitle());
        String nextSlideTitle = idx < 0 || idx >= ordered.size() - 1 ? "无" : nzTitle(ordered.get(idx + 1).getTitle());
        String chapterLabel = slide.getChapter() != null && !slide.getChapter().isBlank()
            ? slide.getChapter().trim()
            : "（未标注章节）";

        String retrievedContextBlock = ragContext.isBlank()
            ? "（暂无向量命中或 Tavily 摘要；请基于主题与常识撰写，事实标 [待核实]，勿伪造链接。）"
            : (ragContext.startsWith("【当前页外部简要依据】")
                ? ragContext
                : "【向量检索 ILF-2 / 须优先对齐的事实片段】\n" + ragContext);

        Map<String, String> vars = new HashMap<>();
        vars.put("slideTitle", slide.getTitle() != null ? slide.getTitle() : "");
        vars.put("chapter", chapterLabel);
        vars.put("prevSlideTitle", prevSlideTitle);
        vars.put("nextSlideTitle", nextSlideTitle);
        vars.put("inputType", "topic".equalsIgnoreCase(inputType) ? "主题" : "文档");
        vars.put("inputContent", inputContent != null ? inputContent : "");
        vars.put("retrieved_context", retrievedContextBlock);

        String basePrompt = formatPrompt(config.getSlidePromptTemplate(), vars);
        String prompt = basePrompt + SLIDE_QUALITY_RULES + tier.suffix;

        String body = buildSlideRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(120));
        SlideContentResponse parsed = parseSlideResponse(responseText);

        slide.setBullets(parsed.getContent() != null ? parsed.getContent() : new ArrayList<>());
        slide.setNotes(parsed.getNotes());
        if (parsed.getSources() != null && !parsed.getSources().isEmpty()) {
            slide.setSources(parsed.getSources());
        }
        slideRepository.save(slide);

        return parsed;
    }

    @Transactional
    public void generateAllSlides(Long projectId, GenerateSlidesRequest request) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));

        String inputType = request.getInputType() != null ? request.getInputType() : "topic";
        String inputContent = request.getInputContent() != null ? request.getInputContent() : "";

        List<Slide> slides = orderedSlides(project);

        for (Slide slide : slides) {
            regenerateSlide(projectId, slide.getId(), inputType, inputContent, CorrectionTier.NONE);
        }

        EvaluationReportResponse eval;
        try {
            eval = evaluationReportService.createAutoEvaluationReportAndReturn(projectId);
        } catch (Exception e) {
            log.warn("Auto evaluation after slide generation failed: {}", e.getMessage());
            return;
        }

        if (!selfCorrectionEnabled) {
            return;
        }

        if (!belowThreshold(eval, tier1AutoBelow, tier1FactBelow)) {
            return;
        }

        log.info(
            "Self-correction tier1 (auto={}, fact={})",
            eval.getAutoTotalScore(),
            eval.getFactVerificationRate());

        Project refreshedProject = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        List<Slide> refreshed = orderedSlides(refreshedProject);
        boolean anyWeak = false;
        for (Slide slide : refreshed) {
            if (needsWeakSlideRegeneration(slide)) {
                regenerateSlide(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER1);
                anyWeak = true;
            }
        }
        if (!anyWeak) {
            for (Slide slide : refreshed) {
                regenerateSlide(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER1);
            }
        }

        try {
            eval = evaluationReportService.createAutoEvaluationReportAndReturn(projectId);
        } catch (Exception e) {
            log.warn("Re-eval after tier1 failed: {}", e.getMessage());
            return;
        }

        if (!belowThreshold(eval, tier2AutoBelow, tier2FactBelow)) {
            return;
        }

        log.info(
            "Self-correction tier2 full pass (auto={}, fact={})",
            eval.getAutoTotalScore(),
            eval.getFactVerificationRate());
        refreshedProject = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        refreshed = orderedSlides(refreshedProject);
        for (Slide slide : refreshed) {
            regenerateSlide(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER2);
        }
        try {
            evaluationReportService.createAutoEvaluationReport(projectId);
        } catch (Exception e) {
            log.warn("Auto evaluation after tier2 failed: {}", e.getMessage());
        }
    }

    private static String nzTitle(String title) {
        return title != null ? title : "";
    }

    private static List<Slide> orderedSlides(Project project) {
        return project.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getPosition))
            .collect(Collectors.toList());
    }

    private static boolean needsWeakSlideRegeneration(Slide slide) {
        List<String> bullets = slide.getBullets();
        int n = bullets != null ? bullets.size() : 0;
        boolean noSources = slide.getSources() == null || slide.getSources().isEmpty();
        String notes = slide.getNotes();
        int noteLen = notes != null ? notes.length() : 0;
        return n < 3 || noSources || noteLen < 40;
    }

    private static boolean belowThreshold(EvaluationReportResponse eval,
                                          double autoBelow,
                                          double factBelow) {
        double auto = eval.getAutoTotalScore() != null ? eval.getAutoTotalScore() : 100;
        if (auto < autoBelow) {
            return true;
        }
        Double fact = eval.getFactVerificationRate();
        return fact != null && fact < factBelow;
    }

    private String buildRagContext(Long projectId, String slideTitle, int topK, CorrectionTier tier) {
        int base = topK > 0 ? topK : 5;
        int effective = Math.min(15, Math.max(1, base + tier.ragBoost));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setProjectId(projectId);
        searchRequest.setQueryEmbedding(embeddingService.embed(slideTitle));
        searchRequest.setTopK(effective);
        SearchResponse response = indexSegmentService.search(searchRequest);
        StringBuilder builder = new StringBuilder();
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            int index = 1;
            for (var result : response.getResults()) {
                builder.append(index++)
                    .append(". ")
                    .append(result.getContent() != null ? result.getContent().trim() : "")
                    .append(" (distance=")
                    .append(String.format("%.4f", result.getDistance()))
                    .append(")\n");
            }
        }
        if (!builder.isEmpty()) {
            return builder.toString().trim();
        }
        if (slideFallbackTavily && slideTitle != null && !slideTitle.isBlank()) {
            List<ExternalSourceDocument> docs =
                externalKnowledgeSourceService.searchExternalSources(slideTitle, slideFallbackLimit);
            if (!docs.isEmpty()) {
                return externalKnowledgeSourceService.formatDocumentsForSlidePrompt(docs, 3500);
            }
        }
        return "";
    }

    private String formatPrompt(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prompt;
    }

    private String buildSlideRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getLlmModel());
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", slideMaxTokens);
        payload.put("top_p", config.getTopP());
        payload.put("top_k", config.getTopK());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize generation request", e);
        }
    }

    private SlideContentResponse parseSlideResponse(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                return degradedSlide("模型未返回 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                return degradedSlide("模型未返回 message");
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                return degradedSlide("模型返回空内容");
            }

            String jsonPayload = stripFence(content.trim());
            try {
                Map<String, Object> payload = objectMapper.readValue(jsonPayload, new TypeReference<>() {});
                return mapPayloadToSlideResponse(payload);
            } catch (IOException e) {
                return degradedSlide(content);
            }
        } catch (IOException e) {
            return degradedSlide(responseText);
        }
    }

    private String stripFence(String content) {
        Matcher m = JSON_FENCE.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content;
    }

    private SlideContentResponse mapPayloadToSlideResponse(Map<String, Object> payload) {
        SlideContentResponse response = new SlideContentResponse();
        Object contentValue = payload.get("content");
        if (contentValue instanceof List<?> list) {
            response.setContent(list.stream().map(Object::toString).collect(Collectors.toList()));
        } else {
            response.setContent(new ArrayList<>());
        }
        response.setNotes(payload.get("notes") != null ? payload.get("notes").toString() : "");
        Object sourcesValue = payload.get("sources");
        if (sourcesValue instanceof List<?> list) {
            response.setSources(list.stream().map(SlideGenerationService::formatSourceEntry).collect(Collectors.toList()));
        }
        return response;
    }

    /**
     * 支持字符串来源行，或 {"title","url","type"} 对象（与默认 Prompt 示例一致）。
     */
    private static String formatSourceEntry(Object o) {
        if (o instanceof Map<?, ?> map) {
            Object title = map.get("title");
            Object url = map.get("url");
            Object type = map.get("type");
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.toString().isBlank()) {
                sb.append(title.toString().trim());
            }
            if (url != null && !url.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(url.toString().trim());
            }
            if (type != null && !type.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(type.toString().trim());
            }
            return sb.length() > 0 ? sb.toString() : String.valueOf(o);
        }
        return o != null ? o.toString() : "";
    }

    private SlideContentResponse degradedSlide(String raw) {
        SlideContentResponse response = new SlideContentResponse();
        String line = raw != null ? raw.replace("\n", " ").trim() : "";
        if (line.length() > 400) {
            line = line.substring(0, 400) + "…";
        }
        response.setContent(List.of(
            "（结构化解析失败，以下为降级展示的原始摘要）",
            line.isEmpty() ? "无可用文本" : line
        ));
        response.setNotes("模型输出不是合法 JSON，已降级；请检查提示词或稍后重试。");
        response.setSources(List.of("内部降级输出"));
        return response;
    }
}
