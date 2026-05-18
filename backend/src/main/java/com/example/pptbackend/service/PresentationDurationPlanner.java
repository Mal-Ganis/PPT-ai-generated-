package com.example.pptbackend.service;

/**
 * 根据目标演讲时长，为大纲/正文 Prompt 生成页数与要点密度约束。
 */
public final class PresentationDurationPlanner {

    public static final int DEFAULT_MINUTES = 15;
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 60;

    private PresentationDurationPlanner() {
    }

    public static int clampMinutes(Integer minutes) {
        if (minutes == null) {
            return DEFAULT_MINUTES;
        }
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }

    public static String outlineGuidanceBlock(int minutes) {
        Budget b = budget(clampMinutes(minutes));
        return """
            
            ## 演讲时长约束（必须遵守）
            用户目标演讲时长：**%d 分钟**（含封面、目录的开口与收尾；正文页须控制信息密度，便于口头讲解）。
            - 建议全稿 **%d–%d 页**（含封面、目录；正文页不宜过多，避免讲不完）。
            - 封面、目录、问答/讨论等骨架页：每条要点 **≤%d 字**，以短语为主。
            - 正文页：每页 **%d–%d 条**要点，每条 **%d–%d 字**（一句可讲完，禁止堆砌长段落）。
            - 若主题复杂，优先删减页数/合并章节，**不要**通过增加单页要点条数来塞内容。
            """.formatted(
            b.minutes,
            b.minSlides,
            b.maxSlides,
            b.structuralBulletMaxChars,
            b.bodyBulletsMin,
            b.bodyBulletsMax,
            b.bodyBulletMinChars,
            b.bodyBulletMaxChars
        ).trim();
    }

    public static String slideGuidanceBlock(int minutes) {
        Budget b = budget(clampMinutes(minutes));
        return """
            
            ## 演讲时长约束（本页须遵守）
            全稿目标时长 **%d 分钟**；本页为正文页时：
            - 输出 **%d–%d 条** content（不宜超过 %d 条）。
            - 每条 **%d–%d 字**，须能口头在约 **%.1f 分钟内讲完**（本页建议口播占比约 %.0f%% 全稿）。
            - 禁止写演讲稿式长段；禁止为凑字数重复前文摘要中的案例。
            """.formatted(
            b.minutes,
            b.bodyBulletsMin,
            b.bodyBulletsMax,
            b.bodyBulletsMax,
            b.bodyBulletMinChars,
            b.bodyBulletMaxChars,
            b.minutesPerBodySlide(),
            100.0 / Math.max(1, b.estimatedBodySlides())
        ).trim();
    }

    private static Budget budget(int minutes) {
        int bodyMinutes = Math.max(3, minutes - 2);
        int estimatedBodySlides = Math.max(3, (int) Math.round(bodyMinutes / 1.5));
        int minSlides = Math.max(5, estimatedBodySlides + 2);
        int maxSlides = Math.min(20, estimatedBodySlides + 4);

        int structuralMax = minutes <= 10 ? 18 : (minutes <= 20 ? 22 : 28);
        int bodyBulletsMin = 2;
        int bodyBulletsMax = minutes <= 8 ? 3 : (minutes <= 15 ? 4 : (minutes <= 25 ? 4 : 5));
        int bulletMin = minutes <= 8 ? 18 : (minutes <= 15 ? 22 : (minutes <= 25 ? 26 : 30));
        int bulletMax = minutes <= 8 ? 28 : (minutes <= 15 ? 35 : (minutes <= 25 ? 42 : 48));

        return new Budget(
            minutes,
            minSlides,
            maxSlides,
            structuralMax,
            bodyBulletsMin,
            bodyBulletsMax,
            bulletMin,
            bulletMax,
            estimatedBodySlides
        );
    }

    private record Budget(
        int minutes,
        int minSlides,
        int maxSlides,
        int structuralBulletMaxChars,
        int bodyBulletsMin,
        int bodyBulletsMax,
        int bodyBulletMinChars,
        int bodyBulletMaxChars,
        int estimatedBodySlides
    ) {
        double minutesPerBodySlide() {
            int bodyMin = Math.max(3, minutes - 2);
            return (double) bodyMin / Math.max(1, estimatedBodySlides);
        }
    }
}
