package com.example.pptbackend.dto;

import com.example.pptbackend.model.InviteCode;
import com.example.pptbackend.model.UserRole;
import java.time.OffsetDateTime;

public class InviteCodeDto {
    private Long id;
    private String code;
    private UserRole role;
    private int maxUses;
    private int usedCount;
    private boolean active;
    private String note;
    private OffsetDateTime createdAt;

    public static InviteCodeDto from(InviteCode invite) {
        InviteCodeDto dto = new InviteCodeDto();
        dto.setId(invite.getId());
        dto.setCode(invite.getCode());
        dto.setRole(invite.getRole());
        dto.setMaxUses(invite.getMaxUses());
        dto.setUsedCount(invite.getUsedCount());
        dto.setActive(invite.isActive());
        dto.setNote(invite.getNote());
        dto.setCreatedAt(invite.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
