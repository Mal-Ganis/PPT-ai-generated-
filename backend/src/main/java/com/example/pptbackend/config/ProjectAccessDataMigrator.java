package com.example.pptbackend.config;

import com.example.pptbackend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 历史 owner 为空的项目视为全员共享数据；迁移为模板供只读访客浏览，编辑者不再可见。
 */
@Component
public class ProjectAccessDataMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectAccessDataMigrator.class);

    private final ProjectRepository projectRepository;

    public ProjectAccessDataMigrator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = projectRepository.markOrphanProjectsAsTemplate();
        if (updated > 0) {
            log.info("Marked {} legacy project(s) without owner as template (viewer-only)", updated);
        }
    }
}
