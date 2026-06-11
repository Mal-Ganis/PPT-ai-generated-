package com.example.pptbackend.config;

import com.example.pptbackend.model.User;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.default-admin-username}")
    private String adminUsername;

    @Value("${app.auth.default-admin-password}")
    private String adminPassword;

    @Value("${app.auth.default-editor-username}")
    private String editorUsername;

    @Value("${app.auth.default-editor-password}")
    private String editorPassword;

    @Value("${app.auth.default-viewer-username}")
    private String viewerUsername;

    @Value("${app.auth.default-viewer-password}")
    private String viewerPassword;

    public AuthDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUser(adminUsername, "系统管理员", adminPassword, UserRole.ADMIN, true);
        if (userRepository.count() <= 3) {
            ensureUser(editorUsername, "内容编辑", editorPassword, UserRole.EDITOR, false);
            ensureUser(viewerUsername, "只读访客", viewerPassword, UserRole.VIEWER, false);
        }
    }

    /**
     * 确保内置账号存在且角色正确（避免库中已有用户时未创建 admin，或 admin 被误降为 VIEWER）。
     */
    private void ensureUser(String username,
                            String displayName,
                            String password,
                            UserRole role,
                            boolean forceRole) {
        if (username == null || username.isBlank()) {
            return;
        }
        userRepository.findByUsernameIgnoreCase(username.trim()).ifPresentOrElse(existing -> {
            if (forceRole && existing.getRole() != role) {
                existing.setRole(role);
                userRepository.save(existing);
                log.warn("已修正内置账号 {} 的角色为 {}", username, role);
            }
        }, () -> {
            User user = new User();
            user.setUsername(username.trim());
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            userRepository.save(user);
            log.info("已创建内置账号 {}（{}）", username, role);
        });
    }
}
