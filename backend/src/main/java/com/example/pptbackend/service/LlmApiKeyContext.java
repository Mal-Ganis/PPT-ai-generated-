package com.example.pptbackend.service;

/**
 * 兼容旧字节码与热重载场景；新代码请使用 {@link LlmRequestContext}。
 */
@Deprecated
public final class LlmApiKeyContext {

    private LlmApiKeyContext() {
    }

    public static void set(String resolvedApiKey) {
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            LlmRequestContext.clear();
            return;
        }
        LlmConnectionConfig existing = LlmRequestContext.get();
        LlmRequestContext.set(new LlmConnectionConfig(
            resolvedApiKey.trim(),
            existing != null ? existing.baseUrl() : null,
            existing != null ? existing.model() : null));
    }

    public static String get() {
        return LlmRequestContext.resolveApiKey();
    }

    public static void clear() {
        LlmRequestContext.clear();
    }
}
