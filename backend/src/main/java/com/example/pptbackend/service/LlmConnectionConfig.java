package com.example.pptbackend.service;

/**
 * 单次 LLM 调用解析出的连接参数（OpenAI 兼容 Chat Completions）。
 */
public record LlmConnectionConfig(String apiKey, String baseUrl, String model) {

    public static LlmConnectionConfig empty() {
        return new LlmConnectionConfig(null, null, null);
    }

    public LlmConnectionConfig withApiKey(String key) {
        return new LlmConnectionConfig(key, baseUrl, model);
    }
}
