package com.example.pptbackend.service;

import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.ExternalSourceLoadRequest;
import com.example.pptbackend.dto.IndexSegmentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EIF-1：双层权威检索。
 * <ul>
 *   <li>主源：Tavily Search（需 {@code TAVILY_API_KEY} / {@code tavily.api-key}）</li>
 *   <li>兜底：中文维基 MediaWiki API（无密钥）</li>
 * </ul>
 * 已移除通用流程中的 Spaceflight News（仅作示例 URL 注释保留）。
 */
@Service
public class ExternalKnowledgeSourceService {

    /**
     * 示例占位源（航天资讯），已从 {@link #searchExternalSources} 主路径移除。
     * https://api.spaceflightnewsapi.net/v3/articles
     */
    @SuppressWarnings("unused")
    private static final String SPACE_FLIGHT_NEWS_EXAMPLE = "https://api.spaceflightnewsapi.net/v3/articles";

    private static final String TAVILY_SEARCH = "https://api.tavily.com/search";
    private static final String WIKIPEDIA_API = "https://zh.wikipedia.org/w/api.php";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IndexSegmentService indexSegmentService;
    private final EmbeddingService embeddingService;
    private final String tavilyApiKey;
    private final String tavilySearchDepth;
    private final int tavilyMaxResults;
    private final int tavilyTimeoutSeconds;
    private final String wikipediaMode;

    public ExternalKnowledgeSourceService(ObjectMapper objectMapper,
                                          IndexSegmentService indexSegmentService,
                                          EmbeddingService embeddingService,
                                          @Value("${tavily.api-key:}") String tavilyApiKey,
                                          @Value("${tavily.search-depth:advanced}") String tavilySearchDepth,
                                          @Value("${tavily.max-results:10}") int tavilyMaxResults,
                                          @Value("${tavily.timeout-seconds:45}") int tavilyTimeoutSeconds,
                                          @Value("${tavily.wikipedia-mode:fallback}") String wikipediaMode) {
        this.objectMapper = objectMapper;
        this.indexSegmentService = indexSegmentService;
        this.embeddingService = embeddingService;
        this.tavilyApiKey = tavilyApiKey != null ? tavilyApiKey.trim() : "";
        this.tavilySearchDepth = tavilySearchDepth != null && !tavilySearchDepth.isBlank()
            ? tavilySearchDepth.trim()
            : "advanced";
        this.tavilyMaxResults = Math.min(20, Math.max(1, tavilyMaxResults));
        this.tavilyTimeoutSeconds = Math.min(120, Math.max(10, tavilyTimeoutSeconds));
        this.wikipediaMode = wikipediaMode != null ? wikipediaMode.trim().toLowerCase() : "fallback";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<ExternalSourceDocument> searchExternalSources(String query, int limit) {
        int safeLimit = Math.max(1, limit);
        List<ExternalSourceDocument> primary = fetchTavily(query);
        List<ExternalSourceDocument> merged = new ArrayList<>(primary);
        boolean needWiki = "supplement".equals(wikipediaMode) || primary.size() < 2;
        if (needWiki) {
            merged.addAll(fetchWikipediaArticles(query, 5));
        }
        return dedupeByUrl(merged).stream()
            .limit(safeLimit)
            .collect(Collectors.toList());
    }

    public int loadExternalSources(ExternalSourceLoadRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery();
        int limit = request.getLimit() != null ? request.getLimit() : 3;
        Long projectId = request.getProjectId() != null ? request.getProjectId() : 0L;

        List<ExternalSourceDocument> docs = searchExternalSources(query, limit);
        return indexDocumentsIntoProject(projectId, docs);
    }

    /**
     * 将权威检索结果写入项目向量索引（ILF-2），供正文阶段 {@code SlideGenerationService.buildRagContext} 命中。
     */
    public int indexDocumentsIntoProject(Long projectId, List<ExternalSourceDocument> docs) {
        if (projectId == null || docs == null || docs.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ExternalSourceDocument doc : docs) {
            IndexSegmentRequest segmentRequest = new IndexSegmentRequest();
            segmentRequest.setProjectId(projectId);
            segmentRequest.setSegmentId(
                "external-" + projectId + "-" + Math.abs(urlOrFallback(doc).hashCode()));
            segmentRequest.setContent(buildSegmentContent(doc));
            segmentRequest.setMetadata(buildMetadata(doc));
            segmentRequest.setEmbedding(embeddingService.embed(segmentRequest.getContent()));
            indexSegmentService.indexSegment(segmentRequest);
            count++;
        }
        return count;
    }

    /** 注入大纲 Prompt：标题 + 摘要 + 链接，总长封顶 */
    public String formatDocumentsForOutlinePrompt(List<ExternalSourceDocument> docs, int maxChars) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【权威检索参考】以下为系统自动检索的资料摘要，请组织**有递进关系**的大纲（封面→目录→背景→核心→案例/数据→风险→结论等按需取舍）；")
            .append("论点应有可追溯线索，勿逐句抄袭。\n\n");
        int budget = maxChars - sb.length();
        for (ExternalSourceDocument doc : docs) {
            String url = doc.getUrl() != null ? doc.getUrl() : "";
            String block = String.format("[%s] %s\n%s\n链接：%s\n\n",
                doc.getSource() != null ? doc.getSource() : "",
                doc.getTitle() != null ? doc.getTitle() : "",
                doc.getSummary() != null ? doc.getSummary() : "",
                url);
            if (block.length() > budget && budget > 200) {
                block = block.substring(0, Math.min(block.length(), budget - 20)) + "…\n\n";
            }
            if (block.length() > budget) {
                break;
            }
            sb.append(block);
            budget -= block.length();
        }
        return sb.toString();
    }

    /** 正文兜底：单页 Tavily 命中较少时使用精简片段 */
    public String formatDocumentsForSlidePrompt(List<ExternalSourceDocument> docs, int maxChars) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【当前页外部简要依据】\n");
        int budget = maxChars - sb.length();
        for (ExternalSourceDocument doc : docs) {
            String block = String.format("- %s | %s\n  %s\n",
                doc.getSource(),
                doc.getTitle(),
                doc.getSummary() != null ? doc.getSummary() : "");
            if (block.length() > budget) {
                break;
            }
            sb.append(block);
            budget -= block.length();
        }
        return sb.toString().trim();
    }

    private static String urlOrFallback(ExternalSourceDocument doc) {
        return doc.getUrl() != null ? doc.getUrl() : doc.getTitle();
    }

    /**
     * Tavily：POST JSON，body 含 api_key（与章程一致）；失败或结果不足两条时由上层补充维基。
     */
    private List<ExternalSourceDocument> fetchTavily(String query) {
        if (tavilyApiKey.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("api_key", tavilyApiKey);
            body.put("query", query);
            body.put("search_depth", tavilySearchDepth);
            body.put("max_results", tavilyMaxResults);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TAVILY_SEARCH))
                .timeout(Duration.ofSeconds(tavilyTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 500 || code == 408) {
                return List.of();
            }
            if (code >= 400) {
                return List.of();
            }
            Map<String, Object> root = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object resultsObj = root.get("results");
            if (!(resultsObj instanceof List<?> results)) {
                return List.of();
            }
            List<ExternalSourceDocument> out = new ArrayList<>();
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                ExternalSourceDocument doc = new ExternalSourceDocument();
                doc.setTitle(stringValue(m.get("title")));
                doc.setUrl(stringValue(m.get("url")));
                doc.setSummary(stringValue(m.get("content")));
                doc.setSource("Tavily");
                doc.setAuthor("");
                doc.setPublishedAt("");
                doc.setSourceType("tavily");
                double score = doubleValue(m.get("score"));
                doc.setCredibilityScore(score);
                doc.setTrustScore(score);
                out.add(doc);
            }
            return out;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<ExternalSourceDocument> fetchWikipediaArticles(String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }
        String url = WIKIPEDIA_API + "?action=query&list=search&srsearch="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&format=json&srlimit=" + limit;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "ppt-ai-backend/1.0 (https://example.local)")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return List.of();
            }
            Map<String, Object> root = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object queryObj = root.get("query");
            if (!(queryObj instanceof Map<?, ?> qMap)) {
                return List.of();
            }
            Object searchObj = qMap.get("search");
            if (!(searchObj instanceof List<?> searchList)) {
                return List.of();
            }
            List<ExternalSourceDocument> out = new ArrayList<>();
            for (Object item : searchList) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                ExternalSourceDocument doc = new ExternalSourceDocument();
                String title = stringValue(m.get("title"));
                doc.setTitle(title);
                doc.setSummary(stripHtml(stringValue(m.get("snippet"))));
                String encoded = URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
                doc.setUrl("https://zh.wikipedia.org/wiki/" + encoded);
                doc.setSource("维基百科");
                doc.setAuthor("维基百科用户社群");
                doc.setPublishedAt("");
                doc.setSourceType("mediawiki");
                doc.setCredibilityScore(0.82);
                doc.setTrustScore(0.82);
                out.add(doc);
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private static List<ExternalSourceDocument> dedupeByUrl(List<ExternalSourceDocument> docs) {
        Map<String, ExternalSourceDocument> map = new LinkedHashMap<>();
        for (ExternalSourceDocument doc : docs) {
            String key = doc.getUrl() != null && !doc.getUrl().isBlank()
                ? doc.getUrl()
                : "title:" + doc.getTitle() + ":" + doc.getSourceType();
            map.putIfAbsent(key, doc);
        }
        return new ArrayList<>(map.values());
    }

    private static String stripHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]+>", "").trim();
    }

    private String buildSegmentContent(ExternalSourceDocument doc) {
        return String.format("%s\n\n%s", doc.getTitle(), doc.getSummary() != null ? doc.getSummary() : "");
    }

    private Map<String, Object> buildMetadata(ExternalSourceDocument doc) {
        return Map.of(
            "externalSource", true,
            "title", doc.getTitle() != null ? doc.getTitle() : "",
            "source", doc.getSource() != null ? doc.getSource() : "",
            "sourceType", doc.getSourceType() != null ? doc.getSourceType() : "",
            "url", doc.getUrl() != null ? doc.getUrl() : "",
            "publishedAt", doc.getPublishedAt() != null ? doc.getPublishedAt() : "",
            "author", doc.getAuthor() != null ? doc.getAuthor() : "",
            "summary", doc.getSummary() != null ? doc.getSummary() : "",
            "trustScore", doc.getTrustScore() != null ? doc.getTrustScore() : 0.0,
            "credibilityScore", doc.getCredibilityScore() != null ? doc.getCredibilityScore() : 0.0
        );
    }

    private double doubleValue(Object value) {
        if (value == null) {
            return 0.75;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.75;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
