package com.example.pptbackend.service;

import com.example.pptbackend.dto.EvaluationReportResponse;
import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.GenerateSlidesRequest;
import com.example.pptbackend.dto.RegenerateSlideFromSourcesRequest;
import com.example.pptbackend.dto.IndexSearchResult;
import com.example.pptbackend.dto.SearchRequest;
import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.dto.SlideContentResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import com.example.pptbackend.repository.SlideRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SlideGenerationService {

    private enum CorrectionTier {
        NONE("", 0),
        TIER1(
            "\n\n【一级修订】要点不足或引用偏弱：请至少输出 3 条要点，每条补充可追溯 sources（检索为空写常识归纳）。",
            2),
        TIER2(
            "\n\n【二级修订】整体质量仍不达标：请显著提高信息密度与引用覆盖，要点表述尽量对齐上方检索片段。",
            6);

        final String suffix;
        final int ragBoost;

        CorrectionTier(String suffix, int ragBoost) {
            this.suffix = suffix;
            this.ragBoost = ragBoost;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SlideGenerationService.class);

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 与默认 slidePromptTemplate 配合；避免与模板中的叙事/禁止项重复，仅做 JSON 与检索提醒。 */
    private static final String SLIDE_QUALITY_RULES = """

【系统提醒】下方若已含「检索上下文」，事实与数字须优先来自该段；无检索命中时在 content 中标注 [待核实]，勿编造 URL。
【去重】须阅读「前面各页已生成的要点摘要」：禁止把已在前面出现过的公司/产品/大学案例再完整讲一遍；若必须呼应，用一句承接并给出**新增**信息。
【引用 sources】至少 1 条；须从「检索上下文」中抄写真实链接或文献标题，格式示例："标题 | https://..." 或 "https://..."。禁止 example.com、禁止占位假链、禁止输出未解析的 JSON 字符串；不要 notes 字段。
【格式】严格返回合法 JSON：{"content":["…"],"sources":["…"]}
""";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final DeepseekChatClient deepseekChatClient;
    private final IndexSegmentService indexSegmentService;
    private final EmbeddingService embeddingService;
    private final SlideRepository slideRepository;
    private final ProjectRepository projectRepository;
    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;
    private final SlideSourceCitationService slideSourceCitationService;
    private final PptDisplayExtractionService pptDisplayExtractionService;
    private final ProjectAccessService projectAccessService;
    private final EvaluationReportService evaluationReportService;
    private final TransactionTemplate transactionTemplate;

    private record RagContextBundle(String promptBlock, List<String> citationLines) {}

    private final int slideMaxTokens;
    private final boolean slideFallbackTavily;
    private final int slideFallbackLimit;
    private final boolean selfCorrectionEnabled;
    private final double tier1AutoBelow;
    private final double tier1FactBelow;
    private final double tier2AutoBelow;
    private final double tier2FactBelow;
    /** 正文专用模型；为 inherit 时沿用系统配置的 llmModel */
    private final String slideGenerationModel;
    /** 与 system_config.retrieval_limit 取较小值，控制单页向量命中条数 */
    private final int slideRetrievalCap;
    /** Tavily 兜底摘要写入 prompt 的最大字符数 */
    private final int slideFallbackSnippetMaxChars;

    public SlideGenerationService(ObjectMapper objectMapper,
                                  SystemConfigService systemConfigService,
                                  DeepseekChatClient deepseekChatClient,
                                  IndexSegmentService indexSegmentService,
                                  EmbeddingService embeddingService,
                                  SlideRepository slideRepository,
                                  ProjectRepository projectRepository,
                                  ExternalKnowledgeSourceService externalKnowledgeSourceService,
                                  SlideSourceCitationService slideSourceCitationService,
                                  PptDisplayExtractionService pptDisplayExtractionService,
                                  ProjectAccessService projectAccessService,
                                  EvaluationReportService evaluationReportService,
                                  TransactionTemplate transactionTemplate,
                                  @Value("${generation.slide-max-tokens:1536}") int slideMaxTokens,
                                  @Value("${generation.slide-fallback-tavily:true}") boolean slideFallbackTavily,
                                  @Value("${generation.slide-fallback-result-limit:1}") int slideFallbackLimit,
                                  @Value("${generation.self-correction-enabled:false}") boolean selfCorrectionEnabled,
                                  @Value("${generation.self-correction-tier1-auto-below:76}") double tier1AutoBelow,
                                  @Value("${generation.self-correction-tier1-fact-below:0.58}") double tier1FactBelow,
                                  @Value("${generation.self-correction-tier2-auto-below:70}") double tier2AutoBelow,
                                  @Value("${generation.self-correction-tier2-fact-below:0.48}") double tier2FactBelow,
                                  @Value("${generation.slide-model:deepseek-chat}") String slideGenerationModel,
                                  @Value("${generation.slide-retrieval-cap:3}") int slideRetrievalCap,
                                  @Value("${generation.slide-fallback-snippet-chars:2400}") int slideFallbackSnippetMaxChars) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.deepseekChatClient = deepseekChatClient;
        this.indexSegmentService = indexSegmentService;
        this.embeddingService = embeddingService;
        this.slideRepository = slideRepository;
        this.projectRepository = projectRepository;
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
        this.slideSourceCitationService = slideSourceCitationService;
        this.pptDisplayExtractionService = pptDisplayExtractionService;
        this.projectAccessService = projectAccessService;
        this.evaluationReportService = evaluationReportService;
        this.transactionTemplate = transactionTemplate;
        this.slideMaxTokens = Math.max(512, slideMaxTokens);
        this.slideFallbackTavily = slideFallbackTavily;
        this.slideFallbackLimit = Math.max(1, slideFallbackLimit);
        this.selfCorrectionEnabled = selfCorrectionEnabled;
        this.tier1AutoBelow = tier1AutoBelow;
        this.tier1FactBelow = tier1FactBelow;
        this.tier2AutoBelow = tier2AutoBelow;
        this.tier2FactBelow = tier2FactBelow;
        this.slideGenerationModel = slideGenerationModel != null ? slideGenerationModel.trim() : "";
        this.slideRetrievalCap = Math.min(15, Math.max(1, slideRetrievalCap));
        this.slideFallbackSnippetMaxChars = Math.max(400, slideFallbackSnippetMaxChars);
    }

    @FunctionalInterface
    public interface SlideGenerationProgressListener {
        void onProgress(int completedSlides, int totalSlides);
    }

    @Transactional
    public SlideContentResponse regenerateSlide(Long projectId, Long slideId, String inputType, String inputContent) {
        return doRegenerateSlide(projectId, slideId, inputType, inputContent, CorrectionTier.NONE, "");
    }

    private void regenerateSlideInNewTransaction(Long projectId, Long slideId, String inputType, String inputContent,
                                               CorrectionTier tier, String priorSlidesDigest) {
        transactionTemplate.executeWithoutResult(status ->
            doRegenerateSlide(projectId, slideId, inputType, inputContent, tier, priorSlidesDigest));
    }

    private SlideContentResponse doRegenerateSlide(Long projectId, Long slideId, String inputType, String inputContent,
                                                 CorrectionTier tier, String priorSlidesDigest) {
        Slide slide = slideRepository.findByIdAndProject_Id(slideId, projectId)
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));

        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            log.debug("Skipping LLM for structural slide project={} slide={} title={}",
                projectId, slideId, slide.getTitle());
            return preserveOutlineBullets(slide);
        }

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        int durationMinutes = PresentationDurationPlanner.clampMinutes(project.getPresentationDurationMinutes());

        SystemConfigDto config = systemConfigService.getSystemConfig();
        int topK = Math.min(Math.max(1, config.getRetrievalLimit()), slideRetrievalCap);
        RagContextBundle rag = buildRagContextBundle(projectId, slide.getTitle(), topK, tier);
        String ragContext = rag.promptBlock();

        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }
        List<Slide> ordered = loadOrderedSlides(projectId);
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (slide.getId().equals(ordered.get(i).getId())) {
                idx = i;
                break;
            }
        }
        String prevSlideTitle = idx <= 0 ? "无" : nzTitle(ordered.get(idx - 1).getTitle());
        String nextSlideTitle = idx < 0 || idx >= ordered.size() - 1 ? "无" : nzTitle(ordered.get(idx + 1).getTitle());
        String chapterLabel = slide.getChapter() != null && !slide.getChapter().isBlank()
            ? slide.getChapter().trim()
            : "（未标注章节）";

        String retrievedContextBlock = ragContext.isBlank()
            ? "（暂无向量命中或 Tavily 摘要；请基于主题与常识撰写，事实标 [待核实]，勿伪造链接。）"
            : (ragContext.startsWith("【当前页外部简要依据】")
                ? ragContext
                : "【向量检索 ILF-2 / 须优先对齐的事实片段】\n" + ragContext);
        if (!rag.citationLines().isEmpty()) {
            retrievedContextBlock = retrievedContextBlock
                + "\n\n【引用候选（请优先写入 sources，可择 1–3 条）】\n"
                + String.join("\n", rag.citationLines());
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("slideTitle", slide.getTitle() != null ? slide.getTitle() : "");
        vars.put("chapter", chapterLabel);
        vars.put("prevSlideTitle", prevSlideTitle);
        vars.put("nextSlideTitle", nextSlideTitle);
        vars.put("inputType", "topic".equalsIgnoreCase(inputType) ? "主题" : "文档");
        vars.put("inputContent", inputContent != null ? inputContent : "");
        String digestLine = priorSlidesDigest != null && !priorSlidesDigest.isBlank()
            ? priorSlidesDigest
            : "（尚无：封面/目录首页生成时可为空。）";
        vars.put("prior_slides_digest", digestLine);
        vars.put("retrieved_context", retrievedContextBlock);

        String template = config.getSlidePromptTemplate();
        String basePrompt = formatPrompt(template, vars);
        boolean digestInTemplate = template != null && template.contains("{prior_slides_digest}");
        String digestSection = digestInTemplate
            ? ""
            : ("\n## 前面各页已生成的要点摘要（去重用）\n" + digestLine + "\n");
        String durationBlock = PresentationDurationPlanner.slideGuidanceBlock(durationMinutes);
        String prompt = basePrompt + digestSection + SLIDE_QUALITY_RULES + "\n\n" + durationBlock + tier.suffix;

        String body = buildSlideRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(120));
        SlideContentResponse parsed = parseSlideResponse(responseText);
        parsed.setNotes("");
        parsed.setSources(slideSourceCitationService.mergeAndSanitize(parsed.getSources(), rag.citationLines()));

        slide.setBullets(parsed.getContent() != null ? parsed.getContent() : new ArrayList<>());
        slide.setPptBullets(new ArrayList<>());
        slide.setNotes(null);
        if (parsed.getSources() != null && !parsed.getSources().isEmpty()) {
            slide.setSources(parsed.getSources());
        }
        slideRepository.save(slide);

        return parsed;
    }

    /**
     * 依据用户已确认的引用来源，重新撰写本页讲稿要点并提炼 PPT 投影句；不覆盖用户 sources。
     */
    @Transactional
    public SlideContentResponse regenerateSlideFromSources(Long projectId,
                                                           Long slideId,
                                                           RegenerateSlideFromSourcesRequest request) {
        Slide slide = slideRepository.findByIdAndProject_Id(slideId, projectId)
            .orElseThrow(() -> new EntityNotFoundException("Slide not found: " + slideId));
        Project project = projectAccessService.requireReadableProject(projectId);
        projectAccessService.assertWritable(project);

        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            throw new IllegalArgumentException("封面、目录、Q&A 等页面无需按引用重生");
        }

        List<String> userSources = resolveUserSourcesForRegeneration(request, slide);
        if (userSources.isEmpty()) {
            throw new IllegalArgumentException("请先补充至少一条有效引用来源（含链接或 type=index 等）");
        }

        String title = request != null && request.getTitle() != null && !request.getTitle().isBlank()
            ? request.getTitle().trim()
            : (slide.getTitle() != null ? slide.getTitle().trim() : "");
        if (title.isBlank()) {
            throw new IllegalArgumentException("页面标题不能为空");
        }
        slide.setTitle(title);

        List<Slide> ordered = loadOrderedSlides(projectId);
        int idx = indexOfSlide(ordered, slide.getId());
        String prevSlideTitle = idx <= 0 ? "无" : nzTitle(ordered.get(idx - 1).getTitle());
        String nextSlideTitle = idx < 0 || idx >= ordered.size() - 1 ? "无" : nzTitle(ordered.get(idx + 1).getTitle());
        String priorDigest = buildPriorSlidesDigest(ordered, idx);
        String prevBulletsBlock = buildAdjacentBulletsBlock(ordered, idx - 1);
        String nextBulletsBlock = buildAdjacentBulletsBlock(ordered, idx + 1);

        String theme = request != null && request.getInputContent() != null && !request.getInputContent().isBlank()
            ? request.getInputContent().trim()
            : (project.getTheme() != null ? project.getTheme().trim() : project.getTitle());
        String chapter = slide.getChapter() != null && !slide.getChapter().isBlank()
            ? slide.getChapter().trim()
            : "（无）";
        String factMaterial = sourcesAsFactMaterial(userSources);
        String previousBlock = buildPreviousContentBlock(request, slide);

        int durationMinutes = PresentationDurationPlanner.clampMinutes(project.getPresentationDurationMinutes());

        String prompt = """
            你是演示稿撰稿人。本页 content 是**演讲者口头讲的要点**，听众看不到引用列表。
            下方「事实素材」仅用于核对数字与结论；须**内化**为自然表述，禁止写成文献综述或引用格式。

            ## 全稿主题
            %s

            ## 本页
            标题：%s
            章节：%s
            演讲时长约束：约 %d 分钟全稿

            ## 页间上下文
            上一页标题：%s
            下一页标题：%s

            ## 前面各页要点摘要（保持连贯，勿重复已讲过的长案例）
            %s

            ## 上一页要点（首条可轻量承接，勿写「承接上一页」等元话术）
            %s

            ## 下一页方向（末条可自然过渡，勿写「下一页将讲」）
            %s

            %s

            ## 事实素材（内化后写入 content；禁止在 content 中出现书名、章节、URL、来源编号）
            %s

            ## 写作规范（必须遵守）
            1. 输出 3–5 条 content：口语化、先结论后证据，像演讲者在陈述，不是论文摘要。
            2. **禁止**在 content 中出现：根据《…》、该来源/报告指出、来源强调、权威来源、external-…、type=、URL、章节号、「如上所述」「综上所述」。
            3. 数字与机构名须来自事实素材，不得编造；无数字时可写定性结论。
            4. 与全稿主题及前后页逻辑一致；勿引入与主题无关的新话题。
            5. 只输出 JSON：{"content":["…","…"]}
            """.formatted(
            theme,
            title,
            chapter,
            durationMinutes,
            prevSlideTitle,
            nextSlideTitle,
            priorDigest,
            prevBulletsBlock,
            nextBulletsBlock,
            previousBlock,
            factMaterial);

        SystemConfigDto config = systemConfigService.getSystemConfig();
        String body = buildSlideRequestBody(prompt, config);
        String responseText = deepseekChatClient.chatCompletions(body, Duration.ofSeconds(120));
        SlideContentResponse parsed = parseSlideResponse(responseText);
        List<String> content = polishPresentationBullets(parsed.getContent());
        if (content.isEmpty()) {
            throw new IllegalStateException("模型未返回有效要点，请检查引用是否与本页主题相关后重试");
        }

        slide.setBullets(content);
        slide.setSources(slideSourceCitationService.formatSourceLinesForStorage(userSources));
        slide.setNotes(null);

        List<String> pptBullets = pptDisplayExtractionService.extractForSlide(slide, durationMinutes);
        slide.setPptBullets(pptBullets);
        slideRepository.save(slide);

        SlideContentResponse out = new SlideContentResponse();
        out.setContent(content);
        out.setSources(slideSourceCitationService.formatSourceLinesForStorage(userSources));
        out.setPptBullets(pptBullets);
        out.setNotes("");
        log.info("Regenerated slide {} from {} user sources for project {}", slideId, userSources.size(), projectId);
        return out;
    }

    private List<String> resolveUserSourcesForRegeneration(RegenerateSlideFromSourcesRequest request, Slide slide) {
        List<String> raw = request != null && request.getSources() != null && !request.getSources().isEmpty()
            ? request.getSources()
            : slide.getSources();
        if (raw == null) {
            return List.of();
        }
        List<String> normalized = slideSourceCitationService.formatSourceLinesForStorage(raw);
        return normalized.stream()
            .filter(s -> s != null && !s.isBlank())
            .filter(s -> !s.contains("未命中可核验的外部链接"))
            .filter(s -> !s.contains("手动补充出处"))
            .toList();
    }

    private static List<String> polishPresentationBullets(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
            .map(SlideGenerationService::stripVerificationMarks)
            .map(SlideGenerationService::stripMetaCitationPhrasing)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toList());
    }

    /** 去掉讲稿中的文献式、来源式表述（引用只留在 sources 字段） */
    private static String stripMetaCitationPhrasing(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String s = line.trim();
        s = s.replaceAll("(?i)\\(external-[\\w-]+\\)", "");
        s = s.replaceAll("（external-[\\w-]+）", "");
        s = s.replaceAll("根据《[^》]{1,80}》[^，。；]*[，,]?", "");
        s = s.replaceAll("《[^》]{1,80}》[^，。；]*[，,]?", "");
        s = s.replaceAll("(该来源|来源|上述来源|权威来源|参考资料|文献)(指出|表明|强调|显示|称|认为)[，,]?", "");
        s = s.replaceAll("(报告指出|研究认为|有研究指出)[，,]?", "");
        s = s.replaceAll("type=(tavily|mediawiki|index|llm_inference)[^，。；\\s]*", "");
        s = s.replaceAll("https?://\\S+", "");
        s = s.replaceAll("\\s{2,}", " ").trim();
        if (s.startsWith("，") || s.startsWith(",")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    private static int indexOfSlide(List<Slide> ordered, Long slideId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (slideId.equals(ordered.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private String buildPriorSlidesDigest(List<Slide> ordered, int currentIdx) {
        if (currentIdx <= 0) {
            return "（尚无：本页为前几页。）";
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < currentIdx; i++) {
            Slide s = ordered.get(i);
            List<String> bullets = s.getBullets();
            if (bullets == null || bullets.isEmpty()) {
                continue;
            }
            String joined = bullets.stream()
                .map(SlideGenerationService::stripMetaCitationPhrasing)
                .filter(b -> !b.isBlank())
                .collect(Collectors.joining("；"));
            if (!joined.isBlank()) {
                chunks.add("【" + nzTitle(s.getTitle()) + "】" + joined);
            }
        }
        if (chunks.isEmpty()) {
            return "（前面各页尚无讲稿要点。）";
        }
        String digest = String.join("\n", chunks);
        if (digest.length() > 2800) {
            digest = digest.substring(digest.length() - 2800);
        }
        return digest;
    }

    private String buildAdjacentBulletsBlock(List<Slide> ordered, int idx) {
        if (idx < 0 || idx >= ordered.size()) {
            return "（无）";
        }
        Slide s = ordered.get(idx);
        List<String> bullets = s.getBullets();
        if (bullets == null || bullets.isEmpty()) {
            return "（无要点）";
        }
        return bullets.stream()
            .map(b -> "- " + stripMetaCitationPhrasing(b))
            .filter(b -> b.length() > 2)
            .collect(Collectors.joining("\n"));
    }

    private String buildPreviousContentBlock(RegenerateSlideFromSourcesRequest request, Slide slide) {
        List<String> prev = request != null && request.getPreviousContent() != null && !request.getPreviousContent().isEmpty()
            ? request.getPreviousContent()
            : slide.getBullets();
        if (prev == null || prev.isEmpty()) {
            return "";
        }
        String block = prev.stream()
            .map(b -> "- " + b)
            .collect(Collectors.joining("\n"));
        return "## 本页现有要点（可改写优化，勿保留文献式引用口吻）\n" + block + "\n";
    }

    /** 将引用行转为无书目腔的事实素材，供模型内化 */
    private String sourcesAsFactMaterial(List<String> userSources) {
        List<String> lines = new ArrayList<>();
        int i = 1;
        for (String raw : userSources) {
            String fact = extractFactFromSourceLine(raw, i++);
            if (!fact.isBlank()) {
                lines.add("- " + fact);
            }
        }
        return lines.isEmpty() ? "（无）" : String.join("\n", lines);
    }

    private static String extractFactFromSourceLine(String line, int index) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String t = line.trim();
        int excerptIdx = t.indexOf("节选：");
        if (excerptIdx >= 0) {
            String excerpt = t.substring(excerptIdx + 3).replaceAll("\\|\\s*type=.*$", "").trim();
            return excerpt.replaceAll("^：", "").trim();
        }
        if (t.contains(" | ")) {
            String[] parts = t.split("\\|");
            for (String part : parts) {
                String p = part.trim();
                if (p.startsWith("http") || p.startsWith("type=") || p.matches("(?i)external-\\d+.*")) {
                    continue;
                }
                if (p.length() > 12 && !p.contains("://")) {
                    return p;
                }
            }
            String cleaned = t.replaceAll("\\|\\s*type=.*$", "").replaceAll("https?://\\S+", "").trim();
            if (cleaned.length() > 20) {
                return cleaned;
            }
        }
        return "素材" + index + "：" + t.replaceAll("\\|\\s*type=.*$", "").replaceAll("https?://\\S+", "").trim();
    }

    private static String stripVerificationMarks(String line) {
        if (line == null) {
            return "";
        }
        return line
            .replace("[待核实]", "")
            .replace("【待核实】", "")
            .replace("[待补充权威来源]", "")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    public void generateAllSlides(Long projectId, GenerateSlidesRequest request) {
        generateAllSlides(projectId, request, null);
    }

    public void generateAllSlides(Long projectId,
                                GenerateSlidesRequest request,
                                SlideGenerationProgressListener progress) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found: " + projectId);
        }

        String inputType = request.getInputType() != null ? request.getInputType() : "topic";
        String inputContent = request.getInputContent() != null ? request.getInputContent() : "";

        List<Slide> slides = loadOrderedSlides(projectId);
        int total = slides.size();

        List<String> priorChunks = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            Slide slide = slides.get(i);
            String digest = String.join("\n", priorChunks);
            if (digest.length() > 3600) {
                digest = digest.substring(digest.length() - 3600);
            }
            final String digestFinal = digest;
            regenerateSlideInNewTransaction(projectId, slide.getId(), inputType, inputContent, CorrectionTier.NONE, digestFinal);
            Slide saved = slideRepository.findByIdAndProject_Id(slide.getId(), projectId).orElse(slide);
            if (saved.getBullets() != null && !saved.getBullets().isEmpty()) {
                priorChunks.add("【" + nzTitle(saved.getTitle()) + "】" + String.join("；", saved.getBullets()));
            }
            if (progress != null) {
                progress.onProgress(i + 1, total);
            }
        }

        EvaluationReportResponse eval;
        try {
            eval = transactionTemplate.execute(status ->
                evaluationReportService.createAutoEvaluationReportAndReturn(projectId));
        } catch (Exception e) {
            log.warn("Auto evaluation after slide generation failed: {}", e.getMessage());
            return;
        }

        if (!selfCorrectionEnabled) {
            return;
        }

        if (!belowThreshold(eval, tier1AutoBelow, tier1FactBelow)) {
            return;
        }

        log.info(
            "Self-correction tier1 (auto={}, fact={})",
            eval.getAutoTotalScore(),
            eval.getFactVerificationRate());

        List<Slide> refreshed = loadOrderedSlides(projectId);
        boolean anyWeak = false;
        for (Slide slide : refreshed) {
            if (needsWeakSlideRegeneration(slide)) {
                regenerateSlideInNewTransaction(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER1, "");
                anyWeak = true;
            }
        }
        if (!anyWeak) {
            for (Slide slide : refreshed) {
                if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
                    continue;
                }
                regenerateSlideInNewTransaction(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER1, "");
            }
        }

        try {
            eval = transactionTemplate.execute(status ->
                evaluationReportService.createAutoEvaluationReportAndReturn(projectId));
        } catch (Exception e) {
            log.warn("Re-eval after tier1 failed: {}", e.getMessage());
            return;
        }

        if (!belowThreshold(eval, tier2AutoBelow, tier2FactBelow)) {
            return;
        }

        log.info(
            "Self-correction tier2 full pass (auto={}, fact={})",
            eval.getAutoTotalScore(),
            eval.getFactVerificationRate());
        refreshed = loadOrderedSlides(projectId);
        for (Slide slide : refreshed) {
            if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
                continue;
            }
            regenerateSlideInNewTransaction(projectId, slide.getId(), inputType, inputContent, CorrectionTier.TIER2, "");
        }
        try {
            transactionTemplate.executeWithoutResult(status ->
                evaluationReportService.createAutoEvaluationReport(projectId));
        } catch (Exception e) {
            log.warn("Auto evaluation after tier2 failed: {}", e.getMessage());
        }
    }

    private static String nzTitle(String title) {
        return title != null ? title : "";
    }

    private List<Slide> loadOrderedSlides(Long projectId) {
        return slideRepository.findByProject_IdOrderByPositionAsc(projectId);
    }

    private static boolean needsWeakSlideRegeneration(Slide slide) {
        if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
            return false;
        }
        List<String> bullets = slide.getBullets();
        int n = bullets != null ? bullets.size() : 0;
        boolean noSources = slide.getSources() == null || slide.getSources().isEmpty();
        return n < 3 || noSources;
    }

    /**
     * 封面 / 目录 / 问答讨论等页保留大纲要点，不调用大模型扩写。
     */
    private SlideContentResponse preserveOutlineBullets(Slide slide) {
        List<String> bullets = slide.getBullets();
        if (bullets == null || bullets.isEmpty()) {
            bullets = defaultStructuralBullets(slide);
        } else {
            bullets = bullets.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
            if (bullets.isEmpty()) {
                bullets = defaultStructuralBullets(slide);
            }
        }
        slide.setBullets(bullets);
        slide.setPptBullets(StructuralSlideDetector.pptBulletsForStructural(
            slide.getTitle(), slide.getChapter(), bullets));
        slide.setNotes(null);
        slide.setSources(new ArrayList<>());
        slideRepository.save(slide);

        SlideContentResponse response = new SlideContentResponse();
        response.setContent(new ArrayList<>(bullets));
        response.setNotes("");
        response.setSources(new ArrayList<>());
        return response;
    }

    private static List<String> defaultStructuralBullets(Slide slide) {
        String title = slide.getTitle() != null ? slide.getTitle().trim() : "";
        String chapter = slide.getChapter();
        if (StructuralSlideDetector.isCover(title, chapter)) {
            return new ArrayList<>(List.of(
                "副标题：（可选）",
                "汇报信息：单位 / 姓名 / 日期"));
        }
        if (StructuralSlideDetector.isTableOfContents(title, chapter)) {
            return new ArrayList<>(List.of(
                "1）请在大纲中维护各章名称",
                "2）目录页仅列出章节路线，正文页再展开细节"));
        }
        if (StructuralSlideDetector.isQaOrDiscussion(title, chapter)) {
            return new ArrayList<>(List.of(
                "预留 3–5 分钟回答听众提问",
                "可提前准备 2 个高频问题及口头回应要点",
                "互动收尾与后续资料说明（讲稿用，不逐条打在幻灯片上）"));
        }
        return new ArrayList<>(List.of("（骨架页：请在大纲或本页编辑中补充简短要点）"));
    }

    private static boolean belowThreshold(EvaluationReportResponse eval,
                                          double autoBelow,
                                          double factBelow) {
        double auto = eval.getAutoTotalScore() != null ? eval.getAutoTotalScore() : 100;
        if (auto < autoBelow) {
            return true;
        }
        Double fact = eval.getFactVerificationRate();
        return fact != null && fact < factBelow;
    }

    private RagContextBundle buildRagContextBundle(Long projectId, String slideTitle, int topK, CorrectionTier tier) {
        int base = topK > 0 ? topK : 5;
        int effective = Math.min(15, Math.max(1, base + tier.ragBoost));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setProjectId(projectId);
        searchRequest.setQueryEmbedding(embeddingService.embed(slideTitle));
        searchRequest.setTopK(effective);
        SearchResponse response = indexSegmentService.search(searchRequest);

        List<String> citationLines = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            citationLines.addAll(slideSourceCitationService.linesFromIndexResults(response.getResults(), 3));
            int index = 1;
            for (IndexSearchResult result : response.getResults()) {
                builder.append(index++)
                    .append(". ")
                    .append(result.getContent() != null ? result.getContent().trim() : "")
                    .append(" (distance=")
                    .append(String.format("%.4f", result.getDistance()))
                    .append(")\n");
            }
        }
        if (!builder.isEmpty()) {
            return new RagContextBundle(builder.toString().trim(), citationLines);
        }
        if (slideFallbackTavily && slideTitle != null && !slideTitle.isBlank()) {
            List<ExternalSourceDocument> docs =
                externalKnowledgeSourceService.searchExternalSources(slideTitle, Math.max(slideFallbackLimit, 3));
            if (!docs.isEmpty()) {
                citationLines.addAll(slideSourceCitationService.linesFromExternalDocuments(docs, 3));
                return new RagContextBundle(
                    externalKnowledgeSourceService.formatDocumentsForSlidePrompt(docs, slideFallbackSnippetMaxChars),
                    citationLines);
            }
        }
        return new RagContextBundle("", citationLines);
    }

    private String formatPrompt(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prompt;
    }

    /**
     * 正文阶段默认用 {@code generation.slide-model}（如 deepseek-chat），避免与大纲共用 reasoner 拖慢；
     * 配置为 {@code inherit} 时沿用系统配置中的 llmModel。
     */
    private String resolveSlideModel(SystemConfigDto config) {
        if (slideGenerationModel != null && !slideGenerationModel.isBlank()
            && !"inherit".equalsIgnoreCase(slideGenerationModel)) {
            return slideGenerationModel;
        }
        String fromConfig = config.getLlmModel();
        return fromConfig != null && !fromConfig.isBlank() ? fromConfig : "deepseek-chat";
    }

    private String buildSlideRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", resolveSlideModel(config));
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", slideMaxTokens);
        payload.put("top_p", config.getTopP());
        payload.put("top_k", config.getTopK());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize generation request", e);
        }
    }

    private SlideContentResponse parseSlideResponse(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                return degradedSlide("模型未返回 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                return degradedSlide("模型未返回 message");
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                return degradedSlide("模型返回空内容");
            }

            String jsonPayload = stripFence(content.trim());
            try {
                Map<String, Object> payload = objectMapper.readValue(jsonPayload, new TypeReference<>() {});
                return mapPayloadToSlideResponse(payload);
            } catch (IOException e) {
                return degradedSlide(content);
            }
        } catch (IOException e) {
            return degradedSlide(responseText);
        }
    }

    private String stripFence(String content) {
        Matcher m = JSON_FENCE.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return content;
    }

    private SlideContentResponse mapPayloadToSlideResponse(Map<String, Object> payload) {
        SlideContentResponse response = new SlideContentResponse();
        Object contentValue = payload.get("content");
        if (contentValue instanceof List<?> list) {
            response.setContent(list.stream().map(Object::toString).collect(Collectors.toList()));
        } else {
            response.setContent(new ArrayList<>());
        }
        response.setNotes("");
        Object sourcesValue = payload.get("sources");
        if (sourcesValue instanceof List<?> list) {
            response.setSources(list.stream()
                .map(SlideGenerationService::formatSourceEntryRaw)
                .map(slideSourceCitationService::normalizeSourceLine)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList()));
        }
        return response;
    }

    /**
     * 支持字符串来源行，或 {"title","url","type"} 对象（与默认 Prompt 示例一致）。
     */
    private static String formatSourceEntryRaw(Object o) {
        if (o instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            Object title = typed.get("title");
            Object url = typed.get("url");
            Object type = typed.get("type");
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.toString().isBlank()) {
                sb.append(title.toString().trim());
            }
            if (url != null && !url.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(url.toString().trim());
            }
            if (type != null && !type.toString().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | type=");
                } else {
                    sb.append("type=");
                }
                sb.append(type.toString().trim());
            }
            return sb.length() > 0 ? sb.toString() : String.valueOf(o);
        }
        return o != null ? o.toString() : "";
    }

    private SlideContentResponse degradedSlide(String raw) {
        SlideContentResponse response = new SlideContentResponse();
        String line = raw != null ? raw.replace("\n", " ").trim() : "";
        if (line.length() > 400) {
            line = line.substring(0, 400) + "…";
        }
        response.setContent(new ArrayList<>(List.of(
            "（结构化解析失败，以下为降级展示的原始摘要）",
            line.isEmpty() ? "无可用文本" : line
        )));
        response.setNotes("");
        response.setSources(new ArrayList<>(List.of("内部降级输出")));
        return response;
    }
}
