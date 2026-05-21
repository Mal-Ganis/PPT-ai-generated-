package com.example.pptbackend.controller;

import com.example.pptbackend.dto.BatchDeleteProjectsRequest;
import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.DocumentProjectRequest;
import com.example.pptbackend.dto.GenerateSlidesRequest;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.ProjectSummaryDto;
import com.example.pptbackend.dto.SlideContentResponse;
import com.example.pptbackend.dto.SlideGenerationStatusDto;
import com.example.pptbackend.dto.RegenerateOutlineRequest;
import com.example.pptbackend.dto.TopicProjectRequest;
import com.example.pptbackend.dto.UpdateSlideRequest;
import com.example.pptbackend.service.DocumentTextExtractionService;
import com.example.pptbackend.service.ProjectService;
import com.example.pptbackend.dto.PptDisplayExtractionStatusDto;
import com.example.pptbackend.service.PptDisplayExtractionOrchestrator;
import com.example.pptbackend.service.PptDisplayExtractionService;
import com.example.pptbackend.service.SlideGenerationOrchestrator;
import com.example.pptbackend.service.SlideGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final SlideGenerationOrchestrator slideGenerationOrchestrator;
    private final PptDisplayExtractionService pptDisplayExtractionService;
    private final PptDisplayExtractionOrchestrator pptDisplayExtractionOrchestrator;
    private final DocumentTextExtractionService documentTextExtractionService;

    public ProjectController(ProjectService projectService,
                             SlideGenerationService slideGenerationService,
                             SlideGenerationOrchestrator slideGenerationOrchestrator,
                             PptDisplayExtractionService pptDisplayExtractionService,
                             PptDisplayExtractionOrchestrator pptDisplayExtractionOrchestrator,
                             DocumentTextExtractionService documentTextExtractionService) {
        this.projectService = projectService;
        this.slideGenerationService = slideGenerationService;
        this.slideGenerationOrchestrator = slideGenerationOrchestrator;
        this.pptDisplayExtractionService = pptDisplayExtractionService;
        this.pptDisplayExtractionOrchestrator = pptDisplayExtractionOrchestrator;
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
        ProjectOutlineResponse outline = projectService.createProjectFromTopic(
            request.getTopic(),
            request.getPresentationDurationMinutes());
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
        ProjectOutlineResponse outline = projectService.createProjectFromDocument(
            request.getTitle(),
            request.getText(),
            request.getPresentationDurationMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(outline);
    }

    /**
     * PDF/DOCX/TXT 服务端解析（PDF 使用 Apache PDFBox），适用于前端仅上传文件的场景。
     */
    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectOutlineResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "presentationDurationMinutes", required = false) Integer presentationDurationMinutes
    ) throws IOException {
        String text = documentTextExtractionService.extractText(file);
        String resolvedTitle = title != null && !title.isBlank() ? title : deriveFilenameTitle(file);
        ProjectOutlineResponse outline = projectService.createProjectFromDocument(
            resolvedTitle, text, presentationDurationMinutes);
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

    /** 在已有项目上按主题重新生成大纲（替换当前幻灯片列表） */
    @PostMapping("/{projectId:\\d+}/outline/regenerate")
    public ResponseEntity<ProjectOutlineResponse> regenerateOutline(
        @PathVariable("projectId") Long projectId,
        @RequestBody RegenerateOutlineRequest request
    ) {
        return ResponseEntity.ok(projectService.regenerateOutline(projectId, request));
    }

    /** 仅更新一页，不影响项目中其它幻灯片 */
    @PatchMapping("/{projectId:\\d+}/slides/{slideId:\\d+}")
    public ResponseEntity<ProjectDetailResponse> patchSlide(@PathVariable("projectId") Long projectId,
                                                           @PathVariable("slideId") Long slideId,
                                                           @RequestBody UpdateSlideRequest request) {
        return ResponseEntity.ok(projectService.updateSlide(projectId, slideId, request));
    }

    /**
     * 异步启动正文生成：立即返回 202，避免长连接在写回超大 JSON 时被客户端/代理断开。
     * 前端请轮询 {@link #getSlideGenerationStatus}，完成后用 {@link #getProject} 拉取结果。
     */
    @PostMapping("/{projectId:\\d+}/slides/generate")
    public ResponseEntity<SlideGenerationStatusDto> generateSlideContents(@PathVariable("projectId") Long projectId,
                                                                         @RequestBody(required = false) GenerateSlidesRequest request) {
        GenerateSlidesRequest payload = request != null ? request : new GenerateSlidesRequest();
        SlideGenerationStatusDto status = slideGenerationOrchestrator.start(projectId, payload);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/{projectId:\\d+}/slides/generate/status")
    public ResponseEntity<SlideGenerationStatusDto> getSlideGenerationStatus(@PathVariable("projectId") Long projectId) {
        return ResponseEntity.ok(slideGenerationOrchestrator.getStatus(projectId));
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

    /** 异步：从讲稿要点提炼适合投影的 pptBullets */
    @PostMapping("/{projectId:\\d+}/slides/extract-ppt-display")
    public ResponseEntity<PptDisplayExtractionStatusDto> extractPptDisplay(
        @PathVariable("projectId") Long projectId,
        @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(pptDisplayExtractionOrchestrator.start(projectId, force));
    }

    @GetMapping("/{projectId:\\d+}/slides/extract-ppt-display/status")
    public ResponseEntity<PptDisplayExtractionStatusDto> getPptDisplayExtractionStatus(
        @PathVariable("projectId") Long projectId
    ) {
        return ResponseEntity.ok(pptDisplayExtractionOrchestrator.getStatus(projectId));
    }

    @PostMapping("/{projectId:\\d+}/slides/{slideId:\\d+}/extract-ppt-display")
    public ResponseEntity<List<String>> extractPptDisplayForSlide(
        @PathVariable("projectId") Long projectId,
        @PathVariable("slideId") Long slideId
    ) {
        return ResponseEntity.ok(pptDisplayExtractionService.extractAndSaveSlide(projectId, slideId));
    }

    @DeleteMapping("/{projectId:\\d+}")
    public ResponseEntity<Void> deleteProject(@PathVariable("projectId") Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch-delete")
    public ResponseEntity<Map<String, Integer>> batchDeleteProjects(@RequestBody BatchDeleteProjectsRequest request) {
        List<Long> ids = request != null ? request.getProjectIds() : List.of();
        int deleted = projectService.deleteProjects(ids);
        return ResponseEntity.ok(Map.of("deletedCount", deleted));
    }

    @GetMapping("/{projectId:\\d+}")
    public ResponseEntity<ProjectDetailResponse> getProject(
        @PathVariable("projectId") Long projectId,
        @RequestParam(name = "includeEvaluations", defaultValue = "false") boolean includeEvaluations
    ) {
        ProjectDetailResponse response = projectService.getProjectById(projectId, includeEvaluations);
        return ResponseEntity.ok(response);
    }
}
