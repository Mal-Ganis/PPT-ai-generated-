package com.example.pptbackend.service;

import com.example.pptbackend.dto.AdminUserDto;
import com.example.pptbackend.dto.CreateInviteCodeRequest;
import com.example.pptbackend.dto.InviteCodeDto;
import com.example.pptbackend.dto.UpdateUserRoleRequest;
import com.example.pptbackend.model.InviteCode;
import com.example.pptbackend.model.User;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.InviteCodeRepository;
import com.example.pptbackend.repository.UserRepository;
import com.example.pptbackend.security.UserPrincipal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeService inviteCodeService;
    private final EditorAccessService editorAccessService;
    private final CurrentUserService currentUserService;

    public UserAdminService(UserRepository userRepository,
                            InviteCodeRepository inviteCodeRepository,
                            InviteCodeService inviteCodeService,
                            EditorAccessService editorAccessService,
                            CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.inviteCodeService = inviteCodeService;
        this.editorAccessService = editorAccessService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers() {
        return userRepository.findAll().stream()
            .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(user -> AdminUserDto.from(user, editorAccessService.latestStatusForUser(user)))
            .toList();
    }

    @Transactional
    public AdminUserDto updateUserRole(Long userId, UpdateUserRoleRequest request) {
        assertAdmin();
        if (request == null || request.getRole() == null) {
            throw new IllegalArgumentException("请指定角色");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserPrincipal admin = currentUserService.requireAuthenticated();
        if (user.getId().equals(admin.getId()) && request.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("不能降低自己的管理员权限");
        }
        user.setRole(request.getRole());
        userRepository.save(user);
        return AdminUserDto.from(user, editorAccessService.latestStatusForUser(user));
    }

    @Transactional(readOnly = true)
    public List<InviteCodeDto> listInviteCodes() {
        assertAdmin();
        return inviteCodeRepository.findAll().stream()
            .sorted(Comparator.comparing(InviteCode::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(InviteCodeDto::from)
            .toList();
    }

    @Transactional
    public InviteCodeDto createInviteCode(CreateInviteCodeRequest request) {
        UserPrincipal admin = currentUserService.requireAuthenticated();
        assertAdmin();
        int maxUses = request != null && request.getMaxUses() != null ? request.getMaxUses() : 1;
        String note = request != null ? request.getNote() : null;
        UserRole role = request != null ? request.getRole() : UserRole.EDITOR;
        InviteCode invite = inviteCodeService.createInviteCode(role, maxUses, note, admin.getId());
        return InviteCodeDto.from(invite);
    }

    @Transactional
    public InviteCodeDto deactivateInviteCode(Long id) {
        assertAdmin();
        InviteCode invite = inviteCodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("邀请码不存在"));
        invite.setActive(false);
        inviteCodeRepository.save(invite);
        return InviteCodeDto.from(invite);
    }

    private void assertAdmin() {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        if (principal.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("仅管理员可执行此操作");
        }
    }
}
