package com.example.pptbackend.dto;

import java.util.List;

public class SearchResponse {

    private List<IndexSearchResult> results;

    public List<IndexSearchResult> getResults() {
        return results;
    }

    public void setResults(List<IndexSearchResult> results) {
        this.results = results;
    }
}
