package com.example.pptbackend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 句向量：若配置 SiliconFlow，则调用 {@link SiliconFlowEmbeddingClient} 并填充至 1536 维以匹配 {@code index_segments.embedding}；
 * 否则使用确定性伪向量（本地开发）。
 */
@Service
public class EmbeddingService {

    public static final int STORAGE_DIMENSIONS = 1536;

    private final SiliconFlowEmbeddingClient siliconFlowEmbeddingClient;

    public EmbeddingService(SiliconFlowEmbeddingClient siliconFlowEmbeddingClient) {
        this.siliconFlowEmbeddingClient = siliconFlowEmbeddingClient;
    }

    public boolean isSemanticEmbeddingEnabled() {
        return siliconFlowEmbeddingClient.isAvailable();
    }

    public List<Float> embed(String text) {
        if (siliconFlowEmbeddingClient.isAvailable()) {
            try {
                List<Float> raw = siliconFlowEmbeddingClient.embedRaw(text);
                return padOrTruncateToStorage(raw);
            } catch (Exception ignored) {
                // 降级为伪向量，避免索引写入失败；事实语义抽检见 FactConsistencyService 分支
            }
        }
        return pseudoEmbed(text);
    }

    static List<Float> padOrTruncateToStorage(List<Float> raw) {
        if (raw == null || raw.isEmpty()) {
            return pseudoEmbed("empty");
        }
        List<Float> out = new ArrayList<>(STORAGE_DIMENSIONS);
        for (int i = 0; i < Math.min(raw.size(), STORAGE_DIMENSIONS); i++) {
            out.add(raw.get(i));
        }
        while (out.size() < STORAGE_DIMENSIONS) {
            out.add(0f);
        }
        return out;
    }

    private static List<Float> pseudoEmbed(String text) {
        if (text == null || text.isBlank()) {
            return pseudoEmbed("empty");
        }
        Random random = new Random(text.hashCode());
        List<Float> embedding = new ArrayList<>(STORAGE_DIMENSIONS);
        for (int i = 0; i < STORAGE_DIMENSIONS; i++) {
            embedding.add(random.nextFloat() * 2f - 1f);
        }
        return embedding;
    }
}
