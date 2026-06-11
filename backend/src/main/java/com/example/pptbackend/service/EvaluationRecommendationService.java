package com.example.pptbackend.service;

import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.EvaluationReport;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRecommendationService.class);

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;

    @Value("${evaluation.recommendation-max-tokens:768}")
    private int recommendationMaxTokens;

    public EvaluationRecommendationService(ObjectMapper objectMapper,
                                           SystemConfigService systemConfigService,
                                           DeepseekChatClient deepseekChatClient) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
    }

    public String generateRecommendations(Project project, EvaluationReport report) {
        try {
            SystemConfigDto config = systemConfigService.getSystemConfig();
            String prompt = buildPrompt(project, report);
            String baseUrl = config.getLlmBaseUrl() != null ? config.getLlmBaseUrl() : "https://api.deepseek.com";
            String model = config.getLlmModel() != null && !config.getLlmModel().isBlank()
                ? config.getLlmModel() : "deepseek-chat";
            Map<String, Object> payload = LlmEndpointSupport.chatPayload(
                model,
                List.of(Map.of("role", "user", "content", prompt)),
                Math.min(0.5, config.getTemperature() != null ? config.getTemperature() : 0.3),
                recommendationMaxTokens,
                config.getTopP(),
                config.getTopK(),
                baseUrl);
            String body = objectMapper.writeValueAsString(payload);
            String raw = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(90));
            String content = extractAssistantContent(raw);
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("LLM evaluation recommendations failed: {}", e.getMessage());
        }
        return buildFallbackRecommendations(report);
    }

    private String buildPrompt(Project project, EvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 PPT 质量评估顾问。根据下列自动评分结果，输出 3~5 条可执行的改进建议。\n");
        sb.append("要求：中文、每条一行以「- 」开头、聚焦大纲逻辑/引用覆盖/信息密度/语言表达；不要重复分数本身；不要 JSON。\n\n");
        if (report.getPageId() != null) {
            sb.append("【范围】单页评估，pageId=").append(report.getPageId()).append("\n");
        } else {
            sb.append("【范围】整份演示文稿\n");
        }
        sb.append("【主题】").append(project.getTitle() != null ? project.getTitle() : "未命名").append("\n");
        sb.append("【自动加权总分】").append(formatNum(report.getAutoTotalScore())).append("\n");
        sb.append("【结构/逻辑】").append(formatInt(report.getAutoOutlineLogicScore())).append('\n');
        sb.append("【信息密度】").append(formatInt(report.getAutoInfoDensityScore())).append('\n');
        sb.append("【语言连贯】").append(formatInt(report.getAutoLanguageExpressionScore())).append('\n');
        sb.append("【引用覆盖】").append(formatInt(report.getAutoSourceCoverageScore())).append('\n');
        sb.append("【质量门禁】").append(report.getQualityGateStatus() != null ? report.getQualityGateStatus() : "—").append('\n');

        List<Slide> slides = project.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getPosition))
            .limit(report.getPageId() != null ? 1 : 8)
            .toList();
        if (report.getPageId() != null) {
            slides = project.getSlides().stream()
                .filter(s -> report.getPageId().equals(s.getId()))
                .toList();
        }
        if (!slides.isEmpty()) {
            sb.append("\n【页面标题摘要】\n");
            for (Slide s : slides) {
                sb.append("- ").append(s.getTitle() != null ? s.getTitle() : "无标题");
                int bullets = s.getBullets() != null ? s.getBullets().size() : 0;
                int sources = s.getSources() != null ? s.getSources().size() : 0;
                sb.append("（要点 ").append(bullets).append(" 条，引用 ").append(sources).append(" 条）\n");
            }
        }
        return sb.toString();
    }

    private String buildFallbackRecommendations(EvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 优先为缺少引用的页面补充可追溯来源。\n");
        if (report.getAutoInfoDensityScore() != null && report.getAutoInfoDensityScore() < 70) {
            sb.append("- 提高单页要点数量与信息密度（建议每页 ≥3 条要点）。\n");
        }
        if ("FAIL".equals(report.getQualityGateStatus())) {
            sb.append("- 当前未通过质量门禁，建议补充引用并重生薄弱页。\n");
        }
        sb.append("- 检查大纲是否包含封面、目录与结论等结构页。");
        return sb.toString();
    }

    private String extractAssistantContent(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            Object choicesObj = parsed.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return null;
            }
            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                return null;
            }
            Object messageObj = choice.get("message");
            if (!(messageObj instanceof Map<?, ?> message)) {
                return null;
            }
            Object content = message.get("content");
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatNum(Double v) {
        return v != null ? String.format("%.1f", v) : "—";
    }

    private static String formatInt(Integer v) {
        return v != null ? v.toString() : "—";
    }
}
