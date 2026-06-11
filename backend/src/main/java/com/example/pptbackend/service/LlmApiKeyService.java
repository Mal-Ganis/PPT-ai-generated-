package com.example.pptbackend.service;



import com.example.pptbackend.dto.LlmApiKeyPresetDto;

import com.example.pptbackend.model.SystemConfig;

import com.example.pptbackend.repository.SystemConfigRepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import java.util.List;

import java.util.Optional;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;



@Service

public class LlmApiKeyService {



    private static final int MAX_PRESETS = 20;

    private static final int MAX_LABEL_LEN = 80;

    private static final int MAX_KEY_LEN = 256;

    private static final int MAX_BASE_URL_LEN = 256;

    private static final int MAX_MODEL_LEN = 128;



    private final SystemConfigRepository systemConfigRepository;

    private final ObjectMapper objectMapper;

    private final String configuredDefaultBaseUrl;



    public LlmApiKeyService(SystemConfigRepository systemConfigRepository,

                            ObjectMapper objectMapper,

                            @Value("${llm.base-url:https://api.deepseek.com}") String configuredDefaultBaseUrl) {

        this.systemConfigRepository = systemConfigRepository;

        this.objectMapper = objectMapper;

        this.configuredDefaultBaseUrl = configuredDefaultBaseUrl;

    }



    public List<LlmApiKeyPresetDto> listPresetsForUsers() {

        return parsePresets(loadPresetsJson()).stream().map(this::toMaskedDto).toList();

    }



    public List<LlmApiKeyPresetDto> listPresetsForAdmin() {

        return parsePresets(loadPresetsJson()).stream().map(this::toAdminDto).toList();

    }



    public void savePresetsFromAdmin(List<LlmApiKeyPresetDto> incoming) {

        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc().orElseGet(SystemConfig::new);

        List<StoredPreset> normalized = normalizeIncoming(incoming);

        try {

            config.setLlmApiKeyPresetsJson(objectMapper.writeValueAsString(normalized));

        } catch (Exception e) {

            throw new IllegalArgumentException("无法保存 API 密钥预设：" + e.getMessage());

        }

        systemConfigRepository.save(config);

    }



    /**

     * 解析本次生成应使用的密钥：请求 override &gt; 项目/请求 preset &gt; 服务器默认（返回 null）。

     */

    public String resolve(String presetId, String requestOverride) {

        return resolveConnection(presetId, requestOverride, null, null).apiKey();

    }



    /**

     * 解析密钥、接口地址与模型；override &gt; preset &gt; 系统默认。

     */

    public LlmConnectionConfig resolveConnection(String presetId,

                                                 String keyOverride,

                                                 String baseUrlOverride,

                                                 String modelOverride) {

        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc().orElse(null);

        String defaultBaseUrl = config != null && config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isBlank()

            ? config.getLlmBaseUrl().trim()

            : configuredDefaultBaseUrl;

        String defaultModel = config != null && config.getLlmModel() != null && !config.getLlmModel().isBlank()

            ? config.getLlmModel().trim()

            : "deepseek-chat";

        return resolveConnection(presetId, keyOverride, baseUrlOverride, modelOverride, defaultBaseUrl, defaultModel);

    }



    public LlmConnectionConfig resolveConnection(String presetId,

                                                 String keyOverride,

                                                 String baseUrlOverride,

                                                 String modelOverride,

                                                 String defaultBaseUrl,

                                                 String defaultModel) {

        Optional<StoredPreset> preset = findPresetById(presetId);



        String apiKey = sanitizeKey(keyOverride);

        if (apiKey == null) {

            apiKey = preset.map(StoredPreset::apiKey).orElse(null);

        }



        String baseUrl = sanitizeBaseUrl(baseUrlOverride);

        if (baseUrl == null) {

            baseUrl = preset.map(StoredPreset::baseUrl).orElse(null);

        }

        if (baseUrl == null) {

            baseUrl = LlmEndpointSupport.normalizeBaseUrl(defaultBaseUrl, configuredDefaultBaseUrl);

        }



        String model = sanitizeModel(modelOverride);

        if (model == null) {

            model = preset.map(StoredPreset::model).orElse(null);

        }

        if (model == null) {

            model = defaultModel;

        }



        return new LlmConnectionConfig(apiKey, baseUrl, model);

    }



    public Optional<StoredPreset> findPresetById(String presetId) {

        if (presetId == null || presetId.isBlank()) {

            return Optional.empty();

        }

        return parsePresets(loadPresetsJson()).stream()

            .filter(p -> presetId.trim().equals(p.id()))

            .findFirst();

    }



    public Optional<String> findKeyById(String presetId) {

        return findPresetById(presetId).map(StoredPreset::apiKey);

    }



    public static String maskApiKey(String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {

            return "—";

        }

        String trimmed = apiKey.trim();

        if (trimmed.length() <= 8) {

            return "****";

        }

        return trimmed.substring(0, Math.min(7, trimmed.length()))

            + "****"

            + trimmed.substring(trimmed.length() - 4);

    }



    private String loadPresetsJson() {

        return systemConfigRepository.findTopByOrderByIdAsc()

            .map(SystemConfig::getLlmApiKeyPresetsJson)

            .orElse(null);

    }



    private List<StoredPreset> parsePresets(String json) {

        if (json == null || json.isBlank()) {

            return List.of();

        }

        try {

            List<StoredPreset> list = objectMapper.readValue(json, new TypeReference<>() {});

            return list != null ? list : List.of();

        } catch (Exception e) {

            return List.of();

        }

    }



    private List<StoredPreset> normalizeIncoming(List<LlmApiKeyPresetDto> incoming) {

        if (incoming == null || incoming.isEmpty()) {

            return List.of();

        }

        List<StoredPreset> result = new ArrayList<>();

        int count = 0;

        for (LlmApiKeyPresetDto dto : incoming) {

            if (dto == null || count >= MAX_PRESETS) {

                continue;

            }

            String label = sanitizeLabel(dto.getLabel());

            String apiKey = sanitizeKey(dto.getApiKey());

            if (label == null || apiKey == null) {

                continue;

            }

            String id = dto.getId() != null && !dto.getId().isBlank() ? dto.getId().trim() : UUID.randomUUID().toString();

            if (id.length() > 64) {

                id = id.substring(0, 64);

            }

            result.add(new StoredPreset(

                id,

                label,

                apiKey,

                sanitizeBaseUrl(dto.getBaseUrl()),

                sanitizeModel(dto.getModel())));

            count++;

        }

        return result;

    }



    private LlmApiKeyPresetDto toMaskedDto(StoredPreset preset) {

        LlmApiKeyPresetDto dto = new LlmApiKeyPresetDto();

        dto.setId(preset.id());

        dto.setLabel(preset.label());

        dto.setMaskedKey(maskApiKey(preset.apiKey()));

        dto.setBaseUrl(preset.baseUrl());

        dto.setModel(preset.model());

        return dto;

    }



    private LlmApiKeyPresetDto toAdminDto(StoredPreset preset) {

        LlmApiKeyPresetDto dto = toMaskedDto(preset);

        dto.setApiKey(preset.apiKey());

        return dto;

    }



    private static String sanitizeLabel(String raw) {

        if (raw == null) {

            return null;

        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {

            return null;

        }

        return trimmed.length() > MAX_LABEL_LEN ? trimmed.substring(0, MAX_LABEL_LEN) : trimmed;

    }



    private static String sanitizeKey(String raw) {

        if (raw == null) {

            return null;

        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {

            return null;

        }

        return trimmed.length() > MAX_KEY_LEN ? trimmed.substring(0, MAX_KEY_LEN) : trimmed;

    }



    private static String sanitizeBaseUrl(String raw) {

        if (raw == null) {

            return null;

        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {

            return null;

        }

        return trimmed.length() > MAX_BASE_URL_LEN ? trimmed.substring(0, MAX_BASE_URL_LEN) : trimmed;

    }



    private static String sanitizeModel(String raw) {

        if (raw == null) {

            return null;

        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {

            return null;

        }

        return trimmed.length() > MAX_MODEL_LEN ? trimmed.substring(0, MAX_MODEL_LEN) : trimmed;

    }



    @JsonIgnoreProperties(ignoreUnknown = true)

    public record StoredPreset(String id, String label, String apiKey, String baseUrl, String model) {

        public StoredPreset(String id, String label, String apiKey) {

            this(id, label, apiKey, null, null);

        }

    }

}

