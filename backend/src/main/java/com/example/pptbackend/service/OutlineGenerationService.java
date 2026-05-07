package com.example.pptbackend.service;

import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutlineGenerationService {

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 叙事与红线已由数据库默认 Prompt 定义；此处保留为空以免双重指令冲突。 */
    private static final String OUTLINE_NARRATIVE_HINT = "";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;
    private final int outlineMaxTokens;

    public OutlineGenerationService(ObjectMapper objectMapper,
                                    SystemConfigService systemConfigService,
                                    DeepseekChatClient deepseekChatClient,
                                    @Value("${outline.max-tokens:2048}") int outlineMaxTokens) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
        this.outlineMaxTokens = Math.max(512, outlineMaxTokens);
    }

    public ProjectOutlineResponse generateOutline(String topic) {
        SystemConfigDto config = systemConfigService.getSystemConfig();
        String augmented = topic != null ? topic : "";
        if (!OUTLINE_NARRATIVE_HINT.isBlank()) {
            augmented = OUTLINE_NARRATIVE_HINT + "\n\n" + augmented;
        }
        String[] topicAndRetrieval = splitTopicAndRetrieval(augmented);
        String prompt = formatPrompt(
            config.getOutlinePromptTemplate(),
            Map.of("content", topicAndRetrieval[0], "retrieved_context", topicAndRetrieval[1]));

        String body = buildOutlineRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(120));
        try {
            ProjectOutlineResponse parsed = parseOutlineResponse(responseText);
            return validateAndNormalizeOutline(parsed);
        } catch (Exception e) {
            String assistant = extractAssistantContent(responseText);
            if (assistant != null) {
                try {
                    return validateAndNormalizeOutline(parseOutlineFromAssistantJson(assistant));
                } catch (Exception ignored) {
                    String fenced = stripMarkdownFence(assistant);
                    try {
                        ProjectOutlineResponse pr = parseOutlineJsonObject(fenced);
                        return validateAndNormalizeOutline(pr);
                    } catch (Exception ignored2) {
                        // fall through to degraded
                    }
                }
            }
            return degradedOutline(topic != null ? topic : "");
        }
    }

    /**
     * P1.2：去重标题、避免空章节名占用叙事位。
     */
    public ProjectOutlineResponse validateAndNormalizeOutline(ProjectOutlineResponse outline) {
        if (outline == null || outline.getSlides() == null) {
            return outline;
        }
        int seq = 1;
        Set<String> usedTitles = new HashSet<>();
        for (ProjectOutlineResponse.OutlineSlide slide : outline.getSlides()) {
            String raw = slide.getTitle() != null ? slide.getTitle().trim() : "未命名页";
            String candidate = raw.isEmpty() ? "未命名页" : raw;
            String unique = candidate;
            int n = 2;
            while (usedTitles.contains(unique)) {
                unique = candidate + "（" + n + "）";
                n++;
            }
            slide.setTitle(unique);
            usedTitles.add(unique);
            slide.setId(seq++);
        }
        return outline;
    }

    /**
     * 将上传节选 / 向量片段 / 权威检索等与「主题句」拆开，便于 Prompt 中分别标注叙事主料与检索上下文。
     */
    static String[] splitTopicAndRetrieval(String full) {
        if (full == null || full.isBlank()) {
            return new String[]{"", "（空输入）"};
        }
        String[] markers = {
            "\n\n【权威检索参考】",
            "\n\n【权威检索补充】",
            "\n\n向量检索到的文档片段：",
            "\n\n上传全文节选："
        };
        int cut = -1;
        for (String m : markers) {
            int p = full.indexOf(m);
            if (p >= 0 && (cut < 0 || p < cut)) {
                cut = p;
            }
        }
        if (cut >= 0) {
            return new String[]{full.substring(0, cut).trim(), full.substring(cut).trim()};
        }
        return new String[]{
            full.trim(),
            "（当前未检测到独立检索/节选标题块；上文整体视为素材，仍须写出数字锚点与递进章节；常识内容须在 notes 中标明 [待补充权威来源]。）"
        };
    }

    private String formatPrompt(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prompt;
    }

    private String buildOutlineRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getLlmModel());
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", outlineMaxTokens);
        payload.put("top_p", config.getTopP());
        payload.put("top_k", config.getTopK());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generation request", e);
        }
    }

    private ProjectOutlineResponse parseOutlineResponse(String responseText) throws IOException {
        Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
        String assistant = extractAssistantContentFromParsed(parsed);
        if (assistant == null || assistant.isBlank()) {
            throw new IllegalStateException("Empty assistant content");
        }
        return parseOutlineFromAssistantJson(assistant);
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantContentFromParsed(Map<String, Object> parsed) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return null;
        }
        return (String) message.get("content");
    }

    private String extractAssistantContent(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            return extractAssistantContentFromParsed(parsed);
        } catch (IOException e) {
            return null;
        }
    }

    private ProjectOutlineResponse parseOutlineFromAssistantJson(String content) throws IOException {
        String trimmed = content.trim();
        trimmed = stripMarkdownFence(trimmed);
        return parseOutlineJsonObject(trimmed);
    }

    private String stripMarkdownFence(String content) {
        Matcher m = JSON_FENCE.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private ProjectOutlineResponse parseOutlineJsonObject(String json) throws IOException {
        Map<String, Object> outline = objectMapper.readValue(json, new TypeReference<>() {});
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setTitle((String) outline.getOrDefault("title", "PPT 演示文稿"));

        List<Map<String, Object>> chapters = (List<Map<String, Object>>) outline.get("chapters");
        List<Map<String, Object>> slides = (List<Map<String, Object>>) outline.get("slides");

        if (chapters != null && !chapters.isEmpty()) {
            for (Map<String, Object> ch : chapters) {
                String chapterName = firstChapterTitle(ch);
                List<Map<String, Object>> chSlides = (List<Map<String, Object>>) ch.get("slides");
                if (chSlides == null || chSlides.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> slide : chSlides) {
                    response.getSlides().add(mapSlideMap(slide, chapterName));
                }
            }
        } else if (slides != null) {
            for (Map<String, Object> slide : slides) {
                Object pagesVal = slide.get("pages");
                String chapterHint = slide.get("chapter") != null ? slide.get("chapter").toString().trim() : null;
                if (pagesVal instanceof List<?> pageList && !pageList.isEmpty()) {
                    for (Object pObj : pageList) {
                        if (pObj instanceof Map<?, ?> pm) {
                            response.getSlides().add(mapSlideMap((Map<String, Object>) pm, chapterHint));
                        }
                    }
                } else {
                    response.getSlides().add(mapSlideMap(slide, chapterHint));
                }
            }
        }
        return response;
    }

    private static String firstChapterTitle(Map<String, Object> ch) {
        Object c = ch.get("chapter");
        if (c != null && !c.toString().isBlank()) {
            return c.toString().trim();
        }
        Object t = ch.get("title");
        if (t != null && !t.toString().isBlank()) {
            return t.toString().trim();
        }
        Object n = ch.get("name");
        if (n != null && !n.toString().isBlank()) {
            return n.toString().trim();
        }
        return "章节";
    }

    private ProjectOutlineResponse.OutlineSlide mapSlideMap(Map<String, Object> slide, String chapterFallback) {
        ProjectOutlineResponse.OutlineSlide outlineSlide = new ProjectOutlineResponse.OutlineSlide();
        Object idVal = slide.get("id");
        outlineSlide.setId(idVal instanceof Number ? ((Number) idVal).intValue() : 0);
        Object titleObj = slide.get("title");
        outlineSlide.setTitle(titleObj != null ? titleObj.toString() : "");
        Object contentValue = slide.get("content");
        if (contentValue instanceof List<?> list) {
            String[] contentArray = list.stream()
                .map(Object::toString)
                .toArray(String[]::new);
            outlineSlide.setContent(contentArray);
        } else {
            outlineSlide.setContent(new String[0]);
        }
        Object notesObj = slide.get("notes");
        outlineSlide.setNotes(notesObj != null ? notesObj.toString() : "");
        Object chObj = slide.get("chapter");
        String slideChapter = chObj != null ? chObj.toString().trim() : "";
        if (!slideChapter.isEmpty()) {
            outlineSlide.setChapter(slideChapter);
        } else if (chapterFallback != null && !chapterFallback.isBlank()) {
            outlineSlide.setChapter(chapterFallback.trim());
        }
        return outlineSlide;
    }

    private ProjectOutlineResponse degradedOutline(String topicSnippet) {
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setTitle("大纲（降级生成）");
        ProjectOutlineResponse.OutlineSlide slide = new ProjectOutlineResponse.OutlineSlide();
        slide.setId(1);
        slide.setTitle("主题概要");
        String snippet = topicSnippet.length() > 800 ? topicSnippet.substring(0, 800) + "…" : topicSnippet;
        slide.setContent(new String[]{
            "模型返回无法解析为 JSON，已使用降级大纲。",
            snippet.isBlank() ? "（无可用主题文本）" : snippet
        });
        slide.setNotes("请检查 DEEPSEEK_API_KEY 或网络；也可稍后重试生成。");
        response.getSlides().add(slide);
        return response;
    }
}
