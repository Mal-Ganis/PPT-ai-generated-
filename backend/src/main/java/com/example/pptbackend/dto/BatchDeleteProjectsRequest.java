package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchDeleteProjectsRequest {

    private List<Long> projectIds = new ArrayList<>();

    public List<Long> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<Long> projectIds) {
        this.projectIds = projectIds != null ? projectIds : new ArrayList<>();
    }
}
