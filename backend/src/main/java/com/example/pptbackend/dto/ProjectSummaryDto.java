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
}
