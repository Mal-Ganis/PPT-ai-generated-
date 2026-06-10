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
        if (userRepository.count() > 0) {
            return;
        }
        createUser(adminUsername, "系统管理员", adminPassword, UserRole.ADMIN);
        createUser(editorUsername, "内容编辑", editorPassword, UserRole.EDITOR);
        createUser(viewerUsername, "只读访客", viewerPassword, UserRole.VIEWER);
        log.info("已初始化演示账号：admin / editor / viewer（详见 application.yml 默认密码）");
    }

    private void createUser(String username, String displayName, String password, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);
    }
}
