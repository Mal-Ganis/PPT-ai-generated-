package com.example.pptbackend.service;

import com.example.pptbackend.dto.EditorAccessRequestDto;
import com.example.pptbackend.dto.EditorAccessStatusDto;
import com.example.pptbackend.dto.SubmitEditorAccessRequest;
import com.example.pptbackend.model.EditorAccessRequest;
import com.example.pptbackend.model.EditorAccessRequestStatus;
import com.example.pptbackend.model.User;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.EditorAccessRequestRepository;
import com.example.pptbackend.repository.UserRepository;
import com.example.pptbackend.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditorAccessService {

    private final EditorAccessRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public EditorAccessService(EditorAccessRequestRepository requestRepository,
                               UserRepository userRepository,
                               CurrentUserService currentUserService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public EditorAccessStatusDto submitRequest(SubmitEditorAccessRequest body) {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        User user = loadUser(principal.getId());
        if (user.getRole() != UserRole.VIEWER) {
            throw new IllegalArgumentException("当前账号无需申请编辑权限");
        }
        if (requestRepository.existsByUserAndStatus(user, EditorAccessRequestStatus.PENDING)) {
            throw new IllegalArgumentException("您已有待审批的申请，请等待管理员处理");
        }

        EditorAccessRequest request = new EditorAccessRequest();
        request.setUser(user);
        request.setStatus(EditorAccessRequestStatus.PENDING);
        if (body != null && body.getMessage() != null && !body.getMessage().isBlank()) {
            request.setMessage(body.getMessage().trim());
        }
        requestRepository.save(request);

        EditorAccessStatusDto dto = new EditorAccessStatusDto();
        dto.setStatus(EditorAccessRequestStatus.PENDING);
        dto.setMessage(request.getMessage());
        return dto;
    }

    @Transactional(readOnly = true)
    public EditorAccessStatusDto myStatus() {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        User user = loadUser(principal.getId());
        if (user.getRole() != UserRole.VIEWER) {
            return EditorAccessStatusDto.none();
        }
        return requestRepository.findTopByUserOrderByCreatedAtDesc(user)
            .map(this::toStatusDto)
            .orElseGet(EditorAccessStatusDto::none);
    }

    @Transactional(readOnly = true)
    public List<EditorAccessRequestDto> listPending() {
        return requestRepository.findByStatusOrderByCreatedAtAsc(EditorAccessRequestStatus.PENDING)
            .stream()
            .map(EditorAccessRequestDto::from)
            .toList();
    }

    @Transactional
    public EditorAccessRequestDto approve(Long requestId) {
        UserPrincipal admin = currentUserService.requireAuthenticated();
        if (admin.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("仅管理员可审批");
        }
        EditorAccessRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
        if (request.getStatus() != EditorAccessRequestStatus.PENDING) {
            throw new IllegalArgumentException("该申请已处理");
        }
        User user = request.getUser();
        user.setRole(UserRole.EDITOR);
        userRepository.save(user);

        request.setStatus(EditorAccessRequestStatus.APPROVED);
        request.setReviewedBy(loadUser(admin.getId()));
        request.setReviewedAt(OffsetDateTime.now());
        requestRepository.save(request);
        return EditorAccessRequestDto.from(request);
    }

    @Transactional
    public EditorAccessRequestDto reject(Long requestId) {
        UserPrincipal admin = currentUserService.requireAuthenticated();
        if (admin.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("仅管理员可审批");
        }
        EditorAccessRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
        if (request.getStatus() != EditorAccessRequestStatus.PENDING) {
            throw new IllegalArgumentException("该申请已处理");
        }
        request.setStatus(EditorAccessRequestStatus.REJECTED);
        request.setReviewedBy(loadUser(admin.getId()));
        request.setReviewedAt(OffsetDateTime.now());
        requestRepository.save(request);
        return EditorAccessRequestDto.from(request);
    }

    @Transactional(readOnly = true)
    public EditorAccessRequestStatus latestStatusForUser(User user) {
        if (user.getRole() != UserRole.VIEWER) {
            return null;
        }
        return requestRepository.findTopByUserOrderByCreatedAtDesc(user)
            .map(EditorAccessRequest::getStatus)
            .orElse(null);
    }

    private EditorAccessStatusDto toStatusDto(EditorAccessRequest request) {
        EditorAccessStatusDto dto = new EditorAccessStatusDto();
        dto.setStatus(request.getStatus());
        dto.setMessage(request.getMessage());
        return dto;
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
}
