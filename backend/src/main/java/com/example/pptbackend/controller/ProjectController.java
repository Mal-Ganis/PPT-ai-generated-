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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;

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
        if (request == null || request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("请求体须包含非空的 topic 字段，例如：{\"topic\":\"你的主题\"}");
        }
        ProjectOutlineResponse outline = projectService.createProjectFromTopic(request.getTopic());
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    /**
     * 浏览器地址栏访问本路径会使用 GET，而创建大纲仅支持 POST。提供说明 JSON，避免 Whitelabel 405 页面。
     */
    @GetMapping(value = "/topic", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> topicEndpointBrowserHint() {
        return ResponseEntity.ok(Map.of(
            "message",
            "创建主题大纲请使用 POST（本页为 GET 说明）。Content-Type: application/json，请求体示例：{\"topic\":\"你的主题\"}。请在应用内操作，或使用 Postman/curl。",
            "method", "POST",
            "path", "/api/projects/topic",
            "contentType", "application/json",
            "exampleBody", "{\"topic\":\"你的主题\"}"));
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

    @PutMapping("/{projectId:\\d+}/outline")
    public ResponseEntity<Void> replaceOutline(@PathVariable("projectId") Long projectId,
                                             @RequestBody CreateProjectRequest request) {
        projectService.replaceOutline(projectId, request);
        return ResponseEntity.noContent().build();
    }

    /** 仅更新一页，不影响项目中其它幻灯片 */
    @PatchMapping("/{projectId:\\d+}/slides/{slideId:\\d+}")
    public ResponseEntity<ProjectDetailResponse> patchSlide(@PathVariable("projectId") Long projectId,
                                                           @PathVariable("slideId") Long slideId,
                                                           @RequestBody UpdateSlideRequest request) {
        return ResponseEntity.ok(projectService.updateSlide(projectId, slideId, request));
    }

    @PostMapping("/{projectId:\\d+}/slides/generate")
    public ResponseEntity<ProjectDetailResponse> generateSlideContents(@PathVariable("projectId") Long projectId,
                                                                       @RequestBody(required = false) GenerateSlidesRequest request) {
        GenerateSlidesRequest payload = request != null ? request : new GenerateSlidesRequest();
        slideGenerationService.generateAllSlides(projectId, payload);
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }

    @PostMapping("/{projectId:\\d+}/slides/{slideId:\\d+}/regenerate")
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

    @GetMapping("/{projectId:\\d+}")
    public ResponseEntity<ProjectDetailResponse> getProject(@PathVariable("projectId") Long projectId) {
        ProjectDetailResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(response);
    }
}
