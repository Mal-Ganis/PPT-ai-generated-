package com.example.pptbackend.service;

import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public UserPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("请先登录");
        }
        return principal;
    }

    public UserPrincipal optionalAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public boolean isAdmin() {
        UserPrincipal principal = optionalAuthenticated();
        return principal != null && principal.getRole() == UserRole.ADMIN;
    }

    public boolean canWriteProjects() {
        UserPrincipal principal = optionalAuthenticated();
        if (principal == null) {
            return false;
        }
        UserRole role = principal.getRole();
        return role == UserRole.ADMIN || role == UserRole.EDITOR;
    }
}
