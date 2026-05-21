package com.example.pptbackend.dto;

/**
 * 在已有项目上按主题/文档上下文重新生成大纲。
 */
public class RegenerateOutlineRequest {

    /** 演示主题；缺省时使用项目已保存的 theme */
    private String topic;
    private Integer presentationDurationMinutes;
    /** topic | document，与正文生成一致 */
    private String inputType;
    private String inputContent;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getPresentationDurationMinutes() {
        return presentationDurationMinutes;
    }

    public void setPresentationDurationMinutes(Integer presentationDurationMinutes) {
        this.presentationDurationMinutes = presentationDurationMinutes;
    }

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
