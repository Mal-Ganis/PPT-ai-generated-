package com.example.pptbackend.dto;

import com.example.pptbackend.model.UserRole;

public class CreateInviteCodeRequest {
    private UserRole role;
    private Integer maxUses;
    private String note;

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
