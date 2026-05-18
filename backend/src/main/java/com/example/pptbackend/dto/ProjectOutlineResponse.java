package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectOutlineResponse {

    private Long projectId;
    private String title;
    private Integer presentationDurationMinutes;
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

    public Integer getPresentationDurationMinutes() {
        return presentationDurationMinutes;
    }

    public void setPresentationDurationMinutes(Integer presentationDurationMinutes) {
        this.presentationDurationMinutes = presentationDurationMinutes;
    }

    public List<OutlineSlide> getSlides() {
        return slides;
    }

    public void setSlides(List<OutlineSlide> slides) {
        this.slides = slides;
    }

    public static class OutlineSlide {
        /** Persisted slide primary key (ILF-1). */
        private Long slideId;
        /** Display order / legacy outline id from LLM JSON. */
        private Integer id;
        /** 章节名（同一章节可对应多页幻灯片）。 */
        private String chapter;
        private String title;
        private String[] content;
        private String notes;

        public Long getSlideId() {
            return slideId;
        }

        public void setSlideId(Long slideId) {
            this.slideId = slideId;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
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
