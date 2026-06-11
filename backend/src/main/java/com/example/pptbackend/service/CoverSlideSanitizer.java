package com.example.pptbackend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 封面页禁止出现模型臆造的具体日期；仅保留占位或用户原文中的非日期信息。
 */
public final class CoverSlideSanitizer {

    private static final Pattern SPECIFIC_DATE = Pattern.compile(
        "20\\d{2}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日|"
            + "20\\d{2}\\s*年\\s*\\d{1,2}\\s*月|"
            + "20\\d{2}[-/.]\\d{1,2}([-/.]\\d{1,2})?|"
            + "\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日\\s*[,，]?\\s*20\\d{2}|"
            + "(?i)\\b20\\d{2}-\\d{2}-\\d{2}\\b");

    private static final Pattern DATE_LABEL_WITH_VALUE = Pattern.compile(
        "(日期|时间|汇报时间|演讲时间|报告时间)[:：]\\s*[^，,；;]*");

    private static final Pattern REPORTER_LABEL = Pattern.compile(
        "(汇报人|演讲者|主讲人|报告人|主讲)[:：]\\s*(.+)");

    private static final Pattern REPORT_INFO = Pattern.compile(
        "汇报信息[:：]\\s*(.+)");

    private static final Pattern REPORT_UNIT = Pattern.compile(
        "(汇报单位|所在单位|单位)[:：]\\s*(.+)");

    private CoverSlideSanitizer() {
    }

    public static List<String> defaultCoverBullets() {
        return defaultCoverBullets(null);
    }

    public static List<String> defaultCoverBullets(String presenterRole) {
        String role = PresenterRolePromptBuilder.sanitize(presenterRole);
        if (role != null) {
            return List.of(
                "副标题：（可选）",
                "汇报人：" + role,
                "日期：（待填写）");
        }
        return List.of(
            "副标题：（可选）",
            "汇报信息：单位 / 姓名 / 日期（待填写）");
    }

    public static String[] sanitizeContentArray(String[] content) {
        return sanitizeContentArray(content, null);
    }

    public static String[] sanitizeContentArray(String[] content, String presenterRole) {
        List<String> bullets = new ArrayList<>();
        if (content != null) {
            for (String line : content) {
                if (line != null && !line.isBlank()) {
                    bullets.add(line);
                }
            }
        }
        return applyPresenterRole(bullets, presenterRole).toArray(new String[0]);
    }

    public static List<String> sanitizeBullets(List<String> bullets) {
        return applyPresenterRole(bullets, null);
    }

    public static List<String> applyPresenterRole(List<String> bullets, String presenterRole) {
        String role = PresenterRolePromptBuilder.sanitize(presenterRole);
        List<String> sanitized = sanitizeBulletsOnly(bullets);
        if (role == null) {
            return sanitized;
        }
        List<String> out = new ArrayList<>();
        boolean hasReporterLine = false;
        for (String line : sanitized) {
            String aligned = alignReporterLine(line, role);
            if (isReporterRelatedLine(aligned)) {
                hasReporterLine = true;
            }
            out.add(aligned);
        }
        if (!hasReporterLine) {
            out.add("汇报人：" + role);
        }
        return out;
    }

    private static List<String> sanitizeBulletsOnly(List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return new ArrayList<>(defaultCoverBullets());
        }
        List<String> out = new ArrayList<>();
        for (String line : bullets) {
            String sanitized = sanitizeLine(line);
            if (sanitized != null && !sanitized.isBlank()) {
                out.add(sanitized);
            }
        }
        if (out.isEmpty()) {
            return new ArrayList<>(defaultCoverBullets());
        }
        return out;
    }

    static String alignReporterLine(String line, String role) {
        if (line == null || line.isBlank() || role == null || role.isBlank()) {
            return line;
        }
        String t = line.trim();
        if (t.startsWith("副标题")) {
            return t;
        }
        var reporter = REPORTER_LABEL.matcher(t);
        if (reporter.find()) {
            String value = reporter.group(2).trim();
            if (!value.equals(role)) {
                return "汇报人：" + role;
            }
            return "汇报人：" + role;
        }
        var info = REPORT_INFO.matcher(t);
        if (info.find()) {
            return "汇报信息：" + role + " / 日期（待填写）";
        }
        var unit = REPORT_UNIT.matcher(t);
        if (unit.find()) {
            String value = unit.group(2).trim();
            if (!isPlaceholderOnly(value) && !value.contains(role)) {
                return "汇报人：" + role;
            }
        }
        if (t.matches("姓名[:：].+") && !t.contains(role)) {
            return "汇报人：" + role;
        }
        return t;
    }

    static boolean isReporterRelatedLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.trim();
        return REPORTER_LABEL.matcher(t).find()
            || REPORT_INFO.matcher(t).find()
            || REPORT_UNIT.matcher(t).find()
            || t.matches("姓名[:：].+");
    }

    public static String sanitizeLine(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        String t = line.trim();
        if (isPlaceholderOnly(t)) {
            return t;
        }
        if (containsSpecificDate(t)) {
            String replaced = SPECIFIC_DATE.matcher(t).replaceAll("（待填写）");
            replaced = DATE_LABEL_WITH_VALUE.matcher(replaced).replaceAll("$1：（待填写）");
            replaced = replaced.replaceAll("（待填写）\\s*（待填写）", "（待填写）");
            if (replaced.replace("（待填写）", "").trim().isEmpty()
                || replaced.matches("(?i).*\\bdate\\b.*")) {
                return "日期：（待填写）";
            }
            return replaced.trim();
        }
        if (DATE_LABEL_WITH_VALUE.matcher(t).find() && !t.contains("待填写") && !t.contains("请在现场")) {
            return DATE_LABEL_WITH_VALUE.matcher(t).replaceFirst("$1：（待填写）");
        }
        if (looksLikeBareDateLine(t)) {
            return "日期：（待填写）";
        }
        return t;
    }

    public static boolean containsSpecificDate(String text) {
        return text != null && SPECIFIC_DATE.matcher(text).find();
    }

    private static boolean isPlaceholderOnly(String t) {
        return t.contains("待填写") || t.contains("请在现场填写") || t.contains("（可选）")
            || t.matches("(?i).*\\b(tbd|待补充|placeholder)\\b.*");
    }

    private static boolean looksLikeBareDateLine(String t) {
        return SPECIFIC_DATE.matcher(t).matches()
            || t.matches("(?i).*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\b.*20\\d{2}.*");
    }
}
