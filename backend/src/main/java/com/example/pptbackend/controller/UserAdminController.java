package com.example.pptbackend.controller;

import com.example.pptbackend.dto.AdminUserDto;
import com.example.pptbackend.dto.CreateInviteCodeRequest;
import com.example.pptbackend.dto.EditorAccessRequestDto;
import com.example.pptbackend.dto.EditorAccessStatusDto;
import com.example.pptbackend.dto.InviteCodeDto;
import com.example.pptbackend.dto.SubmitEditorAccessRequest;
import com.example.pptbackend.dto.UpdateProjectTemplateRequest;
import com.example.pptbackend.dto.UpdateUserRoleRequest;
import com.example.pptbackend.service.EditorAccessService;
import com.example.pptbackend.service.ProjectService;
import com.example.pptbackend.service.UserAdminService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final EditorAccessService editorAccessService;
    private final ProjectService projectService;

    public UserAdminController(UserAdminService userAdminService,
                               EditorAccessService editorAccessService,
                               ProjectService projectService) {
        this.userAdminService = userAdminService;
        this.editorAccessService = editorAccessService;
        this.projectService = projectService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> listUsers() {
        return ResponseEntity.ok(userAdminService.listUsers());
    }

    @PatchMapping("/users/{userId:\\d+}/role")
    public ResponseEntity<AdminUserDto> updateUserRole(@PathVariable("userId") Long userId,
                                                       @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(userAdminService.updateUserRole(userId, request));
    }

    @GetMapping("/editor-access-requests")
    public ResponseEntity<List<EditorAccessRequestDto>> listPendingEditorRequests() {
        return ResponseEntity.ok(editorAccessService.listPending());
    }

    @PostMapping("/editor-access-requests/{requestId:\\d+}/approve")
    public ResponseEntity<EditorAccessRequestDto> approveEditorRequest(@PathVariable("requestId") Long requestId) {
        return ResponseEntity.ok(editorAccessService.approve(requestId));
    }

    @PostMapping("/editor-access-requests/{requestId:\\d+}/reject")
    public ResponseEntity<EditorAccessRequestDto> rejectEditorRequest(@PathVariable("requestId") Long requestId) {
        return ResponseEntity.ok(editorAccessService.reject(requestId));
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<InviteCodeDto>> listInviteCodes() {
        return ResponseEntity.ok(userAdminService.listInviteCodes());
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCodeDto> createInviteCode(@RequestBody CreateInviteCodeRequest request) {
        return ResponseEntity.ok(userAdminService.createInviteCode(request));
    }

    @PostMapping("/invite-codes/{id:\\d+}/deactivate")
    public ResponseEntity<InviteCodeDto> deactivateInviteCode(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userAdminService.deactivateInviteCode(id));
    }

    @PatchMapping("/projects/{projectId:\\d+}/template")
    public ResponseEntity<com.example.pptbackend.dto.ProjectSummaryDto> updateProjectTemplate(
        @PathVariable("projectId") Long projectId,
        @RequestBody UpdateProjectTemplateRequest request
    ) {
        boolean template = request != null && Boolean.TRUE.equals(request.getTemplateProject());
        return ResponseEntity.ok(projectService.updateProjectTemplate(projectId, template));
    }
}
