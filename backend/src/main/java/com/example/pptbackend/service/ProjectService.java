package com.example.pptbackend.service;

import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.ProjectSummaryDto;
import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.UpdateSlideRequest;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final SlideRepository slideRepository;
    private final EvaluationReportService evaluationReportService;
    private final DocumentIndexingService documentIndexingService;
    private final OutlineGenerationService outlineGenerationService;
    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;

    @Value("${outline.use-external-retrieval:true}")
    private boolean outlineUseExternalRetrieval;

    @Value("${outline.external-snippet-max-chars:6000}")
    private int outlineExternalSnippetMaxChars;

    @Value("${outline.external-result-limit:8}")
    private int outlineExternalResultLimit;

    public ProjectService(ProjectRepository projectRepository,
                          SlideRepository slideRepository,
                          EvaluationReportService evaluationReportService,
                          DocumentIndexingService documentIndexingService,
                          OutlineGenerationService outlineGenerationService,
                          ExternalKnowledgeSourceService externalKnowledgeSourceService) {
        this.projectRepository = projectRepository;
        this.slideRepository = slideRepository;
        this.evaluationReportService = evaluationReportService;
        this.documentIndexingService = documentIndexingService;
        this.outlineGenerationService = outlineGenerationService;
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
        String clean = topic.trim();
        Long projectId = createEmptyProject(clean, clean);

        String augmented = clean;
        if (outlineUseExternalRetrieval) {
            try {
                List<ExternalSourceDocument> docs =
                    externalKnowledgeSourceService.searchExternalSources(clean, outlineExternalResultLimit);
                if (!docs.isEmpty()) {
                    int indexed = externalKnowledgeSourceService.indexDocumentsIntoProject(projectId, docs);
                    log.info("Outline phase: indexed {} external snippets for project {}", indexed, projectId);
                    augmented = clean + "\n\n"
                        + externalKnowledgeSourceService.formatDocumentsForOutlinePrompt(
                            docs, outlineExternalSnippetMaxChars);
                } else {
                    log.info(
                        "Outline phase: external retrieval returned no documents (configure TAVILY_API_KEY for Tavily)");
                }
            } catch (Exception e) {
                log.warn("Outline external retrieval failed, falling back to topic only: {}", e.getMessage());
            }
        }

        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(augmented);
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
        Project project = new Project();
        project.setTitle(title);
        project.setTheme(theme != null ? theme : title);
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
            slide.setBullets(new ArrayList<>(request.getBullets()));
            slide.setBody(null);
        } else if (request.getBody() != null) {
            String b = request.getBody().trim();
            slide.setBody(b.isEmpty() ? null : request.getBody());
        }
        if (request.getSources() != null) {
            slide.setSources(new ArrayList<>(request.getSources()));
        }

        slideRepository.save(slide);
        return getProjectById(projectId);
    }

    @Transactional
    public ProjectOutlineResponse createProjectFromDocument(String title, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Document text is required");
        }
        String safeTitle = title != null && !title.isBlank() ? title : "文档演示文稿";
        Long projectId = createEmptyProject(safeTitle, truncate(rawText, 400));
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
                    externalKnowledgeSourceService.indexDocumentsIntoProject(projectId, docs);
                    augmented = augmented + "\n\n【权威检索补充】\n"
                        + externalKnowledgeSourceService.formatDocumentsForOutlinePrompt(
                            docs, outlineExternalSnippetMaxChars);
                }
            } catch (Exception e) {
                log.warn("Document flow: external supplement failed: {}", e.getMessage());
            }
        }
        ProjectOutlineResponse outline = outlineGenerationService.generateOutline(augmented);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setTitle(outline.getTitle() != null ? outline.getTitle() : safeTitle);
        request.setTheme(safeTitle);
        request.setSlides(mapOutlineToSlides(outline));
        replaceOutline(projectId, request);
        return buildOutlineResponse(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectOutlineResponse buildOutlineResponse(Long projectId) {
        ProjectDetailResponse detail = getProjectById(projectId);
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setProjectId(projectId);
        response.setTitle(detail.getTitle());
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
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
            .stream()
            .map(project -> {
                ProjectSummaryDto dto = new ProjectSummaryDto();
                dto.setId(project.getId());
                dto.setTitle(project.getTitle());
                dto.setCreatedAt(project.getCreatedAt());
                dto.setUpdatedAt(project.getUpdatedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));

        ProjectDetailResponse response = new ProjectDetailResponse();
        response.setId(project.getId());
        response.setTitle(project.getTitle());
        response.setTheme(project.getTheme());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());

        List<ProjectDetailResponse.SlideItem> slides = project.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getPosition))
            .map(this::toSlideResponse)
            .collect(Collectors.toList());

        response.setSlides(slides);
        List<EvaluationReportResponse> evaluations = evaluationReportService.getReportsForProject(id);
        response.setEvaluations(evaluations);
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
        item.setSources(slide.getSources());
        item.setNotes(slide.getNotes());
        return item;
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
