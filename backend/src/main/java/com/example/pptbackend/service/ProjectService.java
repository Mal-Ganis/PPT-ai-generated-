package com.example.pptbackend.service;

import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.ProjectSummaryDto;
import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.RegenerateOutlineRequest;
import com.example.pptbackend.dto.UpdateSlideRequest;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.EvaluationReportRepository;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final SlideRepository slideRepository;
    private final EvaluationReportRepository evaluationReportRepository;
    private final EvaluationReportService evaluationReportService;
    private final ProjectWorkflowStageService projectWorkflowStageService;
    private final DocumentIndexingService documentIndexingService;
    private final OutlineGenerationService outlineGenerationService;
    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;
    private final DeferredProjectExternalIndexService deferredProjectExternalIndexService;
    private final IndexSegmentService indexSegmentService;

    @Value("${outline.use-external-retrieval:true}")
    private boolean outlineUseExternalRetrieval;

    @Value("${outline.external-snippet-max-chars:4800}")
    private int outlineExternalSnippetMaxChars;

    @Value("${outline.external-result-limit:6}")
    private int outlineExternalResultLimit;

    public ProjectService(ProjectRepository projectRepository,
                          SlideRepository slideRepository,
                          EvaluationReportRepository evaluationReportRepository,
                          EvaluationReportService evaluationReportService,
                          ProjectWorkflowStageService projectWorkflowStageService,
                          DocumentIndexingService documentIndexingService,
                          OutlineGenerationService outlineGenerationService,
                          ExternalKnowledgeSourceService externalKnowledgeSourceService,
                          DeferredProjectExternalIndexService deferredProjectExternalIndexService,
                          IndexSegmentService indexSegmentService) {
        this.projectRepository = projectRepository;
        this.slideRepository = slideRepository;
        this.evaluationReportRepository = evaluationReportRepository;
        this.evaluationReportService = evaluationReportService;
        this.projectWorkflowStageService = projectWorkflowStageService;
        this.documentIndexingService = documentIndexingService;
        this.outlineGenerationService = outlineGenerationService;
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
        this.deferredProjectExternalIndexService = deferredProjectExternalIndexService;
        this.indexSegmentService = indexSegmentService;
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromTopic(String topic) {
        return createProjectFromTopic(topic, null);
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromTopic(String topic, Integer presentationDurationMinutes) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
        String clean = topic.trim();
        int minutes = PresentationDurationPlanner.clampMinutes(presentationDurationMinutes);
        Long projectId = createEmptyProject(clean, clean, minutes);

        String augmented = clean;
        if (outlineUseExternalRetrieval) {
            try {
                List<ExternalSourceDocument> docs =
                    externalKnowledgeSourceService.searchExternalSources(clean, outlineExternalResultLimit);
                if (!docs.isEmpty()) {
                    augmented = clean + "\n\n"
                        + externalKnowledgeSourceService.formatDocumentsForOutlinePrompt(
                            docs, outlineExternalSnippetMaxChars);
                    scheduleExternalSnippetIndexAfterCommit(projectId, docs);
                    log.info(
                        "Outline phase: scheduled deferred vector index for {} external snippets, project {}",
                        docs.size(),
                        projectId);
                } else {
                    log.info(
                        "Outline phase: external retrieval returned no documents (configure TAVILY_API_KEY for Tavily)");
                }
            } catch (Exception e) {
                log.warn("Outline external retrieval failed, falling back to topic only: {}", e.getMessage());
            }
        }

        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(augmented, minutes);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setTitle(outline.getTitle() != null ? outline.getTitle() : clean);
        request.setTheme(clean);
        request.setSlides(mapOutlineToSlides(outline));
        replaceOutline(projectId, request);
        return buildOutlineResponse(projectId);
    }

    @Transactional
    public Long createProject(CreateProjectRequest request) {
        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setTheme(request.getTheme() != null ? request.getTheme() : request.getTitle());
        applySlides(project, request.getSlides());
        return projectRepository.save(project).getId();
    }

    @Transactional
    public Long createEmptyProject(String title, String theme) {
        return createEmptyProject(title, theme, PresentationDurationPlanner.DEFAULT_MINUTES);
    }

    @Transactional
    public Long createEmptyProject(String title, String theme, int presentationDurationMinutes) {
        Project project = new Project();
        project.setTitle(title);
        project.setTheme(theme != null ? theme : title);
        project.setPresentationDurationMinutes(PresentationDurationPlanner.clampMinutes(presentationDurationMinutes));
        return projectRepository.save(project).getId();
    }

    @Transactional
    public void replaceOutline(Long projectId, CreateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        if (request.getTitle() != null) {
            project.setTitle(request.getTitle());
        }
        if (request.getTheme() != null) {
            project.setTheme(request.getTheme());
        }
        applySlides(project, request.getSlides());
        projectRepository.save(project);
    }

    /**
     * 按用户主题（及文档向量上下文）重新生成大纲并替换当前项目幻灯片结构。
     */
    @Transactional
    public ProjectOutlineResponse regenerateOutline(Long projectId, RegenerateOutlineRequest request) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));

        String cleanTopic = request != null && request.getTopic() != null && !request.getTopic().isBlank()
            ? request.getTopic().trim()
            : (project.getTheme() != null ? project.getTheme().trim() : "");
        if (cleanTopic.isBlank()) {
            throw new IllegalArgumentException("请提供演示主题");
        }

        int minutes = PresentationDurationPlanner.clampMinutes(
            request != null && request.getPresentationDurationMinutes() != null
                ? request.getPresentationDurationMinutes()
                : project.getPresentationDurationMinutes());

        String inputType = request != null && request.getInputType() != null ? request.getInputType() : "topic";
        String inputContent = request != null ? request.getInputContent() : null;
        String augmented = buildAugmentedOutlinePrompt(projectId, cleanTopic, inputType, inputContent);

        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(augmented, minutes);
        CreateProjectRequest upsert = new CreateProjectRequest();
        upsert.setTitle(outline.getTitle() != null ? outline.getTitle() : cleanTopic);
        upsert.setTheme(cleanTopic);
        upsert.setSlides(mapOutlineToSlides(outline));

        project.setPresentationDurationMinutes(minutes);
        projectRepository.save(project);
        evaluationReportRepository.deleteByProjectId(projectId);
        replaceOutline(projectId, upsert);
        return buildOutlineResponse(projectId);
    }

    private String buildAugmentedOutlinePrompt(Long projectId,
                                               String topic,
                                               String inputType,
                                               String inputContent) {
        if ("document".equalsIgnoreCase(inputType)
            && inputContent != null
            && !inputContent.isBlank()) {
            String rag = documentIndexingService.buildRagContext(
                projectId, topic + "\n" + truncate(inputContent, 1500));
            String augmented = topic + "\n\n上传全文节选：\n" + truncate(inputContent, 3200);
            if (!rag.isBlank()) {
                augmented = augmented + "\n\n向量检索到的文档片段：\n" + rag;
            } else {
                augmented = augmented
                    + "\n\n（说明：当前向量库未命中相似片段，请主要依据上方节选组织大纲；可适当扩展章节。）\n";
            }
            return appendExternalOutlineContext(projectId, augmented, topic);
        }
        return appendExternalOutlineContext(projectId, topic, topic);
    }

    private String appendExternalOutlineContext(Long projectId, String base, String searchQuery) {
        if (!outlineUseExternalRetrieval) {
            return base;
        }
        try {
            List<ExternalSourceDocument> docs =
                externalKnowledgeSourceService.searchExternalSources(searchQuery, outlineExternalResultLimit);
            if (!docs.isEmpty()) {
                scheduleExternalSnippetIndexAfterCommit(projectId, docs);
                log.info(
                    "Outline regenerate: scheduled deferred vector index for {} snippets, project {}",
                    docs.size(),
                    projectId);
                return base + "\n\n"
                    + externalKnowledgeSourceService.formatDocumentsForOutlinePrompt(
                        docs, outlineExternalSnippetMaxChars);
            }
            log.info("Outline regenerate: external retrieval returned no documents");
        } catch (Exception e) {
            log.warn("Outline external retrieval failed, falling back: {}", e.getMessage());
        }
        return base;
    }

    /**
     * 仅更新指定幻灯片，其它页的标题、要点、备注等均保持不变。
     */
    @Transactional
    public ProjectDetailResponse updateSlide(Long projectId, Long slideId, UpdateSlideRequest request) {
        Slide slide = slideRepository.findByIdAndProject_Id(slideId, projectId)
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            slide.setTitle(request.getTitle().trim());
        }
        if (request.getChapter() != null) {
            String ch = request.getChapter().trim();
            slide.setChapter(ch.isEmpty() ? null : ch);
        }
        if (request.getNotes() != null) {
            slide.setNotes(request.getNotes().isBlank() ? null : request.getNotes());
        }
        if (request.getBullets() != null) {
            slide.setBullets(request.getBullets());
            slide.setBody(null);
        } else if (request.getBody() != null) {
            String b = request.getBody().trim();
            slide.setBody(b.isEmpty() ? null : request.getBody());
        }
        if (request.getPptBullets() != null) {
            slide.setPptBullets(request.getPptBullets());
        }
        if (request.getSources() != null) {
            slide.setSources(request.getSources());
        }

        slideRepository.save(slide);
        return getProjectById(projectId);
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromDocument(String title, String rawText) {
        return createProjectFromDocument(title, rawText, null);
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromDocument(String title, String rawText, Integer presentationDurationMinutes) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Document text is required");
        }
        String safeTitle = title != null && !title.isBlank() ? title : "文档演示文稿";
        int minutes = PresentationDurationPlanner.clampMinutes(presentationDurationMinutes);
        Long projectId = createEmptyProject(safeTitle, truncate(rawText, 400), minutes);
        documentIndexingService.indexPlainText(projectId, rawText);
        String rag = documentIndexingService.buildRagContext(projectId, safeTitle + "\n" + truncate(rawText, 1500));
        String augmented = safeTitle + "\n\n上传全文节选：\n" + truncate(rawText, 3200);
        if (!rag.isBlank()) {
            augmented = augmented + "\n\n向量检索到的文档片段：\n" + rag;
        } else {
            augmented = augmented + "\n\n（说明：当前向量库未命中相似片段，请主要依据上方节选组织大纲；可适当扩展章节。）\n";
        }
        if (outlineUseExternalRetrieval) {
            try {
                List<ExternalSourceDocument> docs =
                    externalKnowledgeSourceService.searchExternalSources(safeTitle, outlineExternalResultLimit);
                if (!docs.isEmpty()) {
                    augmented = augmented + "\n\n【权威检索补充】\n"
                        + externalKnowledgeSourceService.formatDocumentsForOutlinePrompt(
                            docs, outlineExternalSnippetMaxChars);
                    scheduleExternalSnippetIndexAfterCommit(projectId, docs);
                    log.info(
                        "Document outline: scheduled deferred vector index for {} external snippets, project {}",
                        docs.size(),
                        projectId);
                }
            } catch (Exception e) {
                log.warn("Document flow: external supplement failed: {}", e.getMessage());
            }
        }
        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(augmented, minutes);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setTitle(outline.getTitle() != null ? outline.getTitle() : safeTitle);
        request.setTheme(safeTitle);
        request.setSlides(mapOutlineToSlides(outline));
        replaceOutline(projectId, request);
        return buildOutlineResponse(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectOutlineResponse buildOutlineResponse(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        ProjectDetailResponse detail = getProjectById(projectId);
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setProjectId(projectId);
        response.setTitle(detail.getTitle());
        response.setPresentationDurationMinutes(
            PresentationDurationPlanner.clampMinutes(project.getPresentationDurationMinutes()));
        for (ProjectDetailResponse.SlideItem slideItem : detail.getSlides()) {
            ProjectOutlineResponse.OutlineSlide outlineSlide = new ProjectOutlineResponse.OutlineSlide();
            outlineSlide.setSlideId(slideItem.getId());
            outlineSlide.setId(slideItem.getPosition());
            outlineSlide.setChapter(slideItem.getChapter());
            outlineSlide.setTitle(slideItem.getTitle());
            outlineSlide.setContent(slideItem.getBullets() != null ? slideItem.getBullets().toArray(new String[0]) : new String[0]);
            outlineSlide.setNotes(slideItem.getNotes());
            response.getSlides().add(outlineSlide);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> listProjects() {
        List<Project> projects = projectRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, List<Slide>> slidesByProject = new HashMap<>();
        for (Slide slide : slideRepository.findByProject_IdInWithProjectOrderByPositionAsc(projectIds)) {
            Long pid = slide.getProject().getId();
            slidesByProject.computeIfAbsent(pid, k -> new ArrayList<>()).add(slide);
        }
        Map<Long, ProjectWorkflowStageService.StageSnapshot> stages =
            projectWorkflowStageService.evaluateProjects(slidesByProject);
        return projects.stream()
            .map(project -> {
                ProjectSummaryDto dto = new ProjectSummaryDto();
                dto.setId(project.getId());
                dto.setTitle(project.getTitle());
                dto.setCreatedAt(project.getCreatedAt());
                dto.setUpdatedAt(project.getUpdatedAt());
                ProjectWorkflowStageService.StageSnapshot stage = stages.getOrDefault(
                    project.getId(),
                    new ProjectWorkflowStageService.StageSnapshot(false, false, "仅大纲"));
                dto.setHasScript(stage.hasGeneratedContent());
                dto.setHasPpt(stage.hasReadyPreview());
                dto.setStage(stage.stageLabel());
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteProject(Long projectId) {
        deleteProjects(List.of(projectId));
    }

    @Transactional
    public int deleteProjects(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("projectIds is required");
        }
        List<Long> ids = projectIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("projectIds is required");
        }
        for (Long id : ids) {
            if (!projectRepository.existsById(id)) {
                throw new EntityNotFoundException("Project not found: " + id);
            }
        }
        for (Long id : ids) {
            evaluationReportRepository.deleteByProjectId(id);
        }
        indexSegmentService.deleteByProjectIds(ids);
        projectRepository.deleteAllById(ids);
        log.info("Deleted {} project(s): {}", ids.size(), ids);
        return ids.size();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(Long id) {
        return getProjectById(id, false);
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(Long id, boolean includeEvaluations) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));

        ProjectDetailResponse response = new ProjectDetailResponse();
        response.setId(project.getId());
        response.setTitle(project.getTitle());
        response.setTheme(project.getTheme());
        response.setPresentationDurationMinutes(
            PresentationDurationPlanner.clampMinutes(project.getPresentationDurationMinutes()));
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());

        List<ProjectDetailResponse.SlideItem> slides = slideRepository.findByProject_IdOrderByPositionAsc(id)
            .stream()
            .map(this::toSlideResponse)
            .collect(Collectors.toList());
        response.setSlides(slides);

        if (includeEvaluations) {
            response.setEvaluations(evaluationReportService.getReportsForProject(id));
        } else {
            response.setEvaluations(List.of());
        }
        return response;
    }

    private void applySlides(Project project, List<CreateProjectRequest.SlideItem> items) {
        project.getSlides().clear();
        if (items == null) {
            return;
        }
        items.stream()
            .sorted(Comparator.comparing(item -> item.getPosition() == null ? 0 : item.getPosition()))
            .forEach(item -> {
                Slide slide = new Slide();
                slide.setPosition(item.getPosition() == null ? 0 : item.getPosition());
                slide.setChapter(item.getChapter());
                slide.setTitle(item.getTitle());
                slide.setBody(item.getBody());
                slide.setBullets(item.getBullets());
                slide.setSources(item.getSources());
                slide.setNotes(item.getNotes());
                project.addSlide(slide);
            });
    }

    private List<CreateProjectRequest.SlideItem> mapOutlineToSlides(ProjectOutlineResponse outline) {
        List<CreateProjectRequest.SlideItem> list = new ArrayList<>();
        int pos = 1;
        for (ProjectOutlineResponse.OutlineSlide item : outline.getSlides()) {
            CreateProjectRequest.SlideItem slideItem = new CreateProjectRequest.SlideItem();
            slideItem.setPosition(pos++);
            String ch = item.getChapter();
            slideItem.setChapter(ch != null && !ch.isBlank() ? ch.trim() : null);
            slideItem.setTitle(item.getTitle());
            slideItem.setBullets(item.getContent() != null ? Arrays.asList(item.getContent()) : List.of());
            slideItem.setNotes(item.getNotes());
            list.add(slideItem);
        }
        return list;
    }

    private ProjectDetailResponse.SlideItem toSlideResponse(Slide slide) {
        ProjectDetailResponse.SlideItem item = new ProjectDetailResponse.SlideItem();
        item.setId(slide.getId());
        item.setPosition(slide.getPosition());
        item.setChapter(slide.getChapter());
        item.setTitle(slide.getTitle());
        item.setBody(slide.getBody());
        item.setBullets(slide.getBullets());
        item.setPptBullets(slide.getPptBullets());
        item.setSources(slide.getSources());
        item.setNotes(slide.getNotes());
        return item;
    }

    /**
     * 事务成功提交后再异步写入检索片段，避免阻塞大纲响应；列表做快照以免调用方后续修改。
     */
    private void scheduleExternalSnippetIndexAfterCommit(Long projectId, List<ExternalSourceDocument> docs) {
        if (projectId == null || docs == null || docs.isEmpty()) {
            return;
        }
        List<ExternalSourceDocument> snapshot = List.copyOf(docs);
        Runnable run = () -> deferredProjectExternalIndexService.indexExternalDocumentsForProject(projectId, snapshot);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    run.run();
                }
            });
        } else {
            run.run();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
