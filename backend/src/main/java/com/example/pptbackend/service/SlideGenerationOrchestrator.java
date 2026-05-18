package com.example.pptbackend.service;

import com.example.pptbackend.dto.GenerateSlidesRequest;
import com.example.pptbackend.dto.SlideGenerationStatusDto;
import com.example.pptbackend.dto.SlideGenerationStatusDto.Phase;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class SlideGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SlideGenerationOrchestrator.class);

    private final SlideGenerationService slideGenerationService;
    private final ProjectRepository projectRepository;
    private final SlideRepository slideRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor pptTaskExecutor;
    private final ConcurrentHashMap<Long, SlideGenerationStatusDto> states = new ConcurrentHashMap<>();

    public SlideGenerationOrchestrator(SlideGenerationService slideGenerationService,
                                       ProjectRepository projectRepository,
                                       SlideRepository slideRepository,
                                       TransactionTemplate transactionTemplate,
                                       @Qualifier("pptTaskExecutor") Executor pptTaskExecutor) {
        this.slideGenerationService = slideGenerationService;
        this.projectRepository = projectRepository;
        this.slideRepository = slideRepository;
        this.transactionTemplate = transactionTemplate;
        this.pptTaskExecutor = pptTaskExecutor;
    }

    @Transactional(readOnly = true)
    public SlideGenerationStatusDto start(Long projectId, GenerateSlidesRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }

        SlideGenerationStatusDto running = states.get(projectId);
        if (running != null && running.getPhase() == Phase.RUNNING) {
            return refreshFromDatabase(projectId, running);
        }

        int total = countSlides(projectId);
        SlideGenerationStatusDto status = new SlideGenerationStatusDto();
        status.setProjectId(projectId);
        status.setPhase(Phase.RUNNING);
        status.setTotalSlides(total);
        status.setCompletedSlides(0);
        status.setMessage("正文生成已开始，请稍候…");
        states.put(projectId, status);

        GenerateSlidesRequest payload = request != null ? request : new GenerateSlidesRequest();
        pptTaskExecutor.execute(() -> runGeneration(projectId, payload, status));
        return status;
    }

    @Transactional(readOnly = true)
    public SlideGenerationStatusDto getStatus(Long projectId) {
        SlideGenerationStatusDto cached = states.get(projectId);
        if (cached == null) {
            return idleStatus(projectId);
        }
        return refreshFromDatabase(projectId, cached);
    }

    private void runGeneration(Long projectId, GenerateSlidesRequest request, SlideGenerationStatusDto status) {
        try {
            slideGenerationService.generateAllSlides(projectId, request, (done, total) -> {
                status.setTotalSlides(total);
                status.setCompletedSlides(done);
                status.setMessage("正在生成第 " + done + " / " + total + " 页…");
            });
            status.setPhase(Phase.COMPLETED);
            status.setCompletedSlides(status.getTotalSlides());
            status.setMessage("正文生成完成");
            syncStatusFromDatabase(projectId, status);
            log.info("Slide generation completed for project {}", projectId);
        } catch (Exception e) {
            log.warn("Slide generation failed for project {}: {}", projectId, e.getMessage(), e);
            status.setPhase(Phase.FAILED);
            status.setMessage(e.getMessage() != null ? e.getMessage() : "正文生成失败");
            syncStatusFromDatabase(projectId, status);
        }
    }

    /** 异步线程无事务，须通过 TransactionTemplate 刷新进度。 */
    private void syncStatusFromDatabase(Long projectId, SlideGenerationStatusDto status) {
        transactionTemplate.executeWithoutResult(tx -> refreshFromDatabase(projectId, status));
    }

    private SlideGenerationStatusDto refreshFromDatabase(Long projectId, SlideGenerationStatusDto status) {
        int total = countSlides(projectId);
        int done = countSlidesWithBullets(projectId);
        status.setProjectId(projectId);
        status.setTotalSlides(Math.max(status.getTotalSlides(), total));
        if (status.getPhase() == Phase.RUNNING) {
            status.setCompletedSlides(done);
        } else if (status.getPhase() == Phase.COMPLETED) {
            status.setCompletedSlides(Math.max(done, status.getCompletedSlides()));
        } else if (status.getPhase() == Phase.FAILED) {
            status.setCompletedSlides(done);
        }
        return status;
    }

    private static SlideGenerationStatusDto idleStatus(Long projectId) {
        SlideGenerationStatusDto dto = new SlideGenerationStatusDto();
        dto.setProjectId(projectId);
        dto.setPhase(Phase.IDLE);
        dto.setMessage("暂无进行中的生成任务");
        return dto;
    }

    private int countSlides(Long projectId) {
        return loadSlides(projectId).size();
    }

    private int countSlidesWithBullets(Long projectId) {
        return (int) loadSlides(projectId).stream()
            .filter(s -> s.getBullets() != null && !s.getBullets().isEmpty())
            .count();
    }

    private List<Slide> loadSlides(Long projectId) {
        return slideRepository.findByProject_IdOrderByPositionAsc(projectId);
    }
}
