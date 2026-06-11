package com.example.pptbackend.service;

import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.UserRole;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.security.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    public ProjectAccessService(ProjectRepository projectRepository, CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
    }

    public void assignOwnerOnCreate(Project project) {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        project.setOwnerUserId(principal.getId());
        project.setTemplateProject(false);
    }

    public List<Project> listProjectsForCurrentUser() {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        UserPrincipal principal = currentUserService.requireAuthenticated();
        if (principal.getRole() == UserRole.ADMIN) {
            return projectRepository.findAll(sort);
        }
        if (principal.getRole() == UserRole.VIEWER) {
            return projectRepository.findByTemplateProjectTrue(sort);
        }
        return projectRepository.findByOwnerUserId(principal.getId(), sort);
    }

    public Project requireReadableProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        assertReadable(project);
        return project;
    }

    public void assertReadable(Project project) {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        if (principal.getRole() == UserRole.ADMIN) {
            return;
        }
        if (principal.getRole() == UserRole.VIEWER) {
            if (project.isTemplateProject()) {
                return;
            }
            throw new AccessDeniedException("只读访客仅可查看管理员标记的模板项目");
        }
        Long ownerId = project.getOwnerUserId();
        if (ownerId != null && ownerId.equals(principal.getId())) {
            return;
        }
        throw new AccessDeniedException("无权访问该项目");
    }

    public void assertWritable(Project project) {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        if (principal.getRole() == UserRole.ADMIN) {
            return;
        }
        if (principal.getRole() == UserRole.VIEWER) {
            throw new AccessDeniedException("只读账号无法修改项目");
        }
        if (project.isTemplateProject()) {
            throw new AccessDeniedException("模板项目仅系统管理员可修改");
        }
        Long ownerId = project.getOwnerUserId();
        if (ownerId != null && ownerId.equals(principal.getId())) {
            return;
        }
        throw new AccessDeniedException("无权修改该项目");
    }

    public void assertDeletable(Project project) {
        UserPrincipal principal = currentUserService.requireAuthenticated();
        if (principal.getRole() == UserRole.ADMIN) {
            return;
        }
        if (principal.getRole() != UserRole.EDITOR) {
            throw new AccessDeniedException("无权删除项目");
        }
        if (project.isTemplateProject()) {
            throw new AccessDeniedException("模板项目仅系统管理员可删除");
        }
        Long ownerId = project.getOwnerUserId();
        if (ownerId != null && ownerId.equals(principal.getId())) {
            return;
        }
        throw new AccessDeniedException("只能删除自己创建的项目");
    }
}
