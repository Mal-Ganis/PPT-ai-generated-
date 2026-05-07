package com.example.pptbackend.service;

import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.model.Project;
import com.example.pptbackend.model.Slide;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 事实验证：配置 SiliconFlow 时用语义余弦相似度（任务 9）；否则回退为词重叠抽检。
 */
@Service
public class FactConsistencyService {

    private static final int SAMPLE_CAP = 5;

    private final IndexSegmentService indexSegmentService;
    private final SiliconFlowEmbeddingClient siliconFlowEmbeddingClient;

    public FactConsistencyService(IndexSegmentService indexSegmentService,
                                    SiliconFlowEmbeddingClient siliconFlowEmbeddingClient) {
        this.indexSegmentService = indexSegmentService;
        this.siliconFlowEmbeddingClient = siliconFlowEmbeddingClient;
    }

    /**
     * @return 0~1，表示抽检陈述与 ILF-2 检索片段的平均最高语义证据支持度（或回退策略下的等价比例）
     */
    public double computeVerificationRate(Long projectId, Project project) {
        if (siliconFlowEmbeddingClient.isAvailable()) {
            try {
                return computeSemanticEvidenceRate(projectId, project);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return computeOverlapRateLegacy(projectId, project) / 100.0;
    }

    private double computeSemanticEvidenceRate(Long projectId, Project project) {
        List<String> bullets = collectBullets(project);
        bullets.removeIf(b -> b == null || b.trim().length() < 10);
        if (bullets.isEmpty()) {
            return 0;
        }
        Collections.shuffle(bullets, new Random(projectId != null ? projectId : 42L));
        int take = Math.min(SAMPLE_CAP, bullets.size());
        double sum = 0;
        for (int i = 0; i < take; i++) {
            String bullet = bullets.get(i);
            List<Float> stmtVec = siliconFlowEmbeddingClient.embedRaw(bullet);
            SearchResponse response = indexSegmentService.searchByText(projectId, bullet, 3);
            double best = 0;
            if (response.getResults() != null) {
                for (var hit : response.getResults()) {
                    String evidence = hit.getContent();
                    if (evidence == null || evidence.isBlank()) {
                        continue;
                    }
                    List<Float> evVec = siliconFlowEmbeddingClient.embedRaw(evidence);
                    best = Math.max(best, cosineSimilarity(stmtVec, evVec));
                }
            }
            sum += best;
        }
        return sum / take;
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

    /**
     * @return 0–100（旧口径，无向量密钥时使用）
     */
    private double computeOverlapRateLegacy(Long projectId, Project project) {
        List<String> bullets = collectBullets(project);
        bullets.removeIf(b -> b == null || b.trim().length() < 10);
        if (bullets.isEmpty()) {
            return 0;
        }
        Collections.shuffle(bullets, new Random(projectId != null ? projectId : 42L));
        int take = Math.min(SAMPLE_CAP, bullets.size());
        int verified = 0;
        for (int i = 0; i < take; i++) {
            String bullet = bullets.get(i);
            SearchResponse response = indexSegmentService.searchByText(projectId, bullet, 3);
            if (response.getResults() == null || response.getResults().isEmpty()) {
                continue;
            }
            String evidence = response.getResults().get(0).getContent();
            if (evidence != null && overlapRatio(bullet, evidence) >= 0.22) {
                verified++;
            }
        }
        return take == 0 ? 0 : (verified * 100.0 / take);
    }

    private List<String> collectBullets(Project project) {
        List<String> out = new ArrayList<>();
        for (Slide slide : project.getSlides()) {
            if (slide.getBullets() != null) {
                out.addAll(slide.getBullets());
            }
            if (slide.getBody() != null && !slide.getBody().isBlank()) {
                out.add(slide.getBody());
            }
        }
        return out;
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
}
