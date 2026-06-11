package com.example.pptbackend.service;

import com.example.pptbackend.model.InviteCode;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.InviteCodeRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteCodeService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteCodeRepository inviteCodeRepository;

    public InviteCodeService(InviteCodeRepository inviteCodeRepository) {
        this.inviteCodeRepository = inviteCodeRepository;
    }

    @Transactional(readOnly = true)
    public InviteCode resolveForRegistration(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        InviteCode invite = inviteCodeRepository.findByCodeIgnoreCase(rawCode.trim())
            .orElseThrow(() -> new IllegalArgumentException("邀请码无效或已失效"));
        if (!invite.isActive()) {
            throw new IllegalArgumentException("邀请码无效或已失效");
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new IllegalArgumentException("邀请码已达使用上限");
        }
        if (invite.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("该邀请码不可用于公开注册");
        }
        return invite;
    }

    @Transactional
    public void consume(InviteCode invite) {
        invite.setUsedCount(invite.getUsedCount() + 1);
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            invite.setActive(false);
        }
        inviteCodeRepository.save(invite);
    }

    @Transactional
    public InviteCode createInviteCode(UserRole role, int maxUses, String note, Long createdById) {
        UserRole safeRole = role != null ? role : UserRole.EDITOR;
        if (safeRole == UserRole.ADMIN) {
            throw new IllegalArgumentException("不可创建管理员邀请码");
        }
        int uses = Math.max(1, Math.min(maxUses, 1000));
        InviteCode invite = new InviteCode();
        invite.setCode(generateUniqueCode());
        invite.setRole(safeRole);
        invite.setMaxUses(uses);
        invite.setUsedCount(0);
        invite.setActive(true);
        invite.setNote(note != null && !note.isBlank() ? note.trim() : null);
        invite.setCreatedById(createdById);
        return inviteCodeRepository.save(invite);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = randomCode(10);
            if (inviteCodeRepository.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一邀请码，请重试");
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
