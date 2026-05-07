package com.example.pptbackend.dto;

import java.util.List;

public class SearchRequest {

    private Long projectId;
    private List<Float> queryEmbedding;
    private Integer topK = 5;

    public List<Float> getQueryEmbedding() {
        return queryEmbedding;
    }

    public void setQueryEmbedding(List<Float> queryEmbedding) {
        this.queryEmbedding = queryEmbedding;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
