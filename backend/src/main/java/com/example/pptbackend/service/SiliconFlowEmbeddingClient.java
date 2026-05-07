package com.example.pptbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow OpenAI-compatible embeddings API（任务 9：语义事实验证）。
 * 密钥仅来自配置 {@code siliconflow.api-key} / 环境变量 {@code SILICONFLOW_API_KEY}。
 */
@Component
public class SiliconFlowEmbeddingClient {

    private static final String ENDPOINT = "https://api.siliconflow.cn/v1/embeddings";
    private static final String MODEL = "BAAI/bge-large-zh-v1.5";
    private static final int INPUT_CAP = 8000;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public SiliconFlowEmbeddingClient(RestTemplate restTemplate,
                                      @Value("${siliconflow.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    /**
     * 原始向量维度与模型一致（bge-large-zh-v1.5 为 1024）；失败时抛出由调用方处理。
     */
    public List<Float> embedRaw(String text) {
        if (!isAvailable()) {
            throw new IllegalStateException("SiliconFlow API key is not configured");
        }
        String safe = text == null || text.isBlank() ? "empty" : text;
        if (safe.length() > INPUT_CAP) {
            safe = safe.substring(0, INPUT_CAP);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
            "model", MODEL,
            "input", safe
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        JsonNode root = restTemplate.postForObject(ENDPOINT, entity, JsonNode.class);
        if (root == null || root.isNull()) {
            throw new IllegalStateException("SiliconFlow returned empty body");
        }
        JsonNode emb = root.path("data").path(0).path("embedding");
        if (!emb.isArray() || emb.size() == 0) {
            throw new IllegalStateException("SiliconFlow response missing embedding array");
        }
        List<Float> out = new ArrayList<>(emb.size());
        for (JsonNode n : emb) {
            out.add((float) n.asDouble());
        }
        return out;
    }
}
