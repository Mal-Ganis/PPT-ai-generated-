package com.example.pptbackend.service;

import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutlineGenerationService {

    private static final Logger log = LoggerFactory.getLogger(OutlineGenerationService.class);

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 叙事与红线已由数据库默认 Prompt 定义；此处保留为空以免双重指令冲突。 */
    private static final String OUTLINE_NARRATIVE_HINT = "";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;
    private final int outlineMaxTokens;
    private final boolean defaultOutlineIncludeQaSlide;

    public OutlineGenerationService(ObjectMapper objectMapper,
                                    SystemConfigService systemConfigService,
                                    DeepseekChatClient deepseekChatClient,
                                    @Value("${outline.max-tokens:4096}") int outlineMaxTokens,
                                    @Value("${outline.include-qa-slide:true}") boolean defaultOutlineIncludeQaSlide) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
        this.outlineMaxTokens = Math.max(512, outlineMaxTokens);
        this.defaultOutlineIncludeQaSlide = defaultOutlineIncludeQaSlide;
    }

    public ProjectOutlineResponse generateOutline(String topic) {
        return generateOutline(topic, PresentationDurationPlanner.DEFAULT_MINUTES);
    }

    public ProjectOutlineResponse generateOutline(String topic, int presentationDurationMinutes) {
        SystemConfigDto config = systemConfigService.getSystemConfig();
        String augmented = topic != null ? topic : "";
        if (!OUTLINE_NARRATIVE_HINT.isBlank()) {
            augmented = OUTLINE_NARRATIVE_HINT + "\n\n" + augmented;
        }
        String[] topicAndRetrieval = splitTopicAndRetrieval(augmented);
        String prompt = formatPrompt(
            config.getOutlinePromptTemplate(),
            Map.of("content", topicAndRetrieval[0], "retrieved_context", topicAndRetrieval[1]));
        prompt = prompt + "\n\n" + PresentationDurationPlanner.outlineGuidanceBlock(presentationDurationMinutes);
        boolean includeQaSlide = resolveOutlineIncludeQaSlide(config);
        prompt = prompt + "\n\n" + outlineQaGuidanceBlock(includeQaSlide);
        prompt = prompt + "\n\n" + outlineStructuralUniquenessBlock();

        String body = buildOutlineRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(120));
        try {
            ProjectOutlineResponse parsed = parseOutlineResponse(responseText);
            return finalizeOutline(parsed, topicAndRetrieval[0], includeQaSlide);
        } catch (Exception e) {
            String assistant = extractAssistantContent(responseText);
            if (assistant != null) {
                try {
                    ProjectOutlineResponse recovered = parseOutlineFromAssistantJson(assistant);
                    return finalizeOutline(recovered, topicAndRetrieval[0], includeQaSlide);
                } catch (Exception ignored) {
                    // 已由 parseOutlineFromAssistantJson 内部尝试围栏与括号截取
                }
            }
            String raw = topic != null ? topic : "";
            String[] parts = splitTopicAndRetrieval(raw);
            log.warn("Outline JSON parse failed; degraded outline. Cause: {}", e.getMessage());
            ProjectOutlineResponse degraded = degradedOutline(parts[0], parts[1]);
            return finalizeOutline(degraded, parts[0], includeQaSlide);
        }
    }

    private boolean resolveOutlineIncludeQaSlide(SystemConfigDto config) {
        if (config.getOutlineIncludeQaSlide() != null) {
            return config.getOutlineIncludeQaSlide();
        }
        return defaultOutlineIncludeQaSlide;
    }

    static String outlineStructuralUniquenessBlock() {
        return """
            ## 骨架页唯一性（必须遵守）
            - 全稿**只能有 1 页封面、1 页目录**（目录页 title 用「目录」或「目次」；第 3 页起为正文）。
            - **封面**：`title` 必须等于 JSON 顶层 `title`（演示主标题，禁止把页面标题写成「封面」二字）；`chapter` 填「封面」；`content` 只写副标题、汇报人等补充信息，不要重复主标题。
            - **目录**：`title` 用「目录」或「目次」；`chapter` 填「目录」；`content` 列出 **4–10 个章节名**（叙事阶段名，如「背景与问题」「核心机制」），**禁止**把各正文页的 page title 逐条抄进目录。
            - 正文页：`title` 为**该页主题短标题**；`chapter` 为所属章节名，且**必须**与目录 `content` 中某一条一致。
            - **禁止**在正文中再插入第二页目录。
            """.trim();
    }

    static String outlineQaGuidanceBlock(boolean includeQaSlide) {
        if (includeQaSlide) {
            return """
                ## Q&A 页结构约束（必须遵守）
                - 全稿**必须**包含且仅包含 **1 页** Q&A / 问答 / 答疑 / 问题与讨论（`chapter` 建议「收尾」）。
                - 该页为**全稿最后一页**（在总结/致谢之后）。
                - `content` 写 3–5 条**讲稿用**完整句：预留提问时间、可准备的高频问题与回应要点、互动收尾等（仅出现在演讲稿，不会逐条打在幻灯片上）。
                - 该页 `title` 用「Q&A」或「问答与讨论」等短标题即可。
                """.trim();
        }
        return """
            ## Q&A 页结构约束
            - 本演示**不要**单独增加 Q&A / 问答 / 答疑 / 问题与讨论 页；结论或致谢页作为收束即可。
            """.trim();
    }

    private ProjectOutlineResponse finalizeOutline(
        ProjectOutlineResponse parsed,
        String themeLine,
        boolean includeQaSlide
    ) {
        ProjectOutlineResponse withCover = ensureCoverAndTableOfContents(parsed, themeLine);
        ProjectOutlineResponse withQa = ensureQaSlide(withCover, includeQaSlide);
        ProjectOutlineResponse normalized = validateAndNormalizeOutline(withQa);
        applyChapterAndTitleSemantics(normalized);
        if (normalized.getSlides() == null || normalized.getSlides().isEmpty()) {
            throw new IllegalStateException("outline_json_has_no_slides");
        }
        return normalized;
    }

    /**
     * 保证全稿仅有 1 页封面（第 1 页）、1 页目录（第 2 页）。模型多生成的封面/目录会被合并丢弃，避免「两个目录」。
     */
    public ProjectOutlineResponse ensureCoverAndTableOfContents(ProjectOutlineResponse outline, String themeLine) {
        if (outline == null) {
            return null;
        }
        if (outline.getSlides() == null) {
            outline.setSlides(new ArrayList<>());
        }
        List<ProjectOutlineResponse.OutlineSlide> slides = outline.getSlides();
        String deckTitle = outline.getTitle() != null && !outline.getTitle().isBlank()
            ? outline.getTitle().trim()
            : firstLine(themeLine != null ? themeLine : "演示文稿");

        List<ProjectOutlineResponse.OutlineSlide> coverCandidates = new ArrayList<>();
        List<ProjectOutlineResponse.OutlineSlide> tocCandidates = new ArrayList<>();
        List<ProjectOutlineResponse.OutlineSlide> bodySlides = new ArrayList<>();

        for (ProjectOutlineResponse.OutlineSlide slide : slides) {
            if (slide == null) {
                continue;
            }
            if (looksLikeCoverSlide(slide, deckTitle)) {
                coverCandidates.add(slide);
            } else if (looksLikeTocSlide(slide)) {
                tocCandidates.add(slide);
            } else {
                bodySlides.add(slide);
            }
        }

        if (coverCandidates.isEmpty() && !bodySlides.isEmpty()) {
            ProjectOutlineResponse.OutlineSlide first = bodySlides.get(0);
            if (titleMatchesDeck(first.getTitle(), deckTitle)) {
                coverCandidates.add(first);
                bodySlides.remove(0);
            }
        }

        ProjectOutlineResponse.OutlineSlide cover = coverCandidates.isEmpty()
            ? buildDefaultCoverSlide(deckTitle)
            : pickRichestSlide(coverCandidates);
        normalizeCoverSlide(cover, deckTitle);

        ProjectOutlineResponse.OutlineSlide toc = tocCandidates.isEmpty()
            ? buildDefaultTocSlide(bodySlides)
            : pickRichestSlide(tocCandidates);
        normalizeTocSlide(toc);

        List<ProjectOutlineResponse.OutlineSlide> normalized = new ArrayList<>();
        normalized.add(cover);
        normalized.add(toc);
        normalized.addAll(bodySlides);
        outline.setSlides(normalized);
        return outline;
    }

    private static ProjectOutlineResponse.OutlineSlide buildDefaultCoverSlide(String deckTitle) {
        ProjectOutlineResponse.OutlineSlide cover = new ProjectOutlineResponse.OutlineSlide();
        cover.setTitle(deckTitle);
        cover.setChapter("封面");
        cover.setContent(new String[]{
            "副标题：用一句话点出听众收益或矛盾（可改）",
            "汇报信息：单位 / 姓名 / 日期（请在现场填写）"
        });
        cover.setNotes("10–20 秒开场：自报家门 + 今天讲什么 + 为什么值得听。");
        return cover;
    }

    private static ProjectOutlineResponse.OutlineSlide buildDefaultTocSlide(
        List<ProjectOutlineResponse.OutlineSlide> bodySlides
    ) {
        List<String> chapterNames = collectOrderedChapterNames(bodySlides);
        List<String> tocPoints = formatTocLines(chapterNames);
        if (tocPoints.isEmpty()) {
            tocPoints.add("1）后续各章将在确认大纲后展开");
        }
        ProjectOutlineResponse.OutlineSlide toc = new ProjectOutlineResponse.OutlineSlide();
        toc.setTitle("目录");
        toc.setChapter("目录");
        toc.setContent(tocPoints.toArray(new String[0]));
        toc.setNotes("20–40 秒：用口语串一下章节路线，不要逐字念完所有条目。");
        return toc;
    }

    /**
     * 封面主标题、目录章节列表、正文页 chapter 与目录对齐。
     */
    void applyChapterAndTitleSemantics(ProjectOutlineResponse outline) {
        if (outline == null || outline.getSlides() == null || outline.getSlides().size() < 2) {
            return;
        }
        String deckTitle = outline.getTitle() != null && !outline.getTitle().isBlank()
            ? outline.getTitle().trim()
            : "演示文稿";

        List<ProjectOutlineResponse.OutlineSlide> slides = outline.getSlides();
        ProjectOutlineResponse.OutlineSlide cover = slides.get(0);
        normalizeCoverSlide(cover, deckTitle);

        ProjectOutlineResponse.OutlineSlide toc = slides.get(1);
        normalizeTocSlide(toc);

        List<ProjectOutlineResponse.OutlineSlide> bodySlides = new ArrayList<>();
        List<ProjectOutlineResponse.OutlineSlide> qaSlides = new ArrayList<>();
        for (int i = 2; i < slides.size(); i++) {
            ProjectOutlineResponse.OutlineSlide s = slides.get(i);
            if (s == null) {
                continue;
            }
            if (StructuralSlideDetector.isQaOrDiscussion(s.getTitle(), s.getChapter())) {
                normalizeQaSlide(s);
                qaSlides.add(s);
            } else if (!StructuralSlideDetector.isCover(s.getTitle(), s.getChapter())
                && !StructuralSlideDetector.isTableOfContents(s.getTitle(), s.getChapter())) {
                bodySlides.add(s);
            }
        }

        List<String> tocChapters = parseTocChapterLines(toc.getContent());
        if (tocChapters.isEmpty()) {
            tocChapters = collectOrderedChapterNames(bodySlides);
            if (!tocChapters.isEmpty()) {
                toc.setContent(formatTocLines(tocChapters).toArray(new String[0]));
            }
        }
        assignChaptersToBodySlides(bodySlides, tocChapters);
        rebuildTocFromBodyIfPageTitlesLeaked(toc, bodySlides, tocChapters);

        List<ProjectOutlineResponse.OutlineSlide> merged = new ArrayList<>();
        merged.add(cover);
        merged.add(toc);
        merged.addAll(bodySlides);
        merged.addAll(qaSlides);
        outline.setSlides(merged);
    }

    private static void normalizeCoverSlide(ProjectOutlineResponse.OutlineSlide cover, String deckTitle) {
        if (cover == null) {
            return;
        }
        String title = cover.getTitle() != null ? cover.getTitle().trim() : "";
        if (title.isEmpty() || StructuralSlideDetector.isCover(title, null)) {
            cover.setTitle(deckTitle);
        }
        cover.setChapter("封面");
        cover.setContent(stripRedundantMainTitleBullets(cover.getContent(), deckTitle));
    }

    private static void normalizeTocSlide(ProjectOutlineResponse.OutlineSlide toc) {
        if (toc == null) {
            return;
        }
        if (toc.getTitle() == null || toc.getTitle().isBlank()
            || !StructuralSlideDetector.isTableOfContents(toc.getTitle(), toc.getChapter())) {
            toc.setTitle("目录");
        }
        toc.setChapter("目录");
    }

    private static void normalizeQaSlide(ProjectOutlineResponse.OutlineSlide qa) {
        if (qa == null) {
            return;
        }
        if (qa.getChapter() == null || qa.getChapter().isBlank()) {
            qa.setChapter("收尾");
        }
        String title = qa.getTitle() != null ? qa.getTitle().trim() : "";
        if (title.isEmpty()) {
            qa.setTitle("Q&A 与讨论");
        }
    }

    private static String[] stripRedundantMainTitleBullets(String[] content, String deckTitle) {
        if (content == null || content.length == 0) {
            return content;
        }
        List<String> kept = new ArrayList<>();
        for (String line : content) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String t = line.trim();
            if (t.startsWith("主标题：") || t.startsWith("主标题:")) {
                String rest = t.substring(t.indexOf('：') >= 0 ? t.indexOf('：') + 1 : t.indexOf(':') + 1).trim();
                if (rest.equals(deckTitle) || rest.isEmpty()) {
                    continue;
                }
            }
            if (t.equals(deckTitle)) {
                continue;
            }
            kept.add(t);
        }
        return kept.toArray(new String[0]);
    }

    private static List<String> collectOrderedChapterNames(List<ProjectOutlineResponse.OutlineSlide> bodySlides) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProjectOutlineResponse.OutlineSlide s : bodySlides) {
            if (s == null || StructuralSlideDetector.isQaOrDiscussion(s.getTitle(), s.getChapter())) {
                continue;
            }
            String ch = s.getChapter();
            if (ch != null && !ch.isBlank()
                && !StructuralSlideDetector.isCover(s.getTitle(), ch)
                && !StructuralSlideDetector.isTableOfContents(s.getTitle(), ch)) {
                String key = ch.trim();
                if (seen.add(key)) {
                    names.add(key);
                }
            }
        }
        return names;
    }

    private static List<String> formatTocLines(List<String> chapterNames) {
        List<String> lines = new ArrayList<>();
        int n = 1;
        for (String name : chapterNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            lines.add(n + "）" + name.trim());
            n++;
        }
        return lines;
    }

    private static List<String> parseTocChapterLines(String[] content) {
        List<String> chapters = new ArrayList<>();
        if (content == null) {
            return chapters;
        }
        Set<String> seen = new HashSet<>();
        for (String line : content) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String t = line.trim().replaceFirst("^[\\d０-９]+[.、．)）]\\s*", "").trim();
            if (t.isEmpty() || StructuralSlideDetector.isTableOfContents(t, null)) {
                continue;
            }
            if (seen.add(t)) {
                chapters.add(t);
            }
        }
        return chapters;
    }

    private static void assignChaptersToBodySlides(
        List<ProjectOutlineResponse.OutlineSlide> bodySlides,
        List<String> tocChapters
    ) {
        if (bodySlides.isEmpty() || tocChapters.isEmpty()) {
            return;
        }
        int chapterCount = tocChapters.size();
        int bodyCount = bodySlides.size();
        int slideIdx = 0;
        for (int c = 0; c < chapterCount && slideIdx < bodyCount; c++) {
            String chapter = tocChapters.get(c);
            int slidesInChapter = (bodyCount - slideIdx) / (chapterCount - c);
            slidesInChapter = Math.max(1, slidesInChapter);
            for (int j = 0; j < slidesInChapter && slideIdx < bodyCount; j++, slideIdx++) {
                ProjectOutlineResponse.OutlineSlide slide = bodySlides.get(slideIdx);
                String existing = slide.getChapter();
                if (existing == null || existing.isBlank()
                    || StructuralSlideDetector.isTableOfContents(slide.getTitle(), existing)) {
                    slide.setChapter(chapter);
                }
            }
        }
        while (slideIdx < bodyCount) {
            ProjectOutlineResponse.OutlineSlide slide = bodySlides.get(slideIdx++);
            if (!StructuralSlideDetector.isQaOrDiscussion(slide.getTitle(), slide.getChapter())) {
                String existing = slide.getChapter();
                if (existing == null || existing.isBlank()) {
                    slide.setChapter(tocChapters.get(tocChapters.size() - 1));
                }
            }
        }
    }

    /** 目录 content 若与正文 title 高度重合，则按正文 chapter 重建目录条目。 */
    private static void rebuildTocFromBodyIfPageTitlesLeaked(
        ProjectOutlineResponse.OutlineSlide toc,
        List<ProjectOutlineResponse.OutlineSlide> bodySlides,
        List<String> tocChapters
    ) {
        if (toc == null || bodySlides.isEmpty()) {
            return;
        }
        Set<String> bodyTitles = bodySlides.stream()
            .filter(s -> s != null && !StructuralSlideDetector.isQaOrDiscussion(s.getTitle(), s.getChapter()))
            .map(s -> s.getTitle() != null ? s.getTitle().trim() : "")
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
        List<String> parsed = parseTocChapterLines(toc.getContent());
        long overlap = parsed.stream().filter(bodyTitles::contains).count();
        if (overlap >= 2 || (parsed.size() >= 2 && overlap >= 1 && tocChapters.isEmpty())) {
            List<String> fromBody = collectOrderedChapterNames(bodySlides);
            if (!fromBody.isEmpty()) {
                toc.setContent(formatTocLines(fromBody).toArray(new String[0]));
            }
        }
    }

    private static boolean titleMatchesDeck(String slideTitle, String deckTitle) {
        if (slideTitle == null || deckTitle == null) {
            return false;
        }
        String a = slideTitle.trim();
        String b = deckTitle.trim();
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    /** 多页封面/目录时保留要点更丰富的一页。 */
    private static ProjectOutlineResponse.OutlineSlide pickRichestSlide(
        List<ProjectOutlineResponse.OutlineSlide> candidates
    ) {
        ProjectOutlineResponse.OutlineSlide best = candidates.get(0);
        int bestScore = structuralRichnessScore(best);
        for (int i = 1; i < candidates.size(); i++) {
            int score = structuralRichnessScore(candidates.get(i));
            if (score > bestScore) {
                best = candidates.get(i);
                bestScore = score;
            }
        }
        return best;
    }

    private static int structuralRichnessScore(ProjectOutlineResponse.OutlineSlide slide) {
        int score = 0;
        if (slide.getContent() != null) {
            for (String line : slide.getContent()) {
                if (line != null && !line.isBlank()) {
                    score += line.length();
                }
            }
        }
        if (slide.getNotes() != null && !slide.getNotes().isBlank()) {
            score += 20;
        }
        return score;
    }

    /**
     * 当配置要求包含 Q&A 时，若模型未生成问答页则在末位（总结页之后）自动补一页。
     */
    public ProjectOutlineResponse ensureQaSlide(ProjectOutlineResponse outline, boolean includeQaSlide) {
        if (!includeQaSlide || outline == null) {
            return outline;
        }
        if (outline.getSlides() == null) {
            outline.setSlides(new ArrayList<>());
        }
        List<ProjectOutlineResponse.OutlineSlide> slides = outline.getSlides();
        for (ProjectOutlineResponse.OutlineSlide slide : slides) {
            if (slide != null && StructuralSlideDetector.isQaOrDiscussion(slide.getTitle(), slide.getChapter())) {
                return outline;
            }
        }

        ProjectOutlineResponse.OutlineSlide qa = new ProjectOutlineResponse.OutlineSlide();
        qa.setTitle("Q&A 与讨论");
        qa.setChapter("收尾");
        qa.setContent(new String[]{
            "预留 3–5 分钟回答听众提问，并说明可接受的提问范围",
            "可提前准备 2 个与主题相关的高频问题及口头回应要点（勿逐条打在幻灯片上）",
            "互动收尾：致谢、后续资料获取方式或联系方式（如适用）"
        });
        qa.setNotes("30–60 秒：邀请提问；详细话术见讲稿要点，幻灯片仅保留短标题。");

        slides.add(qa);
        return outline;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "演示文稿";
        }
        int p = text.indexOf('\n');
        String line = (p > 0 ? text.substring(0, p) : text).trim();
        return line.isEmpty() ? "演示文稿" : line;
    }

    private static boolean looksLikeCoverSlide(ProjectOutlineResponse.OutlineSlide s, String deckTitle) {
        if (s == null) {
            return false;
        }
        return StructuralSlideDetector.isCover(s.getTitle(), s.getChapter())
            || titleMatchesDeck(s.getTitle(), deckTitle);
    }

    private static boolean looksLikeTocSlide(ProjectOutlineResponse.OutlineSlide s) {
        return s != null && StructuralSlideDetector.isTableOfContents(s.getTitle(), s.getChapter());
    }

    /**
     * P1.2：去重标题、避免空章节名占用叙事位。
     */
    public ProjectOutlineResponse validateAndNormalizeOutline(ProjectOutlineResponse outline) {
        if (outline == null || outline.getSlides() == null) {
            return outline;
        }
        int seq = 1;
        Set<String> usedTitles = new HashSet<>();
        for (ProjectOutlineResponse.OutlineSlide slide : outline.getSlides()) {
            String raw = slide.getTitle() != null ? slide.getTitle().trim() : "未命名页";
            String candidate = raw.isEmpty() ? "未命名页" : raw;
            String unique = candidate;
            int n = 2;
            while (usedTitles.contains(unique)) {
                unique = candidate + "（" + n + "）";
                n++;
            }
            slide.setTitle(unique);
            usedTitles.add(unique);
            slide.setId(seq++);
        }
        return outline;
    }

    /**
     * 将上传节选 / 向量片段 / 权威检索等与「主题句」拆开，便于 Prompt 中分别标注叙事主料与检索上下文。
     */
    static String[] splitTopicAndRetrieval(String full) {
        if (full == null || full.isBlank()) {
            return new String[]{"", "（空输入）"};
        }
        String[] markers = {
            "\n\n【权威检索参考】",
            "\n\n【权威检索补充】",
            "\n\n向量检索到的文档片段：",
            "\n\n上传全文节选："
        };
        int cut = -1;
        for (String m : markers) {
            int p = full.indexOf(m);
            if (p >= 0 && (cut < 0 || p < cut)) {
                cut = p;
            }
        }
        if (cut >= 0) {
            return new String[]{full.substring(0, cut).trim(), full.substring(cut).trim()};
        }
        return new String[]{
            full.trim(),
            "（当前未检测到独立检索/节选标题块；上文整体视为素材，仍须写出数字锚点与递进章节；常识内容须在 notes 中标明 [待补充权威来源]。）"
        };
    }

    private String formatPrompt(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prompt;
    }

    private String buildOutlineRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getLlmModel());
        String system = """
            你是一个只输出 JSON 的接口后端。严格遵守：回复正文必须是**单个合法 JSON 对象**，首字符为 {、末字符为 }；禁止 Markdown 围栏、禁止 JSON 以外的任何说明或思考过程。
            """.trim();
        payload.put("messages", List.of(
            Map.of("role", "system", "content", system),
            Map.of("role", "user", "content", prompt)));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", outlineMaxTokens);
        payload.put("top_p", config.getTopP());
        payload.put("top_k", config.getTopK());
        payload.put("response_format", Map.of("type", "json_object"));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generation request", e);
        }
    }

    private ProjectOutlineResponse parseOutlineResponse(String responseText) throws IOException {
        Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
        String assistant = extractAssistantContentFromParsed(parsed);
        if (assistant == null || assistant.isBlank()) {
            throw new IllegalStateException("Empty assistant content");
        }
        return parseOutlineFromAssistantJson(assistant);
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantContentFromParsed(Map<String, Object> parsed) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return null;
        }
        return (String) message.get("content");
    }

    private String extractAssistantContent(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            return extractAssistantContentFromParsed(parsed);
        } catch (IOException e) {
            return null;
        }
    }

    private ProjectOutlineResponse parseOutlineFromAssistantJson(String content) throws IOException {
        String fenced = stripMarkdownFence(content.trim());
        try {
            return parseOutlineJsonObject(fenced);
        } catch (IOException first) {
            String extracted = extractBalancedJsonObject(fenced);
            if (extracted != null && !extracted.equals(fenced)) {
                return parseOutlineJsonObject(extracted);
            }
            throw first;
        }
    }

    /**
     * 从模型混排输出中截取第一个顶层 JSON 对象（跳过后缀说明、前缀推理等）。
     */
    static String extractBalancedJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String stripMarkdownFence(String content) {
        Matcher m = JSON_FENCE.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private ProjectOutlineResponse parseOutlineJsonObject(String json) throws IOException {
        Map<String, Object> outline = objectMapper.readValue(json, new TypeReference<>() {});
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setTitle((String) outline.getOrDefault("title", "PPT 演示文稿"));

        List<Map<String, Object>> chapters = (List<Map<String, Object>>) outline.get("chapters");
        List<Map<String, Object>> slides = (List<Map<String, Object>>) outline.get("slides");

        if (chapters != null && !chapters.isEmpty()) {
            for (Map<String, Object> ch : chapters) {
                String chapterName = firstChapterTitle(ch);
                List<Map<String, Object>> chSlides = (List<Map<String, Object>>) ch.get("slides");
                if (chSlides == null || chSlides.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> slide : chSlides) {
                    response.getSlides().add(mapSlideMap(slide, chapterName));
                }
            }
        } else if (slides != null) {
            for (Map<String, Object> slide : slides) {
                Object pagesVal = slide.get("pages");
                String chapterHint = slide.get("chapter") != null ? slide.get("chapter").toString().trim() : null;
                if (pagesVal instanceof List<?> pageList && !pageList.isEmpty()) {
                    for (Object pObj : pageList) {
                        if (pObj instanceof Map<?, ?> pm) {
                            response.getSlides().add(mapSlideMap((Map<String, Object>) pm, chapterHint));
                        }
                    }
                } else {
                    response.getSlides().add(mapSlideMap(slide, chapterHint));
                }
            }
        }
        return response;
    }

    private static String firstChapterTitle(Map<String, Object> ch) {
        Object c = ch.get("chapter");
        if (c != null && !c.toString().isBlank()) {
            return c.toString().trim();
        }
        Object t = ch.get("title");
        if (t != null && !t.toString().isBlank()) {
            return t.toString().trim();
        }
        Object n = ch.get("name");
        if (n != null && !n.toString().isBlank()) {
            return n.toString().trim();
        }
        return "章节";
    }

    private ProjectOutlineResponse.OutlineSlide mapSlideMap(Map<String, Object> slide, String chapterFallback) {
        ProjectOutlineResponse.OutlineSlide outlineSlide = new ProjectOutlineResponse.OutlineSlide();
        Object idVal = slide.get("id");
        outlineSlide.setId(idVal instanceof Number ? ((Number) idVal).intValue() : 0);
        Object titleObj = slide.get("title");
        outlineSlide.setTitle(titleObj != null ? titleObj.toString() : "");
        Object contentValue = slide.get("content");
        if (contentValue instanceof List<?> list) {
            String[] contentArray = list.stream()
                .map(Object::toString)
                .toArray(String[]::new);
            outlineSlide.setContent(contentArray);
        } else {
            outlineSlide.setContent(new String[0]);
        }
        Object notesObj = slide.get("notes");
        outlineSlide.setNotes(notesObj != null ? notesObj.toString() : "");
        Object chObj = slide.get("chapter");
        String slideChapter = chObj != null ? chObj.toString().trim() : "";
        if (!slideChapter.isEmpty()) {
            outlineSlide.setChapter(slideChapter);
        } else if (chapterFallback != null && !chapterFallback.isBlank()) {
            outlineSlide.setChapter(chapterFallback.trim());
        }
        return outlineSlide;
    }

    private ProjectOutlineResponse degradedOutline(String contentLine, String retrievedLine) {
        ProjectOutlineResponse response = new ProjectOutlineResponse();
        response.setTitle("大纲（降级生成）");
        ProjectOutlineResponse.OutlineSlide slide = new ProjectOutlineResponse.OutlineSlide();
        slide.setId(1);
        slide.setTitle("主题概要");
        String c = contentLine != null ? contentLine.trim() : "";
        if (c.length() > 500) {
            c = c.substring(0, 500) + "…";
        }
        String r = retrievedLine != null ? retrievedLine.trim() : "";
        if (r.length() > 400) {
            r = r.substring(0, 400) + "…";
        }
        List<String> bullets = new ArrayList<>();
        bullets.add("模型返回无法解析为 JSON，已使用降级大纲。");
        bullets.add("建议：系统配置使用 deepseek-chat、大纲 max_tokens 充足；或在系统配置页「恢复默认」后重试。");
        if (!c.isBlank()) {
            bullets.add("主题句：" + c);
        }
        if (!r.isBlank()) {
            bullets.add("检索节选：" + r);
        }
        slide.setContent(bullets.toArray(new String[0]));
        slide.setNotes("请检查 DEEPSEEK_API_KEY 与网络；也可稍后重试。");
        response.getSlides().add(slide);
        return response;
    }
}
