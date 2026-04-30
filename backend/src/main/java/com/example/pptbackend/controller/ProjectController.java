package com.example.pptbackend.controller;

import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.TopicProjectRequest;
import com.example.pptbackend.service.OutlineGenerationService;
import com.example.pptbackend.service.ProjectService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final OutlineGenerationService outlineGenerationService;

    public ProjectController(ProjectService projectService, OutlineGenerationService outlineGenerationService) {
        this.projectService = projectService;
        this.outlineGenerationService = outlineGenerationService;
    }

    @PostMapping
    public ResponseEntity<Long> createProject(@RequestBody CreateProjectRequest request) {
        Long projectId = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(projectId);
    }

    @PostMapping("/topic")
    public ResponseEntity<ProjectOutlineResponse> createProjectFromTopic(@RequestBody TopicProjectRequest request) {
        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(request.getTopic());

        CreateProjectRequest createRequest = new CreateProjectRequest();
        createRequest.setTitle(request.getTopic());
        createRequest.setTheme(request.getTopic());

        List<CreateProjectRequest.SlideItem> slides = outline.getSlides().stream().map(item -> {
            CreateProjectRequest.SlideItem slideItem = new CreateProjectRequest.SlideItem();
            slideItem.setPosition(item.getId() != null ? item.getId() : 0);
            slideItem.setTitle(item.getTitle());
            slideItem.setBullets(item.getContent() != null ? Arrays.asList(item.getContent()) : List.of());
            slideItem.setNotes(item.getNotes());
            return slideItem;
        }).toList();

        createRequest.setSlides(slides);
        Long projectId = projectService.createProject(createRequest);
        outline.setProjectId(projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getProject(@PathVariable("projectId") Long projectId) {
        ProjectDetailResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }
}
