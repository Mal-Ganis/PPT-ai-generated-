package com.example.pptbackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 为 slide_bullets / slide_ppt_bullets / slide_sources 补顺序列。
 * 无主键顺序时 PostgreSQL 返回顺序不稳定，高级编辑保存后易出现「首尾要点对调」。
 */
@Component
@Order(1)
public class SlideCollectionOrderCleanup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SlideCollectionOrderCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillOrderColumn("slide_bullets", "bullet_order", "bullet");
        backfillOrderColumn("slide_ppt_bullets", "ppt_bullet_order", "ppt_bullet");
        backfillOrderColumn("slide_sources", "source_order", "source");
    }

    private void backfillOrderColumn(String table, String orderCol, String valueCol) {
        jdbcTemplate.execute(
            "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + orderCol + " INTEGER");
        jdbcTemplate.update(
            """
            UPDATE %s t SET %s = sub.rn
            FROM (
              SELECT slide_id, %s AS val,
                     (ROW_NUMBER() OVER (PARTITION BY slide_id ORDER BY ctid) - 1) AS rn
              FROM %s
            ) sub
            WHERE t.slide_id = sub.slide_id AND t.%s = sub.val AND t.%s IS NULL
            """
                .formatted(table, orderCol, valueCol, table, valueCol, orderCol));
        jdbcTemplate.update(
            "UPDATE " + table + " SET " + orderCol + " = 0 WHERE " + orderCol + " IS NULL");
    }
}
