package com.example.pptbackend.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.example.pptbackend.dto.EvaluationReportResponse;

public class ProjectDetailResponse {

    private Long id;
    private String title;
    private String theme;
    private Integer presentationDurationMinutes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<SlideItem> slides;
    private List<EvaluationReportResponse> evaluations;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Integer getPresentationDurationMinutes() {
        return presentationDurationMinutes;
    }

    public void setPresentationDurationMinutes(Integer presentationDurationMinutes) {
        this.presentationDurationMinutes = presentationDurationMinutes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SlideItem> getSlides() {
        return slides;
    }

    public void setSlides(List<SlideItem> slides) {
        this.slides = slides;
    }

    public List<EvaluationReportResponse> getEvaluations() {
        return evaluations;
    }

    public void setEvaluations(List<EvaluationReportResponse> evaluations) {
        this.evaluations = evaluations;
    }

    public static class SlideItem {
        private Long id;
        private Integer position;
        private String chapter;
        private String title;
        private String body;
        private List<String> bullets;
        private List<String> pptBullets;
        private List<String> sources;
        private String notes;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        public String getChapter() {
            return chapter;
        }

        public void setChapter(String chapter) {
            this.chapter = chapter;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public List<String> getBullets() {
            return bullets;
        }

        public void setBullets(List<String> bullets) {
            this.bullets = bullets;
        }

        public List<String> getPptBullets() {
            return pptBullets;
        }

        public void setPptBullets(List<String> pptBullets) {
            this.pptBullets = pptBullets;
        }

        public List<String> getSources() {
            return sources;
        }

        public void setSources(List<String> sources) {
            this.sources = sources;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
