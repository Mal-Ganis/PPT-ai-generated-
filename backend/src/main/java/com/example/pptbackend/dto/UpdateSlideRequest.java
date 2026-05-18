package com.example.pptbackend.dto;

import java.util.List;

/** 仅更新某一页幻灯片，不影响项目内其他页 */
public class UpdateSlideRequest {

    private String title;
    private String chapter;
    private String body;
    private List<String> bullets;
    private List<String> pptBullets;
    private List<String> sources;
    private String notes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
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
