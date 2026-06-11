package com.example.pptbackend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 过滤 SEO 噪声，并区分「同领域」与「要点级事实支持」。
 */
final class IndexEvidenceQuality {

    private static final int MIN_SPECIFIC_TOKEN_HITS = 1;
    private static final double MIN_SPECIFIC_TOKEN_RATIO = 0.08;

    private static final Pattern RATIO_ANCHOR = Pattern.compile("\\d+(?:\\.\\d+)?\\s*[:：]\\s*\\d+(?:\\.\\d+)?");
    private static final Pattern PERCENT_ANCHOR = Pattern.compile("\\d+(?:\\.\\d+)?\\s*%");
    private static final Pattern WAN_ANCHOR = Pattern.compile("\\d+(?:\\.\\d+)?\\s*万");
    private static final Pattern DECIMAL_ANCHOR = Pattern.compile("\\d+\\.\\d+");

    private static final Set<String> GENERIC_DOMAIN_TOKENS = Set.of(
        "中国", "全国", "市场", "销量", "零售", "生产", "出口", "累计", "同比", "环比", "增长", "发布", "显示",
        "报告", "产业", "发展", "研究", "分析", "数据", "品牌", "产品", "技术", "企业", "公司", "行业", "汽车",
        "新能源", "乘用车", "渗透", "渗透率", "预计", "认为", "包括", "其中", "此外", "当月", "今年", "以来",
        "pdf", "百科", "百度", "数据库", "图书", "撰写", "支持", "部分", "总报告", "专题", "国际", "专家", "视点"
    );

    private IndexEvidenceQuality() {
    }

    enum SupportLevel {
        STRONG,
        DOMAIN_ONLY,
        IRRELEVANT
    }

    record SupportAssessment(SupportLevel level, String note) {}

    /** 词重叠率（要点 + 页标题 vs 证据），与抽检用的 overlapRatio 对齐。 */
    static double lexicalOverlapRatio(String statement, String slideTitle, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return 0;
        }
        String query = (nullToEmpty(slideTitle) + " " + nullToEmpty(statement)).trim();
        if (query.isEmpty()) {
            query = nullToEmpty(statement);
        }
        return FactConsistencyService.overlapRatio(query, evidence);
    }

    static boolean passesEvidenceGate(String statement, String slideTitle, String evidence) {
        if (evidence == null || evidence.isBlank() || isSeoSpamEvidence(evidence)) {
            return false;
        }
        if (hasDomainOverlap(statement, slideTitle, evidence)) {
            return true;
        }
        return lexicalOverlapRatio(statement, slideTitle, evidence) >= 0.10;
    }

    /**
     * 汇总用支持度：DOMAIN_ONLY 给部分分（领域相关但未逐字命中关键数据），避免事实率恒为 0。
     */
    static double effectiveRateContribution(double rawScore, SupportLevel level) {
        if (level == null || rawScore < 0) {
            return 0;
        }
        return switch (level) {
            case STRONG -> rawScore;
            case DOMAIN_ONLY -> Math.max(0.28, rawScore * 0.72);
            case IRRELEVANT -> 0;
        };
    }

    static boolean isSeoSpamEvidence(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int pptHits = countSubstring(lower, "ppt");
        if (pptHits >= 4) {
            return true;
        }
        if (lower.contains("毕业答辩") && lower.contains("产品发布会") && pptHits >= 2) {
            return true;
        }
        if (countSubstring(content, "产品发布会PPT") >= 2) {
            return true;
        }
        if (countSubstring(content, "毕业答辩PPT") >= 2) {
            return true;
        }
        if (content.contains("查看更多") && (content.contains("模板") || content.contains("套装"))) {
            return true;
        }
        if (content.contains("## 更多") && content.contains("模板") && pptHits >= 2) {
            return true;
        }
        return false;
    }

    static SupportAssessment assessSupport(String statement, String slideTitle, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return new SupportAssessment(SupportLevel.IRRELEVANT, noEvidenceMessage());
        }
        if (isSeoSpamEvidence(evidence)) {
            return new SupportAssessment(SupportLevel.IRRELEVANT, seoSpamMessage());
        }
        double lexical = lexicalOverlapRatio(statement, slideTitle, evidence);
        boolean domainOk = hasDomainOverlap(statement, slideTitle, evidence);
        if (!domainOk && lexical < 0.10) {
            return new SupportAssessment(SupportLevel.IRRELEVANT, lowOverlapMessage());
        }
        List<String> significantAnchors = extractSignificantAnchors(statement + " " + nullToEmpty(slideTitle));
        double specificRatio = specificTokenOverlapRatio(statement, slideTitle, evidence);
        long specificHits = specificTokenHits(statement, slideTitle, evidence);

        if (!significantAnchors.isEmpty() && !anchorsMatch(evidence, significantAnchors)) {
            if (specificHits >= 1 || specificRatio >= 0.08 || lexical >= 0.18) {
                return new SupportAssessment(
                    SupportLevel.DOMAIN_ONLY,
                    "检索内容与主题同属一个领域，但未出现要点中的关键数字/比例，建议补充更精确的来源");
            }
            return new SupportAssessment(
                SupportLevel.DOMAIN_ONLY,
                "证据与要点仅领域相近，未覆盖本句核心概念或数据");
        }
        if (specificHits >= MIN_SPECIFIC_TOKEN_HITS || specificRatio >= MIN_SPECIFIC_TOKEN_RATIO
            || lexical >= 0.22) {
            return new SupportAssessment(SupportLevel.STRONG, null);
        }
        if (specificHits >= 1 || specificRatio >= 0.07 || lexical >= 0.14) {
            return new SupportAssessment(
                SupportLevel.DOMAIN_ONLY,
                "证据与要点主题相关，但关键概念匹配偏弱，建议核对数据出处");
        }
        return new SupportAssessment(
            SupportLevel.DOMAIN_ONLY,
            "证据与要点仅领域相近，未覆盖本句核心概念或数据");
    }

    static boolean isStrongSupport(String statement, String slideTitle, String evidence) {
        return assessSupport(statement, slideTitle, evidence).level() == SupportLevel.STRONG;
    }

    static boolean isCandidateEvidence(String statement, String slideTitle, String evidence) {
        return passesEvidenceGate(statement, slideTitle, evidence);
    }

    static String irrelevantEvidenceMessage() {
        return "（未找到可用证据片段）";
    }

    static String noEvidenceMessage() {
        return "索引中未检索到与要点相关的片段";
    }

    static String emptyIndexMessage() {
        return "项目向量索引为空：请在大纲阶段加载外部资料，或在正文生成时写入引用后再评估";
    }

    static String seoSpamMessage() {
        return "检索片段含 PPT 模板/SEO 噪声特征，已排除";
    }

    static String lowOverlapMessage() {
        return "检索片段与要点词汇重叠偏低（低于 10%），未采信";
    }

    static String skippedStructuralMessage() {
        return "（封面/目录/Q&A 等结构性页面，不参与事实抽检）";
    }

    private static boolean hasDomainOverlap(String statement, String slideTitle, String evidence) {
        Set<String> query = buildQueryTokens(statement, slideTitle);
        if (query.isEmpty()) {
            return true;
        }
        Set<String> ev = FactConsistencyService.tokens(evidence);
        long hit = query.stream().filter(ev::contains).count();
        return hit >= 2 || hit * 1.0 / query.size() >= 0.08;
    }

    private static Set<String> buildQueryTokens(String statement, String slideTitle) {
        Set<String> query = new HashSet<>();
        query.addAll(FactConsistencyService.tokens(statement));
        query.addAll(FactConsistencyService.tokens(nullToEmpty(slideTitle)));
        query.removeIf(t -> t.length() < 2 || isFormatStopToken(t));
        return query;
    }

    private static Set<String> specificTokens(String statement, String slideTitle) {
        Set<String> query = buildQueryTokens(statement, slideTitle);
        query.removeIf(GENERIC_DOMAIN_TOKENS::contains);
        return query;
    }

    private static long specificTokenHits(String statement, String slideTitle, String evidence) {
        Set<String> specific = specificTokens(statement, slideTitle);
        if (specific.isEmpty()) {
            return 0;
        }
        Set<String> ev = FactConsistencyService.tokens(evidence);
        return specific.stream().filter(ev::contains).count();
    }

    private static double specificTokenOverlapRatio(String statement, String slideTitle, String evidence) {
        Set<String> specific = specificTokens(statement, slideTitle);
        if (specific.isEmpty()) {
            return 0;
        }
        Set<String> ev = FactConsistencyService.tokens(evidence);
        long hit = specific.stream().filter(ev::contains).count();
        return hit * 1.0 / specific.size();
    }

    private static List<String> extractSignificantAnchors(String text) {
        List<String> anchors = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return anchors;
        }
        addMatches(anchors, RATIO_ANCHOR, text);
        addMatches(anchors, PERCENT_ANCHOR, text);
        addMatches(anchors, WAN_ANCHOR, text);
        for (Matcher m = DECIMAL_ANCHOR.matcher(text); m.find(); ) {
            String v = m.group();
            if (v.length() >= 3 && !isYearLike(v)) {
                anchors.add(v);
            }
        }
        return anchors.stream().distinct().toList();
    }

    private static boolean isYearLike(String v) {
        if (!v.contains(".")) {
            try {
                int n = Integer.parseInt(v);
                return n >= 2000 && n <= 2035;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static List<String> extractAnchors(String text) {
        return extractSignificantAnchors(text);
    }

    private static void addMatches(List<String> out, Pattern pattern, String text) {
        for (Matcher m = pattern.matcher(text); m.find(); ) {
            out.add(m.group().replace("：", ":").replaceAll("\\s+", ""));
        }
    }

    private static boolean anchorsMatch(String evidence, List<String> anchors) {
        String normalizedEv = evidence.replace("：", ":").replaceAll("\\s+", "");
        for (String anchor : anchors) {
            String a = anchor.replace("：", ":").replaceAll("\\s+", "");
            if (normalizedEv.contains(a)) {
                return true;
            }
            if (a.contains(":")) {
                String alt = a.replace(":", "：");
                if (evidence.replaceAll("\\s+", "").contains(alt.replaceAll("\\s+", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFormatStopToken(String token) {
        return Set.of("ppt", "模板", "套装", "简约", "查看更多", "qa", "如有", "需要", "进一步", "提供").contains(token);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static int countSubstring(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
