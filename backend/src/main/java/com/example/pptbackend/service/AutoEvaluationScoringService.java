package com.example.pptbackend.service;

import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.model.EvaluationReport;
import com.example.pptbackend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 依据 ILF-1 大纲与正文自动计算多维度分数，与人工评分一并写入 ILF-3。
 */
@Service
public class AutoEvaluationScoringService {

    private static final Pattern TRANSITION = Pattern.compile(
        "因此|所以|其次|再次|总之|综上|此外|然而|接下来|最后|引言|目录|结论|展望");

    private final ProjectRepository projectRepository;
    private final FactConsistencyService factConsistencyService;

    public AutoEvaluationScoringService(ProjectRepository projectRepository,
                                         FactConsistencyService factConsistencyService) {
        this.projectRepository = projectRepository;
        this.factConsistencyService = factConsistencyService;
    }

    @Transactional(readOnly = true)
    public void attachAutoScores(Long projectId, EvaluationReport report) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        List<Slide> slides = project.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getPosition))
            .toList();

        int structure = scoreStructure(slides);
        int density = scoreInformationDensity(slides);
        int sourceCov = scoreSourceCoverage(slides);
        int coherence = scoreCoherence(slides);
        double factRate01 = factConsistencyService.computeVerificationRate(projectId, project);
        int factual = mapFactualAutoScore(factRate01);

        report.setAutoOutlineLogicScore(structure);
        report.setAutoInfoDensityScore(density);
        report.setAutoSourceCoverageScore(sourceCov);
        report.setAutoLanguageExpressionScore(coherence);
        report.setAutoFactualAccuracyScore(factual);
        report.setFactVerificationRate(factRate01);

        double autoTotal = structure * 0.28 + factual * 0.28 + density * 0.18 + coherence * 0.14 + sourceCov * 0.12;
        report.setAutoTotalScore(autoTotal);
    }

    /** 将 0~1 的 factVerificationRate 映射为 0~100 自动事实分：≥0.92 记满分，否则线性折算 */
    private static int mapFactualAutoScore(double rate01) {
        if (rate01 >= 0.92) {
            return 100;
        }
        return (int) Math.round(Math.min(100, Math.max(0, rate01 / 0.92 * 100)));
    }

    private int scoreStructure(List<Slide> slides) {
        if (slides.isEmpty()) {
            return 0;
        }
        String joined = slides.stream()
            .map(Slide::getTitle)
            .map(t -> t != null ? t.toLowerCase(Locale.ROOT) : "")
            .collect(java.util.stream.Collectors.joining(" "));
        int score = 35;
        if (joined.contains("封面") || joined.contains("标题") || joined.contains("首页")) {
            score += 20;
        }
        if (joined.contains("目录") || joined.contains("纲要")) {
            score += 20;
        }
        if (joined.contains("总结") || joined.contains("结论") || joined.contains("致谢") || joined.contains("展望")) {
            score += 15;
        }
        if (slides.size() >= 5) {
            score += 10;
        }
        return Math.min(100, score);
    }

    private int scoreInformationDensity(List<Slide> slides) {
        if (slides.isEmpty()) {
            return 0;
        }
        double bulletSum = 0;
        int charSum = 0;
        for (Slide s : slides) {
            int bc = s.getBullets() != null ? s.getBullets().size() : 0;
            bulletSum += bc;
            if (s.getBullets() != null) {
                for (String b : s.getBullets()) {
                    charSum += b != null ? b.length() : 0;
                }
            }
        }
        double avgBullets = bulletSum / slides.size();
        double avgChars = charSum / (double) Math.max(1, slides.size());
        int bScore = (int) Math.round(Math.min(100, avgBullets / 5.0 * 100));
        int cScore = (int) Math.round(Math.min(100, avgChars / 180.0 * 100));
        return Math.min(100, (bScore + cScore) / 2);
    }

    private int scoreSourceCoverage(List<Slide> slides) {
        if (slides.isEmpty()) {
            return 0;
        }
        long with = slides.stream()
            .filter(s -> s.getSources() != null && !s.getSources().isEmpty())
            .count();
        return (int) Math.round(with * 100.0 / slides.size());
    }

    private int scoreCoherence(List<Slide> slides) {
        if (slides.size() < 2) {
            return 70;
        }
        int hits = 0;
        int pairs = 0;
        for (int i = 1; i < slides.size(); i++) {
            String prev = slides.get(i - 1).getTitle() + " " + slides.get(i - 1).getNotes();
            String curr = slides.get(i).getTitle() + " " + slides.get(i).getNotes();
            pairs++;
            String blob = (prev != null ? prev : "") + (curr != null ? curr : "");
            if (TRANSITION.matcher(blob).find()) {
                hits++;
            }
        }
        int base = 55;
        if (pairs > 0) {
            base += (int) (35 * (hits / (double) pairs));
        }
        return Math.min(100, base);
    }
}
