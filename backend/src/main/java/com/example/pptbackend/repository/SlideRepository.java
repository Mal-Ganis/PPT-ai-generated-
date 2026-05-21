package com.example.pptbackend.repository;

import com.example.pptbackend.model.Slide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlideRepository extends JpaRepository<Slide, Long> {

    Optional<Slide> findByIdAndProject_Id(Long id, Long projectId);

    List<Slide> findByProject_IdOrderByPositionAsc(Long projectId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM Slide s JOIN FETCH s.project p WHERE p.id IN :projectIds ORDER BY p.id, s.position")
    List<Slide> findByProject_IdInWithProjectOrderByPositionAsc(
        @org.springframework.data.repository.query.Param("projectIds") Collection<Long> projectIds);

    /** 存在非空讲稿正文或要点（排除大纲占位「（待编辑要点）」）的项目 ID */
    @Query(
        value = """
            SELECT DISTINCT s.project_id
            FROM slides s
            WHERE TRIM(COALESCE(s.body, '')) <> ''
               OR EXISTS (
                 SELECT 1 FROM slide_bullets b
                 WHERE b.slide_id = s.id
                   AND TRIM(b.bullet) <> ''
                   AND TRIM(b.bullet) <> '（待编辑要点）'
               )
            """,
        nativeQuery = true)
    List<Long> findProjectIdsWithScriptContent();

    /** 存在非空 PPT 投影要点的项目 ID */
    @Query(
        value = """
            SELECT DISTINCT s.project_id
            FROM slides s
            WHERE EXISTS (
              SELECT 1 FROM slide_ppt_bullets p
              WHERE p.slide_id = s.id
                AND TRIM(p.ppt_bullet) <> ''
            )
            """,
        nativeQuery = true)
    List<Long> findProjectIdsWithPptBullets();

    /**
     * 已走过「生成内容」：至少一页存在非空引用来源（大纲阶段通常无 sources）。
     */
    @Query(
        value = """
            SELECT DISTINCT s.project_id
            FROM slides s
            WHERE EXISTS (
              SELECT 1 FROM slide_sources src
              WHERE src.slide_id = s.id
                AND TRIM(COALESCE(src.source, '')) <> ''
            )
            """,
        nativeQuery = true)
    List<Long> findProjectIdsWithGeneratedContent();
}
