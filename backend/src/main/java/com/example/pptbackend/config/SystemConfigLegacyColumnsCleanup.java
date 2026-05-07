package com.example.pptbackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 早期 Hibernate 命名曾生成误列 {@code topp}/{@code topk}，与 {@code top_p}/{@code top_k} 并存且 NOT NULL，
 * 导致插入默认配置失败。启动时删除遗留列（若不存在则跳过）。
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
    }
}
