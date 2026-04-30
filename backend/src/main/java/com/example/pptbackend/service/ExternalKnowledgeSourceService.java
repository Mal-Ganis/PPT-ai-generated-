package com.example.pptbackend.service;

import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.ExternalSourceLoadRequest;
import com.example.pptbackend.dto.IndexSegmentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class ExternalKnowledgeSourceService {

    private static final String EXTERNAL_NEWS_API = "https://api.spaceflightnewsapi.net/v3/articles";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IndexSegmentService indexSegmentService;

    public ExternalKnowledgeSourceService(ObjectMapper objectMapper, IndexSegmentService indexSegmentService) {
        this.objectMapper = objectMapper;
        this.indexSegmentService = indexSegmentService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<ExternalSourceDocument> searchExternalSources(String query, int limit) {
        String apiUrl = buildSearchUrl(query, limit);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("External source request failed: " + response.statusCode());
            }

            List<Map<String, Object>> articles = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            return articles.stream()
                .map(this::mapToExternalDocument)
                .collect(Collectors.toList());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to fetch external source documents", e);
        }
    }

    public int loadExternalSources(ExternalSourceLoadRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery();
        int limit = request.getLimit() != null ? request.getLimit() : 3;
        Long projectId = request.getProjectId() != null ? request.getProjectId() : 0L;

        List<ExternalSourceDocument> docs = searchExternalSources(query, limit);
        for (ExternalSourceDocument doc : docs) {
            IndexSegmentRequest segmentRequest = new IndexSegmentRequest();
            segmentRequest.setProjectId(projectId);
            segmentRequest.setSegmentId("external-" + Math.abs(doc.getUrl().hashCode()));
            segmentRequest.setContent(buildSegmentContent(doc));
            segmentRequest.setMetadata(buildMetadata(doc));
            segmentRequest.setEmbedding(generateEmbedding(segmentRequest.getContent()));
            indexSegmentService.indexSegment(segmentRequest);
        }

        return docs.size();
    }

    private String buildSearchUrl(String query, int limit) {
        StringBuilder builder = new StringBuilder(EXTERNAL_NEWS_API);
        builder.append("?_limit=").append(limit);
        if (!query.isBlank()) {
            builder.append("&title_contains=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private ExternalSourceDocument mapToExternalDocument(Map<String, Object> article) {
        ExternalSourceDocument document = new ExternalSourceDocument();
        document.setTitle(stringValue(article.get("title")));
        document.setUrl(stringValue(article.get("url")));
        document.setSource(stringValue(article.get("newsSite")));
        document.setPublishedAt(stringValue(article.get("publishedAt")));
        document.setAuthor(stringValue(article.get("newsSite")));
        document.setSummary(stringValue(article.get("summary")));
        document.setTrustScore(determineTrustScore(article));
        return document;
    }

    private String buildSegmentContent(ExternalSourceDocument doc) {
        return String.format("%s\n\n%s", doc.getTitle(), doc.getSummary() != null ? doc.getSummary() : "");
    }

    private Map<String, Object> buildMetadata(ExternalSourceDocument doc) {
        return Map.of(
            "externalSource", true,
            "title", doc.getTitle(),
            "source", doc.getSource(),
            "url", doc.getUrl(),
            "publishedAt", doc.getPublishedAt(),
            "author", doc.getAuthor(),
            "summary", doc.getSummary(),
            "trustScore", doc.getTrustScore()
        );
    }

    private List<Float> generateEmbedding(String content) {
        Random random = new Random(content.hashCode());
        List<Float> embedding = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            embedding.add(random.nextFloat() * 2f - 1f);
        }
        return embedding;
    }

    private double determineTrustScore(Map<String, Object> article) {
        Object featured = article.get("featured");
        boolean isFeatured = featured instanceof Boolean && (Boolean) featured;
        return isFeatured ? 0.92 : 0.74;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
