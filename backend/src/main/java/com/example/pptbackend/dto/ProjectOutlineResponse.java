package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectOutlineResponse {

    private Long projectId;
    private String title;
    private List<OutlineSlide> slides = new ArrayList<>();

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<OutlineSlide> getSlides() {
        return slides;
    }

    public void setSlides(List<OutlineSlide> slides) {
        this.slides = slides;
    }

    public static class OutlineSlide {
        private Integer id;
        private String title;
        private String[] content;
        private String notes;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String[] getContent() {
            return content;
        }

        public void setContent(String[] content) {
            this.content = content;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
