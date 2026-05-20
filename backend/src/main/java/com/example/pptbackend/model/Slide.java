package com.example.pptbackend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "slides")
public class Slide {

    /** 与 PostgreSQL TEXT 对齐的上限，防止单字段过大拖垮 ORM/内存 */
    private static final int MAX_TITLE_CHARS = 4000;
    private static final int MAX_CHAPTER_CHARS = 2000;
    private static final int MAX_BULLET_CHARS = 16000;
    private static final int MAX_SOURCE_CHARS = 16000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name = "bullet_order")
    @CollectionTable(name = "slide_bullets", joinColumns = @JoinColumn(name = "slide_id"))
    @Column(name = "bullet", columnDefinition = "TEXT")
    private List<String> bullets = new ArrayList<>();

    /** 适合投影的短要点（由讲稿 bullets 提炼，供预览中间栏展示） */
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name = "ppt_bullet_order")
    @CollectionTable(name = "slide_ppt_bullets", joinColumns = @JoinColumn(name = "slide_id"))
    @Column(name = "ppt_bullet", columnDefinition = "TEXT")
    private List<String> pptBullets = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name = "source_order")
    @CollectionTable(name = "slide_sources", joinColumns = @JoinColumn(name = "slide_id"))
    @Column(name = "source", columnDefinition = "TEXT")
    private List<String> sources = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @PrePersist
    @PreUpdate
    private void clampLongFields() {
        if (title != null && title.length() > MAX_TITLE_CHARS) {
            title = title.substring(0, MAX_TITLE_CHARS);
        }
        if (chapter != null && chapter.length() > MAX_CHAPTER_CHARS) {
            chapter = chapter.substring(0, MAX_CHAPTER_CHARS);
        }
        if (body != null && body.length() > 65535) {
            body = body.substring(0, 65535);
        }
        if (notes != null && notes.length() > 65535) {
            notes = notes.substring(0, 65535);
        }
        clampList(bullets, MAX_BULLET_CHARS);
        clampList(pptBullets, MAX_BULLET_CHARS);
        clampList(sources, MAX_SOURCE_CHARS);
    }

    private static void clampList(List<String> list, int maxChars) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s != null && s.length() > maxChars) {
                list.set(i, s.substring(0, maxChars));
            }
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getBullets() {
        return bullets;
    }

    public void setBullets(List<String> bullets) {
        replaceStringCollection(this.bullets, bullets);
    }

    public List<String> getPptBullets() {
        return pptBullets;
    }

    public void setPptBullets(List<String> pptBullets) {
        replaceStringCollection(this.pptBullets, pptBullets);
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        replaceStringCollection(this.sources, sources);
    }

    /**
     * slide_bullets / slide_ppt_bullets / slide_sources 主键含文本列，须就地 clear 再写入，
     * 且同一页不能有完全相同的字符串（否则会违反唯一约束）。
     */
    private static void replaceStringCollection(List<String> target, List<String> incoming) {
        target.clear();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : incoming) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (seen.add(trimmed)) {
                normalized.add(trimmed);
            }
        }
        target.addAll(normalized);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}
