package com.example.pptbackend.dto;

public class TopicProjectRequest {

    private String topic;
    /** 目标演讲时长（分钟），5–60；缺省 15。 */
    private Integer presentationDurationMinutes;

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
}
