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

 * OpenAI 兼容 Chat Completions（DeepSeek / OpenAI / 各类中转），带重试。

 */

@Service

public class DeepseekChatClient {



    private static final String API_KEY_ENV = "DEEPSEEK_API_KEY";

    private static final int MAX_ATTEMPTS = 3;



    private final String configuredApiKey;

    private final String configuredDefaultBaseUrl;

    private final ExecutorService llmHttpExecutor;



    private final HttpClient httpClient = HttpClient.newBuilder()

        .connectTimeout(Duration.ofSeconds(15))

        .build();



    public DeepseekChatClient(@Value("${deepseek.api-key:}") String configuredApiKey,

                              @Value("${llm.base-url:https://api.deepseek.com}") String configuredDefaultBaseUrl,

                              @Qualifier("llmHttpExecutor") ExecutorService llmHttpExecutor) {

        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";

        this.configuredDefaultBaseUrl = LlmEndpointSupport.normalizeBaseUrl(configuredDefaultBaseUrl, "https://api.deepseek.com");

        this.llmHttpExecutor = llmHttpExecutor;

    }



    public String chatCompletions(String jsonBody, Duration timeoutPerAttempt) {

        return chatCompletions(jsonBody, timeoutPerAttempt, null, null);

    }



    public String chatCompletions(String jsonBody, Duration timeoutPerAttempt, String explicitApiKey) {

        return chatCompletions(jsonBody, timeoutPerAttempt, explicitApiKey, null);

    }



    public String chatCompletions(String jsonBody,

                                  Duration timeoutPerAttempt,

                                  String explicitApiKey,

                                  String explicitBaseUrl) {

        String apiKey = resolveApiKey(explicitApiKey);

        if (apiKey.isBlank()) {

            throw new IllegalStateException(

                "缺少 LLM API 密钥：请在环境变量 " + API_KEY_ENV + "、系统配置预设或生成页自定义密钥中设置。");

        }

        String baseUrl = resolveBaseUrl(explicitBaseUrl);



        long backoffMs = 400;

        Exception last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            try {

                return executeOnceOffloaded(jsonBody, apiKey, baseUrl, timeoutPerAttempt);

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



    private String resolveApiKey(String explicitApiKey) {

        if (explicitApiKey != null && !explicitApiKey.isBlank()) {

            return explicitApiKey.trim();

        }

        String fromContext = LlmRequestContext.resolveApiKey();

        if (fromContext != null && !fromContext.isBlank()) {

            return fromContext.trim();

        }

        if (!configuredApiKey.isBlank()) {

            return configuredApiKey;

        }

        String fromEnv = System.getenv(API_KEY_ENV);

        return fromEnv != null ? fromEnv.trim() : "";

    }



    private String resolveBaseUrl(String explicitBaseUrl) {

        if (explicitBaseUrl != null && !explicitBaseUrl.isBlank()) {

            return LlmEndpointSupport.normalizeBaseUrl(explicitBaseUrl, configuredDefaultBaseUrl);

        }

        String fromContext = LlmRequestContext.resolveBaseUrl(null);

        if (fromContext != null && !fromContext.isBlank()) {

            return LlmEndpointSupport.normalizeBaseUrl(fromContext, configuredDefaultBaseUrl);

        }

        return configuredDefaultBaseUrl;

    }



    private String executeOnceOffloaded(String jsonBody, String apiKey, String baseUrl, Duration timeout) throws IOException {

        Future<String> future = llmHttpExecutor.submit(() -> executeBlockingSend(jsonBody, apiKey, baseUrl, timeout));

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



    private String executeBlockingSend(String jsonBody, String apiKey, String baseUrl, Duration timeout)

        throws IOException, InterruptedException {

        String url = LlmEndpointSupport.chatCompletionsUrl(baseUrl);

        HttpRequest request = HttpRequest.newBuilder()

            .uri(URI.create(url))

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

