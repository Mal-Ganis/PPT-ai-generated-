package com.example.pptbackend.service;

/**
 * 单次生成任务内传递已解析的 LLM 连接参数（含异步正文生成线程）。
 */
public final class LlmRequestContext {

    private static final ThreadLocal<LlmConnectionConfig> RESOLVED = new ThreadLocal<>();

    private LlmRequestContext() {
    }

    public static void set(LlmConnectionConfig config) {
        if (config == null || (isBlank(config.apiKey()) && isBlank(config.baseUrl()) && isBlank(config.model()))) {
            RESOLVED.remove();
        } else {
            RESOLVED.set(config);
        }
    }

    public static LlmConnectionConfig get() {
        return RESOLVED.get();
    }

    public static String resolveApiKey() {
        LlmConnectionConfig config = RESOLVED.get();
        return config != null && !isBlank(config.apiKey()) ? config.apiKey().trim() : null;
    }

    public static String resolveBaseUrl(String fallback) {
        LlmConnectionConfig config = RESOLVED.get();
        if (config != null && !isBlank(config.baseUrl())) {
            return config.baseUrl().trim();
        }
        return fallback != null ? fallback.trim() : "";
    }

    public static String resolveModel(String fallback) {
        LlmConnectionConfig config = RESOLVED.get();
        if (config != null && !isBlank(config.model())) {
            return config.model().trim();
        }
        return fallback != null && !fallback.isBlank() ? fallback.trim() : null;
    }

    public static void clear() {
        RESOLVED.remove();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
