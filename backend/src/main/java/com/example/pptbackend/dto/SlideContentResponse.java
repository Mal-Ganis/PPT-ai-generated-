package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class SlideContentResponse {

    private List<String> content = new ArrayList<>();
    private String notes = "";
    private List<String> sources = new ArrayList<>();

    public List<String> getContent() {
        return content;
    }

    public void setContent(List<String> content) {
        this.content = content;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }
}
