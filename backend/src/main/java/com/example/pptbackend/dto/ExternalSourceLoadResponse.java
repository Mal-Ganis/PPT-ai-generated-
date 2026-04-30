package com.example.pptbackend.dto;

public class ExternalSourceLoadResponse {

    private int loadedCount;

    public ExternalSourceLoadResponse() {
    }

    public ExternalSourceLoadResponse(int loadedCount) {
        this.loadedCount = loadedCount;
    }

    public int getLoadedCount() {
        return loadedCount;
    }

    public void setLoadedCount(int loadedCount) {
        this.loadedCount = loadedCount;
    }
}
