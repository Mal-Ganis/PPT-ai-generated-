package com.example.pptbackend.service;

import com.example.pptbackend.dto.ExternalSourceDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将「权威检索片段写入项目向量库」从大纲同步链路中拆出，事务提交后再异步执行。
 * 正文阶段若向量尚未就绪，{@link SlideGenerationService#buildRagContext} 会走 Tavily 兜底。
 */
@Service
public class DeferredProjectExternalIndexService {

    private static final Logger log = LoggerFactory.getLogger(DeferredProjectExternalIndexService.class);

    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;

    public DeferredProjectExternalIndexService(ExternalKnowledgeSourceService externalKnowledgeSourceService) {
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
    }

    @Async("pptTaskExecutor")
    public void indexExternalDocumentsForProject(Long projectId, List<ExternalSourceDocument> docs) {
        if (projectId == null || docs == null || docs.isEmpty()) {
            return;
        }
        try {
            int n = externalKnowledgeSourceService.indexDocumentsIntoProject(projectId, docs);
            log.info("Deferred external snippet index finished: projectId={} segments={}", projectId, n);
        } catch (Exception e) {
            log.warn("Deferred external snippet index failed: projectId={} error={}", projectId, e.getMessage());
        }
    }
}
