package com.example.pptbackend.service;

import com.example.pptbackend.dto.IndexSearchResult;
import com.example.pptbackend.dto.IndexSegmentRequest;
import com.example.pptbackend.dto.SearchRequest;
import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIndexingService {

    private static final int CHUNK_SIZE = 900;

    private final IndexSegmentService indexSegmentService;
    private final EmbeddingService embeddingService;
    private final SystemConfigService systemConfigService;

    public DocumentIndexingService(IndexSegmentService indexSegmentService,
                                   EmbeddingService embeddingService,
                                   SystemConfigService systemConfigService) {
        this.indexSegmentService = indexSegmentService;
        this.embeddingService = embeddingService;
        this.systemConfigService = systemConfigService;
    }

    public void indexPlainText(Long projectId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> chunks = chunkForIndexing(text);
        int index = 0;
        for (String chunk : chunks) {
            IndexSegmentRequest request = new IndexSegmentRequest();
            request.setProjectId(projectId);
            request.setSegmentId("doc-" + (++index));
            request.setContent(chunk);
            request.setMetadata(Map.of(
                "sourceType", "upload",
                "title", "Uploaded document",
                "url", "",
                "trustScore", 0.85
            ));
            request.setEmbedding(embeddingService.embed(chunk));
            indexSegmentService.indexSegment(request);
        }
    }

    public String buildRagContext(Long projectId, String queryText) {
        SystemConfigDto config = systemConfigService.getSystemConfig();
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setProjectId(projectId);
        searchRequest.setQueryEmbedding(embeddingService.embed(queryText));
        searchRequest.setTopK(config.getRetrievalLimit());
        SearchResponse response = indexSegmentService.search(searchRequest);
        return formatSearchResults(response);
    }

    private String formatSearchResults(SearchResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int n = 1;
        for (IndexSearchResult result : response.getResults()) {
            builder.append(n++)
                .append(". ")
                .append(result.getContent() != null ? result.getContent().trim() : "")
                .append("\n   relevance(distance)=")
                .append(String.format("%.4f", result.getDistance()))
                .append("\n");
        }
        return builder.toString().trim();
    }

    private List<String> chunkForIndexing(String text) {
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int breakPoint = normalized.lastIndexOf('\n', end);
                if (breakPoint > start + CHUNK_SIZE / 2) {
                    end = breakPoint;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            start = end;
        }
        if (chunks.isEmpty()) {
            chunks.add(normalized);
        }
        return chunks;
    }
}
