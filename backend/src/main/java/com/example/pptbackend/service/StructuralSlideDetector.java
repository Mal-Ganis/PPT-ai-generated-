package com.example.pptbackend.service;

/**
 * 识别封面、目录、问答/讨论等「骨架页」：正文阶段应保留大纲要点，不宜按正文页做血肉填充。
 */
public final class StructuralSlideDetector {

    private StructuralSlideDetector() {
    }

    public static boolean isStructuralSlide(String title, String chapter) {
        return isCover(title) || isTableOfContents(title) || isQaOrDiscussion(title, chapter);
    }

    public static boolean isCover(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.trim();
        return t.contains("封面") || t.contains("扉页") || t.matches("(?i).*\\bcover\\b.*")
            || t.matches("(?i).*(title\\s*slide|opening).*");
    }

    public static boolean isTableOfContents(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.trim();
        return t.contains("目录") || t.contains("目次") || t.matches("(?i).*(contents|agenda|outline).*");
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
