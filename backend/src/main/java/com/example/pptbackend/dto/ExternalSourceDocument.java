package com.example.pptbackend.dto;

public class ExternalSourceDocument {

    private String title;
    private String source;
    private String author;
    private String publishedAt;
    private String summary;
    private String url;
    private Double trustScore;
    /** tavily | mediawiki */
    private String sourceType;
    /** 与权威检索得分对应，0~1 */
    private Double credibilityScore;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Double getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(Double trustScore) {
        this.trustScore = trustScore;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Double getCredibilityScore() {
        return credibilityScore;
    }

    public void setCredibilityScore(Double credibilityScore) {
        this.credibilityScore = credibilityScore;
    }
}
