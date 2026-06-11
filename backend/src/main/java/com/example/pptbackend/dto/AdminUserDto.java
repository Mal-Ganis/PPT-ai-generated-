package com.example.pptbackend.dto;

import com.example.pptbackend.model.EditorAccessRequestStatus;
import com.example.pptbackend.model.User;
import com.example.pptbackend.model.UserRole;
import java.time.OffsetDateTime;

public class AdminUserDto {
    private Long id;
    private String username;
    private String displayName;
    private UserRole role;
    private OffsetDateTime createdAt;
    private EditorAccessRequestStatus editorAccessStatus;

    public static AdminUserDto from(User user, EditorAccessRequestStatus editorAccessStatus) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setEditorAccessStatus(editorAccessStatus);
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public EditorAccessRequestStatus getEditorAccessStatus() {
        return editorAccessStatus;
    }

    public void setEditorAccessStatus(EditorAccessRequestStatus editorAccessStatus) {
        this.editorAccessStatus = editorAccessStatus;
    }
}
