package com.example.pptbackend.dto;

import com.example.pptbackend.model.EditorAccessRequest;
import com.example.pptbackend.model.EditorAccessRequestStatus;
import com.example.pptbackend.model.UserRole;
import java.time.OffsetDateTime;

public class EditorAccessRequestDto {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private UserRole currentRole;
    private EditorAccessRequestStatus status;
    private String message;
    private OffsetDateTime createdAt;

    public static EditorAccessRequestDto from(EditorAccessRequest request) {
        EditorAccessRequestDto dto = new EditorAccessRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUser().getId());
        dto.setUsername(request.getUser().getUsername());
        dto.setDisplayName(request.getUser().getDisplayName());
        dto.setCurrentRole(request.getUser().getRole());
        dto.setStatus(request.getStatus());
        dto.setMessage(request.getMessage());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UserRole getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(UserRole currentRole) {
        this.currentRole = currentRole;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
