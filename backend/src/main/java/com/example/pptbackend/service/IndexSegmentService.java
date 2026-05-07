package com.example.pptbackend.service;

import com.example.pptbackend.dto.IndexSearchResult;
import com.example.pptbackend.dto.IndexSegmentRequest;
import com.example.pptbackend.dto.SearchRequest;
import com.example.pptbackend.dto.SearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IndexSegmentService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;

    public IndexSegmentService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public Long indexSegment(IndexSegmentRequest request) {
        validateSegmentRequest(request);

        String metadataJson = serializeMetadata(request.getMetadata());
        String vectorLiteral = toVectorLiteral(request.getEmbedding());

        String sql = "INSERT INTO index_segments (project_id, segment_id, content, metadata, embedding, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?::jsonb, ?::vector, now(), now()) RETURNING id";

        return jdbcTemplate.queryForObject(sql,
            new Object[]{request.getProjectId(), request.getSegmentId(), request.getContent(), metadataJson, vectorLiteral},
            Long.class);
    }

    @Transactional(readOnly = true)
    public SearchResponse searchByText(Long projectId, String query, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        SearchRequest request = new SearchRequest();
        request.setProjectId(projectId);
        request.setQueryEmbedding(embeddingService.embed(query));
        request.setTopK(topK != null && topK > 0 ? topK : 5);
        return search(request);
    }

    @Transactional(readOnly = true)
    public SearchResponse search(SearchRequest request) {
        validateSearchRequest(request);

        String vectorLiteral = toVectorLiteral(request.getQueryEmbedding());
        int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 5;

        String sql;
        Object[] params;
        if (request.getProjectId() != null) {
            sql = "SELECT id, project_id, segment_id, content, metadata, embedding <=> ?::vector AS distance "
                + "FROM index_segments "
                + "WHERE project_id = ? "
                + "ORDER BY distance ASC "
                + "LIMIT ?";
            params = new Object[]{vectorLiteral, request.getProjectId(), topK};
        } else {
            sql = "SELECT id, project_id, segment_id, content, metadata, embedding <=> ?::vector AS distance "
                + "FROM index_segments "
                + "ORDER BY distance ASC "
                + "LIMIT ?";
            params = new Object[]{vectorLiteral, topK};
        }

        List<IndexSearchResult> results = jdbcTemplate.query(sql, params, rowMapper());

        SearchResponse response = new SearchResponse();
        response.setResults(results);
        return response;
    }

    private void validateSegmentRequest(IndexSegmentRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (request.getSegmentId() == null || request.getSegmentId().isBlank()) {
            throw new IllegalArgumentException("segmentId is required");
        }
        if (request.getEmbedding() == null || request.getEmbedding().isEmpty()) {
            throw new IllegalArgumentException("embedding is required");
        }
    }

    private void validateSearchRequest(SearchRequest request) {
        if (request.getQueryEmbedding() == null || request.getQueryEmbedding().isEmpty()) {
            throw new IllegalArgumentException("queryEmbedding is required");
        }
    }

    private String serializeMetadata(Object metadata) {
        if (metadata == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize metadata", e);
        }
    }

    private String toVectorLiteral(List<Float> embedding) {
        return embedding.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private RowMapper<IndexSearchResult> rowMapper() {
        return (rs, rowNum) -> {
            IndexSearchResult result = new IndexSearchResult();
            result.setId(rs.getLong("id"));
            result.setProjectId(rs.getLong("project_id"));
            result.setSegmentId(rs.getString("segment_id"));
            result.setContent(rs.getString("content"));
            result.setMetadata(rs.getString("metadata"));
            result.setDistance(rs.getDouble("distance"));
            return result;
        };
    }
}
