package com.example.pptbackend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 识别封面、目录、问答/讨论等「骨架页」：正文阶段应保留大纲要点，不宜按正文页做血肉填充。
 */
public final class StructuralSlideDetector {

    private StructuralSlideDetector() {
    }

    public static boolean isStructuralSlide(String title, String chapter) {
        return isCover(title, chapter)
            || isTableOfContents(title, chapter)
            || isQaOrDiscussion(title, chapter);
    }

    public static boolean isCover(String title) {
        return isCover(title, null);
    }

    /** 封面：chapter 标「封面」，或标题仍为旧式「封面/Cover」；主标题页用 deck 标题 + chapter=封面 识别。 */
    public static boolean isCover(String title, String chapter) {
        if (chapter != null && !chapter.isBlank()) {
            String c = chapter.trim();
            if ("封面".equals(c) || c.contains("扉页")) {
                return true;
            }
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.trim();
        return t.contains("封面") || t.contains("扉页") || t.matches("(?i).*\\bcover\\b.*")
            || t.matches("(?i).*(title\\s*slide|opening).*");
    }

    public static boolean isTableOfContents(String title) {
        return isTableOfContents(title, null);
    }

    public static boolean isTableOfContents(String title, String chapter) {
        if (chapter != null && !chapter.isBlank()) {
            String c = chapter.trim();
            if ("目录".equals(c) || "目次".equals(c)) {
                return true;
            }
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.trim();
        return t.contains("目录") || t.contains("目次") || t.matches("(?i).*(contents|agenda|outline).*");
    }

    /** Q&A 页投影用：仅保留极短提示，详细话术留在讲稿 bullets。 */
    public static boolean useScriptOnlyPptBullets(String title, String chapter) {
        return isQaOrDiscussion(title, chapter);
    }

    /** 骨架页 PPT 投影要点：封面=主标题+副标题信息；目录=章节名；Q&A=仅短标题。 */
    public static List<String> pptBulletsForStructural(String title, String chapter, List<String> scriptBullets) {
        String t = title != null ? title.trim() : "";
        if (useScriptOnlyPptBullets(t, chapter)) {
            return new ArrayList<>(List.of(t.isEmpty() ? "提问与交流" : t));
        }
        if (isCover(t, chapter)) {
            List<String> ppt = new ArrayList<>();
            if (!t.isEmpty()) {
                ppt.add(t);
            }
            if (scriptBullets != null) {
                scriptBullets.stream()
                    .filter(b -> b != null && !b.isBlank())
                    .map(String::trim)
                    .filter(b -> !b.equals(t))
                    .limit(3)
                    .forEach(ppt::add);
            }
            if (ppt.isEmpty()) {
                ppt.add("（主标题）");
            }
            return ppt;
        }
        if (isTableOfContents(t, chapter)) {
            if (scriptBullets != null && !scriptBullets.isEmpty()) {
                return scriptBullets.stream()
                    .filter(b -> b != null && !b.isBlank())
                    .map(String::trim)
                    .map(b -> b.replaceFirst("^[\\d０-９]+[.、．)）]\\s*", "").trim())
                    .filter(b -> !b.isEmpty())
                    .limit(10)
                    .collect(Collectors.toCollection(ArrayList::new));
            }
            return new ArrayList<>(List.of("（章节目录）"));
        }
        return scriptBullets != null
            ? scriptBullets.stream().filter(b -> b != null && !b.isBlank()).map(String::trim).collect(Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public static boolean isQaOrDiscussion(String title, String chapter) {
        if (matchesQaOrDiscussionText(title)) {
            return true;
        }
        return chapter != null && matchesQaOrDiscussionText(chapter);
    }

    private static boolean matchesQaOrDiscussionText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.contains("问题与讨论") || t.contains("问答") || t.contains("答疑")) {
            return true;
        }
        if (t.contains("讨论") && (t.contains("问题") || t.contains("交流") || t.contains("互动"))) {
            return true;
        }
        return t.matches("(?i).*\\bq\\s*&?\\s*a\\b.*")
            || t.contains("Q&A")
            || t.contains("Q＆A")
            || t.matches("(?i).*\\b(questions?\\s*(and|&)\\s*answers?|discussion)\\b.*");
    }
}
