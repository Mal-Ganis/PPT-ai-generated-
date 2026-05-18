package com.example.pptbackend.service;

import com.example.pptbackend.dto.PptDisplayExtractionStatusDto;
import com.example.pptbackend.dto.PptDisplayExtractionStatusDto.Phase;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class PptDisplayExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PptDisplayExtractionOrchestrator.class);

    private final PptDisplayExtractionService extractionService;
    private final ProjectRepository projectRepository;
    private final SlideRepository slideRepository;
    private final Executor pptTaskExecutor;
    private final ConcurrentHashMap<Long, PptDisplayExtractionStatusDto> states = new ConcurrentHashMap<>();

    public PptDisplayExtractionOrchestrator(PptDisplayExtractionService extractionService,
                                            ProjectRepository projectRepository,
                                            SlideRepository slideRepository,
                                            @Qualifier("pptTaskExecutor") Executor pptTaskExecutor) {
        this.extractionService = extractionService;
        this.projectRepository = projectRepository;
        this.slideRepository = slideRepository;
        this.pptTaskExecutor = pptTaskExecutor;
    }

    @Transactional(readOnly = true)
    public PptDisplayExtractionStatusDto start(Long projectId, boolean force) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }

        PptDisplayExtractionStatusDto running = states.get(projectId);
        if (running != null && running.getPhase() == Phase.RUNNING) {
            return running;
        }

        int total = slideRepository.findByProject_IdOrderByPositionAsc(projectId).size();
        PptDisplayExtractionStatusDto status = new PptDisplayExtractionStatusDto();
        status.setProjectId(projectId);
        status.setPhase(Phase.RUNNING);
        status.setTotalSlides(total);
        status.setCompletedSlides(0);
        status.setMessage("正在提炼 PPT 投影文案…");
        states.put(projectId, status);

        pptTaskExecutor.execute(() -> runExtraction(projectId, force, status));
        return status;
    }

    public PptDisplayExtractionStatusDto getStatus(Long projectId) {
        PptDisplayExtractionStatusDto cached = states.get(projectId);
        if (cached == null) {
            PptDisplayExtractionStatusDto idle = new PptDisplayExtractionStatusDto();
            idle.setProjectId(projectId);
            idle.setPhase(Phase.IDLE);
            idle.setMessage("暂无提炼任务");
            return idle;
        }
        if (cached.getPhase() == Phase.RUNNING) {
            int done = countSlidesWithPptBullets(projectId);
            cached.setCompletedSlides(done);
        }
        return cached;
    }

    private void runExtraction(Long projectId, boolean force, PptDisplayExtractionStatusDto status) {
        try {
            extractionService.extractAllForProject(projectId, force, (done, total) -> {
                status.setTotalSlides(total);
                status.setCompletedSlides(done);
                status.setMessage("正在提炼第 " + done + " / " + total + " 页…");
            });
            status.setPhase(Phase.COMPLETED);
            status.setCompletedSlides(status.getTotalSlides());
            status.setMessage("PPT 投影文案提炼完成");
            log.info("PPT display extraction completed for project {}", projectId);
        } catch (Exception e) {
            log.warn("PPT display extraction failed for project {}: {}", projectId, e.getMessage(), e);
            status.setPhase(Phase.FAILED);
            status.setMessage(e.getMessage() != null ? e.getMessage() : "提炼失败");
        }
    }

    private int countSlidesWithPptBullets(Long projectId) {
        return (int) loadSlides(projectId).stream()
            .filter(s -> s.getPptBullets() != null && !s.getPptBullets().isEmpty())
            .count();
    }

    private List<Slide> loadSlides(Long projectId) {
        return slideRepository.findByProject_IdOrderByPositionAsc(projectId);
    }
}
