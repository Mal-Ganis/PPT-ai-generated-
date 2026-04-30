package com.example.pptbackend.service;

import com.example.pptbackend.dto.CreateProjectRequest;
import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.ProjectDetailResponse;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.service.EvaluationReportService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EvaluationReportService evaluationReportService;

    public ProjectService(ProjectRepository projectRepository,
                          EvaluationReportService evaluationReportService) {
        this.projectRepository = projectRepository;
        this.evaluationReportService = evaluationReportService;
    }

    @Transactional
    public Long createProject(CreateProjectRequest request) {
        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setTheme(request.getTheme());

        if (request.getSlides() != null) {
            request.getSlides().stream()
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

        return projectRepository.save(project).getId();
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
}
