package com.example.pptbackend.service;

import com.example.pptbackend.dto.AuthResponse;
import com.example.pptbackend.dto.LoginRequest;
import com.example.pptbackend.dto.RegisterRequest;
import com.example.pptbackend.dto.UserProfileDto;
import com.example.pptbackend.model.InviteCode;
import com.example.pptbackend.model.User;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.UserRepository;
import com.example.pptbackend.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final InviteCodeService inviteCodeService;
    private final EditorAccessService editorAccessService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       CurrentUserService currentUserService,
                       InviteCodeService inviteCodeService,
                       EditorAccessService editorAccessService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
        this.inviteCodeService = inviteCodeService;
        this.editorAccessService = editorAccessService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("请输入用户名");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("请输入密码");
        }
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        User user = loadUser(principal.getId());
        return new AuthResponse(token, toProfile(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("请输入用户名");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        UserRole role = UserRole.VIEWER;
        InviteCode invite = null;
        if (request.getInviteCode() != null && !request.getInviteCode().isBlank()) {
            invite = inviteCodeService.resolveForRegistration(request.getInviteCode());
            role = invite.getRole();
        }

        User user = new User();
        user.setUsername(username);
        user.setDisplayName(
            request.getDisplayName() != null && !request.getDisplayName().isBlank()
                ? request.getDisplayName().trim()
                : username
        );
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        userRepository.save(user);

        if (invite != null) {
            inviteCodeService.consume(invite);
        }

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfileDto me() {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        return toProfile(loadUser(principal.getId()));
    }

    private UserProfileDto toProfile(User user) {
        return UserProfileDto.from(user, editorAccessService.latestStatusForUser(user));
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
}
