package com.example.pptbackend.service;

import com.example.pptbackend.dto.FactCheckDetailDto;
import com.example.pptbackend.dto.IndexSearchResult;
import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FactConsistencyService {

    private static final int SAMPLE_CAP = 5;
    private static final int SEARCH_TOP_K = 12;
    private static final int LEXICAL_SCAN_CAP = 300;
    private static final double SEMANTIC_PASS_THRESHOLD = 0.55;
    private static final double SEMANTIC_MIN_SCORE = 0.32;
    private static final double OVERLAP_PASS_THRESHOLD = 0.22;
    /** 骨架页单页评估：不参与事实率汇总 */
    private static final double FACT_RATE_NOT_APPLICABLE = -1;

    private static final Pattern QUESTION_BULLET = Pattern.compile(
        "^(如何|为什么|为何|怎样|怎么|是否|能否|可不可以|哪些|什么|谁|多少|哪(?:些|里|个)).+[？?]?$");

    private final IndexSegmentService indexSegmentService;
    private final SiliconFlowEmbeddingClient siliconFlowEmbeddingClient;

    public FactConsistencyService(IndexSegmentService indexSegmentService,
                                    SiliconFlowEmbeddingClient siliconFlowEmbeddingClient) {
        this.indexSegmentService = indexSegmentService;
        this.siliconFlowEmbeddingClient = siliconFlowEmbeddingClient;
    }

    public double computeVerificationRate(Long projectId, Project project) {
        return computeVerificationDetails(projectId, project, null).rate();
    }

    public FactVerificationResult computeVerificationDetails(Long projectId, Project project, Long slideId) {
        Optional<FactVerificationResult> structuralSkip = tryStructuralSkip(project, slideId);
        if (structuralSkip.isPresent()) {
            return structuralSkip.get();
        }
        if (siliconFlowEmbeddingClient.isAvailable()) {
            try {
                return computeSemanticEvidenceDetails(projectId, project, slideId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return computeOverlapDetailsLegacy(projectId, project, slideId);
    }

    private FactVerificationResult computeSemanticEvidenceDetails(Long projectId, Project project, Long slideId) {
        List<BulletSample> samples = collectBulletSamples(project, slideId);
        samples.removeIf(b -> b.text() == null || b.text().trim().length() < 10);
        if (samples.isEmpty()) {
            return new FactVerificationResult(0, List.of());
        }
        Collections.shuffle(samples, new Random(projectId != null ? projectId : 42L));
        int take = Math.min(SAMPLE_CAP, samples.size());
        List<FactCheckDetailDto> details = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < take; i++) {
            BulletSample sample = samples.get(i);
            String bullet = sample.text();
            String query = buildFactCheckQuery(sample);
            SearchResponse response = indexSegmentService.searchByText(projectId, query, SEARCH_TOP_K);
            EvidencePick pick = resolveEvidence(projectId, project, sample, bullet, response);
            double best = pick.overlap();
            String bestEvidence = pick.evidence();
            if ("semantic".equals(pick.method()) && bestEvidence != null && siliconFlowEmbeddingClient.isAvailable()) {
                try {
                    List<Float> stmtVec = siliconFlowEmbeddingClient.embedRaw(bullet);
                    List<Float> evVec = siliconFlowEmbeddingClient.embedRaw(bestEvidence);
                    best = cosineSimilarity(stmtVec, evVec);
                } catch (Exception ignored) {
                    // keep lexical overlap score
                }
            }
            details.add(buildDetailFromEvidence(sample, bullet, bestEvidence, best,
                pick.method() != null ? pick.method() : "overlap", pick.note()));
            sum += details.get(details.size() - 1).getRateContribution() != null
                ? details.get(details.size() - 1).getRateContribution() : 0;
        }
        return new FactVerificationResult(sum / take, details);
    }

    private FactVerificationResult computeOverlapDetailsLegacy(Long projectId, Project project, Long slideId) {
        List<BulletSample> samples = collectBulletSamples(project, slideId);
        samples.removeIf(b -> b.text() == null || b.text().trim().length() < 10);
        if (samples.isEmpty()) {
            return new FactVerificationResult(0, List.of());
        }
        Collections.shuffle(samples, new Random(projectId != null ? projectId : 42L));
        int take = Math.min(SAMPLE_CAP, samples.size());
        List<FactCheckDetailDto> details = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < take; i++) {
            BulletSample sample = samples.get(i);
            String bullet = sample.text();
            String query = buildFactCheckQuery(sample);
            SearchResponse response = indexSegmentService.searchByText(projectId, query, SEARCH_TOP_K);
            EvidencePick pick = resolveEvidence(projectId, project, sample, bullet, response);
            FactCheckDetailDto detail = buildDetailFromEvidence(
                sample, bullet, pick.evidence(), pick.overlap(), "overlap", pick.note());
            details.add(detail);
            sum += detail.getRateContribution() != null ? detail.getRateContribution() : 0;
        }
        return new FactVerificationResult(sum / take, details);
    }

    private FactCheckDetailDto buildDetailFromEvidence(
        BulletSample sample,
        String bullet,
        String evidence,
        double rawScore,
        String method,
        String resolveNote) {
        IndexEvidenceQuality.SupportAssessment assessment = evidence != null && !evidence.isBlank()
            ? IndexEvidenceQuality.assessSupport(bullet, sample.slideTitle(), evidence)
            : new IndexEvidenceQuality.SupportAssessment(IndexEvidenceQuality.SupportLevel.IRRELEVANT,
                resolveNote != null ? resolveNote : IndexEvidenceQuality.noEvidenceMessage());

        double score = rawScore;
        boolean passed;
        String evidenceText;
        String note = null;
        IndexEvidenceQuality.SupportLevel level = assessment.level();

        switch (level) {
            case STRONG -> {
                passed = "semantic".equals(method)
                    ? score >= SEMANTIC_PASS_THRESHOLD && score >= SEMANTIC_MIN_SCORE
                    : score >= OVERLAP_PASS_THRESHOLD;
                evidenceText = truncate(evidence, 320);
            }
            case DOMAIN_ONLY -> {
                passed = false;
                score = "semantic".equals(method) ? Math.min(score, 0.45) : Math.min(score, OVERLAP_PASS_THRESHOLD - 0.01);
                evidenceText = truncate(evidence, 280);
                note = assessment.note();
            }
            default -> {
                passed = false;
                evidenceText = evidence != null && !evidence.isBlank()
                    ? truncate(evidence, 240)
                    : IndexEvidenceQuality.irrelevantEvidenceMessage();
                note = assessment.note() != null ? assessment.note() : IndexEvidenceQuality.noEvidenceMessage();
            }
        }

        if (evidence == null || evidence.isBlank()) {
            passed = false;
            score = 0;
            level = IndexEvidenceQuality.SupportLevel.IRRELEVANT;
            evidenceText = resolveNote != null && !resolveNote.isBlank()
                ? resolveNote
                : IndexEvidenceQuality.irrelevantEvidenceMessage();
            note = null;
        } else if (resolveNote != null && !resolveNote.isBlank() && note == null) {
            note = resolveNote;
        }

        double rateContrib = IndexEvidenceQuality.effectiveRateContribution(rawScore, level);

        FactCheckDetailDto detail = new FactCheckDetailDto();
        detail.setStatement(truncate(bullet, 280));
        detail.setEvidence(evidenceText);
        detail.setSupportScore(score);
        detail.setRateContribution(rateContrib);
        detail.setPassed(passed);
        detail.setMethod(method);
        detail.setSlideTitle(sample.slideTitle());
        detail.setEvidenceNote(note);
        return detail;
    }

    private static String buildFactCheckQuery(BulletSample sample) {
        StringBuilder sb = new StringBuilder();
        if (sample.slideTitle() != null && !sample.slideTitle().isBlank()) {
            sb.append(sample.slideTitle()).append(' ');
        }
        sb.append(sample.text());
        return truncate(sb.toString().trim(), 480);
    }

    static double cosineSimilarity(List<Float> a, List<Float> b) {
        int n = Math.min(a.size(), b.size());
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < n; i++) {
            float x = a.get(i);
            float y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private record BulletSample(String text, String slideTitle) {}

    private record EvidencePick(String evidence, double overlap, String method, String note) {
        EvidencePick(String evidence, double overlap) {
            this(evidence, overlap, "overlap", null);
        }
    }

    private EvidencePick resolveEvidence(
        Long projectId,
        Project project,
        BulletSample sample,
        String bullet,
        SearchResponse vectorResponse) {
        if (indexSegmentService.countByProjectId(projectId) <= 0) {
            EvidencePick fromSlide = pickFromSlideSources(project, sample, bullet);
            if (fromSlide.evidence() != null) {
                return fromSlide;
            }
            return new EvidencePick(null, 0, "overlap", IndexEvidenceQuality.emptyIndexMessage());
        }

        EvidencePick fromVector = pickBestOverlap(sample, bullet, vectorResponse != null ? vectorResponse.getResults() : null);
        if (fromVector.evidence() != null) {
            return new EvidencePick(fromVector.evidence(), fromVector.overlap(),
                siliconFlowEmbeddingClient.isAvailable() ? "semantic" : "overlap", null);
        }

        EvidencePick fromLexical = pickBestOverlap(
            sample, bullet, indexSegmentService.listByProjectId(projectId, LEXICAL_SCAN_CAP));
        if (fromLexical.evidence() != null) {
            return new EvidencePick(fromLexical.evidence(), fromLexical.overlap(), "overlap",
                "向量检索未命中，已通过全库词面扫描匹配");
        }

        EvidencePick fromSlide = pickFromSlideSources(project, sample, bullet);
        if (fromSlide.evidence() != null) {
            return fromSlide;
        }

        return new EvidencePick(null, 0, "overlap", IndexEvidenceQuality.noEvidenceMessage());
    }

    private EvidencePick pickBestOverlap(BulletSample sample, String bullet, List<IndexSearchResult> hits) {
        String queryText = buildFactCheckQuery(sample);
        String bestStrict = null;
        double bestStrictOverlap = 0;
        String bestRelaxed = null;
        double bestRelaxedOverlap = 0;
        if (hits == null || hits.isEmpty()) {
            return new EvidencePick(null, 0);
        }
        for (IndexSearchResult hit : hits) {
            String candidate = hit.getContent();
            if (candidate == null || candidate.isBlank() || IndexEvidenceQuality.isSeoSpamEvidence(candidate)) {
                continue;
            }
            double r = overlapRatio(queryText, candidate);
            if (IndexEvidenceQuality.passesEvidenceGate(bullet, sample.slideTitle(), candidate) && r > bestStrictOverlap) {
                bestStrictOverlap = r;
                bestStrict = candidate;
            }
            if (r > bestRelaxedOverlap) {
                bestRelaxedOverlap = r;
                bestRelaxed = candidate;
            }
        }
        if (bestStrict != null) {
            return new EvidencePick(bestStrict, bestStrictOverlap);
        }
        if (bestRelaxed != null && bestRelaxedOverlap >= 0.04) {
            return new EvidencePick(bestRelaxed, bestRelaxedOverlap);
        }
        return new EvidencePick(null, 0);
    }

    private EvidencePick pickFromSlideSources(Project project, BulletSample sample, String bullet) {
        if (project.getSlides() == null) {
            return new EvidencePick(null, 0);
        }
        String queryText = buildFactCheckQuery(sample);
        String best = null;
        double bestOverlap = 0;
        for (Slide slide : project.getSlides()) {
            if (sample.slideTitle() != null && slide.getTitle() != null
                && !sample.slideTitle().equals(slide.getTitle())) {
                continue;
            }
            if (slide.getSources() == null) {
                continue;
            }
            for (String sourceLine : slide.getSources()) {
                String candidate = extractEvidenceFromSourceLine(sourceLine);
                if (candidate == null || candidate.isBlank() || IndexEvidenceQuality.isSeoSpamEvidence(candidate)) {
                    continue;
                }
                double r = overlapRatio(queryText, candidate);
                if (r > bestOverlap) {
                    bestOverlap = r;
                    best = candidate;
                }
            }
        }
        if (best == null || bestOverlap < 0.04) {
            return new EvidencePick(null, 0);
        }
        return new EvidencePick(best, bestOverlap, "overlap", "来自本页引用行词面匹配");
    }

    private static String extractEvidenceFromSourceLine(String sourceLine) {
        if (sourceLine == null || sourceLine.isBlank()) {
            return null;
        }
        String t = sourceLine.trim();
        int excerptIdx = t.indexOf("节选：");
        if (excerptIdx >= 0) {
            return t.substring(excerptIdx + 3).replaceFirst("^\\s*", "");
        }
        int pipe = t.indexOf(" | ");
        if (pipe > 0 && pipe < 80) {
            return t.substring(0, pipe);
        }
        return t;
    }

    private List<BulletSample> collectBulletSamples(Project project, Long slideId) {
        List<BulletSample> out = new ArrayList<>();
        for (Slide slide : project.getSlides()) {
            if (slideId != null && !slideId.equals(slide.getId())) {
                continue;
            }
            if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
                continue;
            }
            String title = slide.getTitle() != null ? slide.getTitle() : "";
            if (slide.getBullets() != null) {
                for (String b : slide.getBullets()) {
                    if (isMetaBullet(b) || isQuestionLikeBullet(b)) {
                        continue;
                    }
                    out.add(new BulletSample(b, title));
                }
            }
            if (slide.getBody() != null && !slide.getBody().isBlank()
                && !isQuestionLikeBullet(slide.getBody())) {
                out.add(new BulletSample(slide.getBody(), title));
            }
        }
        return out;
    }

    private static boolean isMetaBullet(String bullet) {
        if (bullet == null || bullet.isBlank()) {
            return true;
        }
        String t = bullet.trim();
        return t.startsWith("如有需要，可进一步提供")
            || t.startsWith("如需进一步")
            || t.matches("^(欢迎|敬请).{0,12}(提问|交流|讨论).*")
            || (t.contains("预留") && t.contains("提问"))
            || t.contains("高频问题")
            || t.contains("口头回应")
            || t.contains("互动收尾")
            || t.contains("讲稿用");
    }

    private static boolean isQuestionLikeBullet(String bullet) {
        if (bullet == null || bullet.isBlank()) {
            return false;
        }
        String t = bullet.trim();
        if (t.endsWith("?") || t.endsWith("？")) {
            return true;
        }
        if (t.contains("？") && t.length() <= 120) {
            return true;
        }
        return QUESTION_BULLET.matcher(t).matches();
    }

    private Optional<FactVerificationResult> tryStructuralSkip(Project project, Long slideId) {
        if (slideId == null || project.getSlides() == null) {
            return Optional.empty();
        }
        for (Slide slide : project.getSlides()) {
            if (!slideId.equals(slide.getId())) {
                continue;
            }
            if (StructuralSlideDetector.isStructuralSlide(slide.getTitle(), slide.getChapter())) {
                return Optional.of(buildStructuralSkippedResult(slide));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private FactVerificationResult buildStructuralSkippedResult(Slide slide) {
        FactCheckDetailDto detail = new FactCheckDetailDto();
        detail.setStatement("（骨架页不参与事实抽检）");
        detail.setEvidence(IndexEvidenceQuality.skippedStructuralMessage());
        detail.setPassed(null);
        detail.setMethod("skipped");
        detail.setSlideTitle(slide.getTitle());
        return new FactVerificationResult(FACT_RATE_NOT_APPLICABLE, List.of(detail));
    }

    static double overlapRatio(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty()) {
            return 0;
        }
        long hit = ta.stream().filter(tb::contains).count();
        return hit * 1.0 / ta.size();
    }

    static Set<String> tokens(String text) {
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", " ");
        String[] parts = norm.split(" ");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            if (p.length() >= 2) {
                set.add(p);
            }
        }
        for (int i = 0; i + 1 < norm.length(); i++) {
            String bi = norm.substring(i, i + 2).trim();
            if (bi.length() == 2 && !bi.contains(" ")) {
                set.add(bi);
            }
        }
        return set;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max - 1) + "…";
    }
}
