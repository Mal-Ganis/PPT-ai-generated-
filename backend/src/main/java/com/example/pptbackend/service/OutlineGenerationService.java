package com.example.pptbackend.service;

import com.example.pptbackend.dto.ProjectOutlineResponse;
import com.example.pptbackend.dto.SystemConfigDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OutlineGenerationService {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String API_KEY_ENV = "DEEPSEEK_API_KEY";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final HttpClient httpClient;

    public OutlineGenerationService(ObjectMapper objectMapper, SystemConfigService systemConfigService) {
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public ProjectOutlineResponse generateOutline(String topic) {
        SystemConfigDto config = systemConfigService.getSystemConfig();
        String prompt = formatPrompt(config.getOutlinePromptTemplate(), Map.of("content", topic));

        String body = buildRequestBody(prompt, config);
        String responseText = callDeepseek(body);
        return parseOutlineResponse(responseText);
    }

    private String formatPrompt(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prompt;
    }

    private String buildRequestBody(String prompt, SystemConfigDto config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getLlmModel());
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", config.getMaxTokens());
        payload.put("top_p", config.getTopP());
        payload.put("top_k", config.getTopK());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generation request", e);
        }
    }

    private String callDeepseek(String body) {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + API_KEY_ENV);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DEEPSEEK_BASE_URL + "/chat/completions"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Deepseek API request failed: " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call Deepseek API", e);
        }
    }

    private ProjectOutlineResponse parseOutlineResponse(String responseText) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseText, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("No choices returned by generation API");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                throw new IllegalStateException("No message returned by generation API");
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Empty generation content returned");
            }

            Map<String, Object> outline = objectMapper.readValue(content, new TypeReference<>() {});
            ProjectOutlineResponse response = new ProjectOutlineResponse();
            response.setTitle((String) outline.getOrDefault("title", "PPT 演示文稿"));

            List<Map<String, Object>> slides = (List<Map<String, Object>>) outline.get("slides");
            if (slides != null) {
                for (Map<String, Object> slide : slides) {
                    ProjectOutlineResponse.OutlineSlide outlineSlide = new ProjectOutlineResponse.OutlineSlide();
                    outlineSlide.setId((Integer) slide.getOrDefault("id", 0));
                    outlineSlide.setTitle((String) slide.getOrDefault("title", ""));
                    Object contentValue = slide.get("content");
                    if (contentValue instanceof List<?> list) {
                        String[] contentArray = list.stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                        outlineSlide.setContent(contentArray);
                    } else {
                        outlineSlide.setContent(new String[0]);
                    }
                    outlineSlide.setNotes((String) slide.getOrDefault("notes", ""));
                    response.getSlides().add(outlineSlide);
                }
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse generation response", e);
        }
    }
}
