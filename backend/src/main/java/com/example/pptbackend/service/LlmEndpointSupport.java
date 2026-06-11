package com.example.pptbackend.service;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions：URL 规范化与按服务商裁剪请求体字段。
 */
public final class LlmEndpointSupport {

    private LlmEndpointSupport() {
    }

    public static String normalizeBaseUrl(String raw, String fallback) {
        String candidate = raw != null && !raw.isBlank() ? raw.trim() : fallback;
        if (candidate == null || candidate.isBlank()) {
            return "https://api.deepseek.com";
        }
        return candidate.replaceAll("/+$", "");
    }

    public static String chatCompletionsUrl(String baseUrl) {
        String base = normalizeBaseUrl(baseUrl, null);
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/chat/completions";
    }

    /** DeepSeek 等支持 top_k；OpenAI 官方接口不接受 top_k。 */
    public static boolean supportsTopK(String baseUrl) {
        String lower = normalizeBaseUrl(baseUrl, "").toLowerCase();
        if (lower.contains("api.openai.com")) {
            return false;
        }
        if (lower.contains("openai.azure.com")) {
            return false;
        }
        return true;
    }

    public static void putOptionalTopK(Map<String, Object> payload, Integer topK, String baseUrl) {
        if (topK != null && supportsTopK(baseUrl)) {
            payload.put("top_k", topK);
        }
    }

    public static Map<String, Object> chatPayload(String model,
                                                   Object messages,
                                                   Double temperature,
                                                   Integer maxTokens,
                                                   Double topP,
                                                   Integer topK,
                                                   String baseUrl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (maxTokens != null) {
            payload.put("max_tokens", maxTokens);
        }
        if (topP != null) {
            payload.put("top_p", topP);
        }
        putOptionalTopK(payload, topK, baseUrl);
        return payload;
    }
}
