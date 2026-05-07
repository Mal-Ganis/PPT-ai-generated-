package com.example.pptbackend.dto;

public class GenerateSlidesRequest {

    private String inputType = "topic";
    private String inputContent = "";

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getInputContent() {
        return inputContent;
    }

    public void setInputContent(String inputContent) {
        this.inputContent = inputContent;
    }
}
