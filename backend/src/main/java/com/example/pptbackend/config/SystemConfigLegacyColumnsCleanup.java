package com.example.pptbackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时修补 {@code system_config} 表结构：删除 Hibernate 误列，补全新配置列。
 */
@Component
@Order(0)
public class SystemConfigLegacyColumnsCleanup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SystemConfigLegacyColumnsCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE system_config DROP COLUMN IF EXISTS topp");
        jdbcTemplate.execute("ALTER TABLE system_config DROP COLUMN IF EXISTS topk");
        jdbcTemplate.execute(
            "ALTER TABLE system_config ADD COLUMN IF NOT EXISTS outline_include_qa_slide boolean DEFAULT true");
        jdbcTemplate.update(
            "UPDATE system_config SET outline_include_qa_slide = true WHERE outline_include_qa_slide IS NULL");
    }
}
