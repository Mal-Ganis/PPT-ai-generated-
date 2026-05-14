package com.example.pptbackend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * DeepSeek Chat Completions with retries (exponential backoff) for transient failures.
 */
@Service
public class DeepseekChatClient {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String API_KEY_ENV = "DEEPSEEK_API_KEY";
    private static final int MAX_ATTEMPTS = 3;

    private final String configuredApiKey;
    private final ExecutorService llmHttpExecutor;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    public DeepseekChatClient(@Value("${deepseek.api-key:}") String configuredApiKey,
                              @Qualifier("llmHttpExecutor") ExecutorService llmHttpExecutor) {
        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
        this.llmHttpExecutor = llmHttpExecutor;
    }

    public String chatCompletions(String jsonBody, Duration timeoutPerAttempt) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                "缺少 DeepSeek API 密钥：请在环境变量 " + API_KEY_ENV + " 或配置项 deepseek.api-key 中设置。");
        }

        long backoffMs = 400;
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return executeOnceOffloaded(jsonBody, apiKey, timeoutPerAttempt);
            } catch (IOException e) {
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("大模型重试等待被中断", ie);
                }
                backoffMs *= 2;
            }
        }
        throw new IllegalStateException("大模型请求失败，已重试 " + MAX_ATTEMPTS + " 次：" + (last != null ? last.getMessage() : "unknown"));
    }

    private String resolveApiKey() {
        if (!configuredApiKey.isBlank()) {
            return configuredApiKey;
        }
        String fromEnv = System.getenv(API_KEY_ENV);
        return fromEnv != null ? fromEnv.trim() : "";
    }

    /**
     * 在专用线程执行阻塞 {@code HttpClient.send}，请求线程用不响应中断的方式等待，避免 Tomcat
     * 对请求线程 interrupt 时被误判为「大模型请求被中断」。
     */
    private String executeOnceOffloaded(String jsonBody, String apiKey, Duration timeout) throws IOException {
        Future<String> future = llmHttpExecutor.submit(() -> executeBlockingSend(jsonBody, apiKey, timeout));
        try {
            return getUninterruptibly(future);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException ioe) {
                throw ioe;
            }
            if (c instanceof Error err) {
                throw err;
            }
            if (c instanceof InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("大模型 HTTP 工作线程被中断", ie);
            }
            throw new IOException(c != null ? c.getMessage() : "大模型请求执行失败", c);
        }
    }

    private String executeBlockingSend(String jsonBody, String apiKey, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DEEPSEEK_BASE_URL + "/chat/completions"))
            .timeout(timeout != null ? timeout : Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    /**
     * 等待 Future 完成期间忽略当前线程的 {@link InterruptedException}，避免与外部 HTTP 无关的误中断。
     * 若等待期间曾被中断，在返回前恢复中断标记。
     */
    private static <T> T getUninterruptibly(Future<T> future) throws ExecutionException {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
