package com.example.pptbackend.service;

/**
 * 大纲 Prompt 中的「演示角色」块：用户可指定，否则由模型根据输入自行推断。
 */
public final class PresenterRolePromptBuilder {

    private static final int MAX_LEN = 200;

    private PresenterRolePromptBuilder() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_LEN ? trimmed.substring(0, MAX_LEN) : trimmed;
    }

    public static String buildNarratorRoleBlock(String userSpecifiedRole) {
        String role = sanitize(userSpecifiedRole);
        if (role != null) {
            return "用户指定：请全程以「" + role + "」的身份与口吻组织叙事"
                + "（章节命名、论证方式、证据类型与结论收束均须符合该角色与典型听众预期）。";
        }
        return """
            用户未指定角色。请先通读下方输入，自行推断最合适的演示身份与叙事风格（例如：学术答辩、课程讲授、技术方案评审、政策解读、科普讲座、产品发布、内训汇报、投融资路演等），再按该身份生成大纲。
            - 不要默认套用「商业叙事顾问 / 融资路演」框架；
            - 章节钩子、证据类型与结论形式须与推断身份一致；
            - 若材料偏课程、实验或论文，优先采用学术或教学视角。
            """.trim();
    }

    /**
     * 正文补全阶段与大纲共用同一角色块；未指定时额外要求与大纲口吻一致。
     */
    public static String buildSlideNarratorRoleBlock(String userSpecifiedRole) {
        if (sanitize(userSpecifiedRole) != null) {
            return buildNarratorRoleBlock(userSpecifiedRole);
        }
        return buildNarratorRoleBlock(null)
            + "\n- 正文撰写须与大纲阶段同一叙事身份与口吻，勿在单页切换人设。";
    }

    public static String resolveOutlineTemplate(String template) {
        return resolveRoleTemplate(template, TemplateKind.OUTLINE);
    }

    public static String resolveSlideTemplate(String template) {
        return resolveRoleTemplate(template, TemplateKind.SLIDE);
    }

    private enum TemplateKind {
        OUTLINE, SLIDE
    }

    private static String resolveRoleTemplate(String template, TemplateKind kind) {
        if (template == null || template.isBlank()) {
            return template;
        }
        if (template.contains("{narrator_role}")) {
            return template;
        }
        String normalized = template;
        if (kind == TemplateKind.OUTLINE) {
            normalized = normalized.replaceFirst("^你是一位专业的商业叙事顾问。请", "请");
        } else {
            normalized = normalized.replaceFirst("^你是一位资深演示文稿撰稿人。", "");
            normalized = normalized.replaceFirst("^你是一位专业的商业叙事顾问。请", "请");
        }
        normalized = normalized.stripLeading();
        return "## 演示角色\n{narrator_role}\n\n" + normalized;
    }
}
