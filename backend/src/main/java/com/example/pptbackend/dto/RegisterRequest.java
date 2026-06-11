package com.example.pptbackend.dto;

import com.example.pptbackend.model.UserRole;

public class RegisterRequest {
    private String username;
    private String password;
    private String displayName;
    /** 注册时可选；有效则按邀请码授予 EDITOR 等角色，否则为 VIEWER */
    private String inviteCode;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
