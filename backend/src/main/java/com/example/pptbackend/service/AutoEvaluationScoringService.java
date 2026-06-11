package com.example.pptbackend.service;

import com.example.pptbackend.model.EvaluationReport;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import com.example.pptbackend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 依据 ILF-1 大纲与正文自动计算多维度分数，与人工评分一并写入 ILF-3。
 * 事实准确抽检已停用，不在自动分与质量门禁中计权。
 */
@Service
public class AutoEvaluationScoringService {

    private static final Pattern TRANSITION = Pattern.compile(
        "因此|所以|其次|再次|总之|综上|此外|然而|接下来|最后|引言|目录|结论|展望");

    private final ProjectRepository projectRepository;

    public AutoEvaluationScoringService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public void attachAutoScores(Long projectId, EvaluationReport report) {
        attachAutoScores(projectId, report, report.getPageId());
    }

    @Transactional(readOnly = true)
    public void attachAutoScores(Long projectId, EvaluationReport report, Long pageId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        List<Slide> allSlides = project.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getPosition))
            .toList();
        List<Slide> slides = pageId != null
            ? allSlides.stream().filter(s -> pageId.equals(s.getId())).toList()
            : allSlides;

        if (slides.isEmpty()) {
            zeroOutAutoScores(report);
            return;
        }

        int structure = pageId != null ? scoreSingleSlideStructure(slides.get(0)) : scoreStructure(slides);
        int density = scoreInformationDensity(slides);
        int sourceCov = scoreSourceCoverage(slides);
        int coherence = pageId != null ? scoreSingleSlideCoherence(slides.get(0)) : scoreCoherence(slides);

        report.setAutoOutlineLogicScore(structure);
        report.setAutoInfoDensityScore(density);
        report.setAutoSourceCoverageScore(sourceCov);
        report.setAutoLanguageExpressionScore(coherence);
        report.setAutoFactualAccuracyScore(null);
        report.setFactVerificationRate(null);
        report.setFactCheckDetails("[]");

        double autoTotal = structure * 0.35 + density * 0.25 + coherence * 0.20 + sourceCov * 0.20;
        report.setAutoTotalScore(autoTotal);
    }

    private void zeroOutAutoScores(EvaluationReport report) {
        report.setAutoOutlineLogicScore(0);
        report.setAutoInfoDensityScore(0);
        report.setAutoSourceCoverageScore(0);
        report.setAutoLanguageExpressionScore(0);
        report.setAutoFactualAccuracyScore(null);
        report.setFactVerificationRate(null);
        report.setFactCheckDetails("[]");
        report.setAutoTotalScore(0.0);
    }

    private int scoreSingleSlideStructure(Slide slide) {
        int score = 50;
        String title = slide.getTitle() != null ? slide.getTitle().toLowerCase(Locale.ROOT) : "";
        int bullets = slide.getBullets() != null ? slide.getBullets().size() : 0;
        if (bullets >= 3) {
            score += 25;
        } else if (bullets >= 1) {
            score += 10;
        }
        if (slide.getSources() != null && !slide.getSources().isEmpty()) {
            score += 15;
        }
        if (title.contains("目录") || title.contains("结论") || title.contains("总结")) {
            score += 10;
        }
        return Math.min(100, score);
    }

    private int scoreSingleSlideCoherence(Slide slide) {
        String blob = (slide.getTitle() != null ? slide.getTitle() : "")
            + " " + (slide.getNotes() != null ? slide.getNotes() : "");
        return TRANSITION.matcher(blob).find() ? 78 : 68;
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
