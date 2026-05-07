package com.example.pptbackend.controller;

import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.DocumentProjectRequest;
import com.example.pptbackend.dto.GenerateSlidesRequest;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.ProjectSummaryDto;
import com.example.pptbackend.dto.SlideContentResponse;
import com.example.pptbackend.dto.TopicProjectRequest;
import com.example.pptbackend.dto.UpdateSlideRequest;
import com.example.pptbackend.service.DocumentTextExtractionService;
import com.example.pptbackend.service.ProjectService;
import com.example.pptbackend.service.SlideGenerationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final SlideGenerationService slideGenerationService;
    private final DocumentTextExtractionService documentTextExtractionService;

    public ProjectController(ProjectService projectService,
                             SlideGenerationService slideGenerationService,
                             DocumentTextExtractionService documentTextExtractionService) {
        this.projectService = projectService;
        this.slideGenerationService = slideGenerationService;
        this.documentTextExtractionService = documentTextExtractionService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectSummaryDto>> listProjects() {
        return ResponseEntity.ok(projectService.listProjects());
    }

    @PostMapping
    public ResponseEntity<Long> createProject(@RequestBody CreateProjectRequest request) {
        Long projectId = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(projectId);
    }

    @PostMapping("/topic")
    public ResponseEntity<ProjectOutlineResponse> createProjectFromTopic(@RequestBody TopicProjectRequest request) {
        ProjectOutlineResponse outline = projectService.createProjectFromTopic(request.getTopic());
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    @PostMapping("/document")
    public ResponseEntity<ProjectOutlineResponse> createProjectFromDocument(@RequestBody DocumentProjectRequest request) {
        ProjectOutlineResponse outline = projectService.createProjectFromDocument(request.getTitle(), request.getText());
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    /**
     * PDF/DOCX/TXT 服务端解析（PDF 使用 Apache PDFBox），适用于前端仅上传文件的场景。
     */
    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectOutlineResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", required = false) String title
    ) throws IOException {
        String text = documentTextExtractionService.extractText(file);
        String resolvedTitle = title != null && !title.isBlank() ? title : deriveFilenameTitle(file);
        ProjectOutlineResponse outline = projectService.createProjectFromDocument(resolvedTitle, text);
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    private static String deriveFilenameTitle(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "上传文档";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @PutMapping("/{projectId}/outline")
    public ResponseEntity<Void> replaceOutline(@PathVariable("projectId") Long projectId,
                                             @RequestBody CreateProjectRequest request) {
        projectService.replaceOutline(projectId, request);
        return ResponseEntity.noContent().build();
    }

    /** 仅更新一页，不影响项目中其它幻灯片 */
    @PatchMapping("/{projectId}/slides/{slideId}")
    public ResponseEntity<ProjectDetailResponse> patchSlide(@PathVariable("projectId") Long projectId,
                                                           @PathVariable("slideId") Long slideId,
                                                           @RequestBody UpdateSlideRequest request) {
        return ResponseEntity.ok(projectService.updateSlide(projectId, slideId, request));
    }

    @PostMapping("/{projectId}/slides/generate")
    public ResponseEntity<ProjectDetailResponse> generateSlideContents(@PathVariable("projectId") Long projectId,
                                                                       @RequestBody(required = false) GenerateSlidesRequest request) {
        GenerateSlidesRequest payload = request != null ? request : new GenerateSlidesRequest();
        slideGenerationService.generateAllSlides(projectId, payload);
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }

    @PostMapping("/{projectId}/slides/{slideId}/regenerate")
    public ResponseEntity<SlideContentResponse> regenerateSlide(@PathVariable("projectId") Long projectId,
                                                               @PathVariable("slideId") Long slideId,
                                                               @RequestBody(required = false) GenerateSlidesRequest request) {
        GenerateSlidesRequest payload = request != null ? request : new GenerateSlidesRequest();
        SlideContentResponse response = slideGenerationService.regenerateSlide(
            projectId,
            slideId,
            payload.getInputType(),
            payload.getInputContent()
        );
        return ResponseEntity.ok(response);
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
