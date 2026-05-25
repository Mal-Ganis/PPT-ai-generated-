package com.example.pptbackend.service;

import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.IndexSearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文页引用来源：解析模型 JSON、过滤假链，并在无有效来源时用检索/Tavily 结果回填。
 */
@Service
public class SlideSourceCitationService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s|\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT_LIKE = Pattern.compile("^\\s*\\{.*}\\s*$", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public SlideSourceCitationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param modelSources  模型返回的 sources
     * @param retrievalLines 向量片段 / Tavily 等检索候选（无模型来源时优先使用）
     */
    public List<String> mergeAndSanitize(List<String> modelSources, List<String> retrievalLines) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendUnique(out, seen, normalizeLines(modelSources));
        if (out.isEmpty()) {
            appendUnique(out, seen, normalizeLines(retrievalLines));
        }
        if (out.isEmpty()) {
            out.add("未命中可核验的外部链接；要点含 [待核实] 处请结合上传文档或「知识检索」结果后在本页手动补充出处");
        }
        return out;
    }

    public List<String> linesFromExternalDocuments(List<ExternalSourceDocument> docs, int max) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int n = 0;
        for (ExternalSourceDocument doc : docs) {
            if (n >= max) {
                break;
            }
            String line = formatExternalDoc(doc);
            if (!line.isBlank() && !isBlockedOrHallucinatedSource(line)) {
                lines.add(line);
                n++;
            }
        }
        return lines;
    }

    public List<String> linesFromIndexResults(List<IndexSearchResult> results, int max) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int n = 0;
        for (IndexSearchResult hit : results) {
            if (n >= max) {
                break;
            }
            String line = formatIndexHit(hit, n + 1);
            if (!line.isBlank() && !isBlockedOrHallucinatedSource(line)) {
                lines.add(line);
                n++;
            }
        }
        return lines;
    }

    private static String formatExternalDoc(ExternalSourceDocument doc) {
        String title = doc.getTitle() != null ? doc.getTitle().trim() : "";
        String url = doc.getUrl() != null ? doc.getUrl().trim() : "";
        String type = doc.getSourceType() != null ? doc.getSourceType().trim() : "external";
        if (!url.isEmpty()) {
            return title.isEmpty() ? url + " | type=" + type : title + " | " + url + " | type=" + type;
        }
        if (!title.isEmpty()) {
            return title + " | type=" + type;
        }
        return "";
    }

    private String formatIndexHit(IndexSearchResult hit, int index) {
        String url = extractUrlFromMetadata(hit.getMetadata());
        String excerpt = truncate(hit.getContent(), 72);
        if (url != null && !url.isBlank()) {
            String label = hit.getSegmentId() != null ? hit.getSegmentId() : ("片段#" + index);
            return label + " | " + url.trim() + " | type=index";
        }
        if (excerpt.isBlank()) {
            return "";
        }
        return "项目文档片段 " + index + " | 节选：" + excerpt + " | type=index";
    }

    private String extractUrlFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(metadataJson, new TypeReference<>() {});
            for (String key : List.of("url", "sourceUrl", "link", "href")) {
                Object v = meta.get(key);
                if (v != null && !v.toString().isBlank()) {
                    return v.toString().trim();
                }
            }
        } catch (Exception ignored) {
            Matcher m = URL_PATTERN.matcher(metadataJson);
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    private List<String> normalizeLines(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            String normalized = normalizeSourceLine(line);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            if (isBlockedOrHallucinatedSource(normalized) || isInternalPlaceholder(normalized)) {
                continue;
            }
            out.add(normalized);
        }
        return out;
    }

    /**
     * 支持字符串、JSON 对象字符串、或 Map 序列化后的行。
     */
    public String normalizeSourceLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        if (JSON_OBJECT_LIKE.matcher(trimmed).matches()) {
            try {
                Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<>() {});
                return formatSourceMap(map);
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
        if (urlMatcher.find() && !trimmed.contains(" | ")) {
            String url = urlMatcher.group();
            String title = trimmed.replace(url, "").replaceAll("[\\s|：:]+$", "").trim();
            if (title.isEmpty()) {
                return url;
            }
            return title + " | " + url;
        }
        return trimmed;
    }

    private static String formatSourceMap(Map<String, Object> map) {
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
                sb.append(" | type=");
            } else {
                sb.append("type=");
            }
            sb.append(type.toString().trim());
        }
        return sb.toString();
    }

    private static boolean isInternalPlaceholder(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("type=llm_inference")
            || lower.contains("已过滤不可验证链接")
            || lower.contains("常识归纳需人工核对")
            || lower.contains("内部降级输出");
    }

    static boolean isBlockedOrHallucinatedSource(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("example.com") || lower.contains("example.org")) {
            return true;
        }
        if (lower.contains("localhost")) {
            return true;
        }
        if (lower.contains("article/123456")) {
            return true;
        }
        return lower.contains("placeholder");
    }

    private static void appendUnique(List<String> out, Set<String> seen, List<String> lines) {
        for (String line : lines) {
            if (seen.add(line)) {
                out.add(line);
            }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
