package com.example.pptbackend.dto;

import com.example.pptbackend.model.UserRole;

public class UpdateUserRoleRequest {
    private UserRole role;

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
