package com.example.pptbackend.dto;

public class PptDisplayExtractionStatusDto {

    public enum Phase {
        IDLE,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private Long projectId;
    private Phase phase = Phase.IDLE;
    private int totalSlides;
    private int completedSlides;
    private String message = "";

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getTotalSlides() {
        return totalSlides;
    }

    public void setTotalSlides(int totalSlides) {
        this.totalSlides = totalSlides;
    }

    public int getCompletedSlides() {
        return completedSlides;
    }

    public void setCompletedSlides(int completedSlides) {
        this.completedSlides = completedSlides;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
