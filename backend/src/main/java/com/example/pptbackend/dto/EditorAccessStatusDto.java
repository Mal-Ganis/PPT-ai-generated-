package com.example.pptbackend.dto;

import com.example.pptbackend.model.EditorAccessRequestStatus;

public class EditorAccessStatusDto {
    private EditorAccessRequestStatus status;
    private String message;

    public static EditorAccessStatusDto none() {
        EditorAccessStatusDto dto = new EditorAccessStatusDto();
        dto.setStatus(null);
        return dto;
    }

    public EditorAccessRequestStatus getStatus() {
        return status;
    }

    public void setStatus(EditorAccessRequestStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
