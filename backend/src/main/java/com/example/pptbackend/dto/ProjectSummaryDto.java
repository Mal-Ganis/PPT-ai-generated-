package com.example.pptbackend.dto;

import java.time.OffsetDateTime;

public class ProjectSummaryDto {

    private Long id;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    /** 是否已有讲稿/正文要点 */
    private boolean hasScript;
    /** 是否已有 PPT 投影要点 */
    private boolean hasPpt;
    /** 列表展示：仅大纲 / 已有正文 / 可预览 */
    private String stage;
    /** 是否为管理员标记的模板（只读访客可见） */
    private boolean templateProject;
    /** 归属用户；管理员列表展示用 */
    private Long ownerUserId;
    private String ownerUsername;
    private String ownerDisplayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isHasScript() {
        return hasScript;
    }

    public void setHasScript(boolean hasScript) {
        this.hasScript = hasScript;
    }

    public boolean isHasPpt() {
        return hasPpt;
    }

    public void setHasPpt(boolean hasPpt) {
        this.hasPpt = hasPpt;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public boolean isTemplateProject() {
        return templateProject;
    }

    public void setTemplateProject(boolean templateProject) {
        this.templateProject = templateProject;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }
}
