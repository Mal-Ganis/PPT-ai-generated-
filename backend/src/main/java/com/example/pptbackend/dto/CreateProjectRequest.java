package com.example.pptbackend.dto;

import java.util.List;

public class CreateProjectRequest {

    private String title;
    private String theme;
    private List<SlideItem> slides;

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

    public List<SlideItem> getSlides() {
        return slides;
    }

    public void setSlides(List<SlideItem> slides) {
        this.slides = slides;
    }

    public static class SlideItem {
        private Integer position;
        private String chapter;
        private String title;
        private String body;
        private List<String> bullets;
        private List<String> sources;
        private String notes;

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
