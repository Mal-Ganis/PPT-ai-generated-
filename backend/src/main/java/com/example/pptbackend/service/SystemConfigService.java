package com.example.pptbackend.service;

import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.SystemConfig;
import com.example.pptbackend.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Transactional
    public SystemConfigDto getSystemConfig() {
        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc()
            .orElseGet(this::createDefaultConfig);
        return toDto(config);
    }

    @Transactional
    public SystemConfigDto saveSystemConfig(SystemConfigDto dto) {
        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc().orElse(new SystemConfig());
        config.setLlmModel(dto.getLlmModel());
        config.setTemperature(dto.getTemperature());
        config.setMaxTokens(dto.getMaxTokens());
        config.setTopP(dto.getTopP());
        config.setTopK(dto.getTopK());
        config.setRetrievalLimit(dto.getRetrievalLimit());
        config.setOutlinePromptTemplate(dto.getOutlinePromptTemplate());
        config.setSlidePromptTemplate(dto.getSlidePromptTemplate());

        return toDto(systemConfigRepository.save(config));
    }

    private SystemConfigDto toDto(SystemConfig config) {
        SystemConfigDto dto = new SystemConfigDto();
        dto.setId(config.getId());
        dto.setLlmModel(config.getLlmModel());
        dto.setTemperature(config.getTemperature());
        dto.setMaxTokens(config.getMaxTokens());
        dto.setTopP(config.getTopP());
        dto.setTopK(config.getTopK());
        dto.setRetrievalLimit(config.getRetrievalLimit());
        dto.setOutlinePromptTemplate(config.getOutlinePromptTemplate());
        dto.setSlidePromptTemplate(config.getSlidePromptTemplate());
        return dto;
    }

    private SystemConfig createDefaultConfig() {
        SystemConfig config = new SystemConfig();
        config.setLlmModel("deepseek-reasoner");
        config.setTemperature(0.7);
        config.setMaxTokens(1024);
        config.setTopP(0.95);
        config.setTopK(1);
        config.setRetrievalLimit(5);
        config.setOutlinePromptTemplate("请根据以下主题生成一个专业的PPT大纲。主题：{content}\n\n请以JSON格式返回，格式如下：{\n  \"title\": \"大纲标题\",\n  \"slides\": [\n    {\n      \"id\": 1,\n      \"title\": \"幻灯片标题\",\n      \"content\": [\"要点1\", \"要点2\"],\n      \"notes\": \"演讲者备注\"\n    }\n  ]\n}\n\n大纲应该包括封面、目录、主要内容章节和总结，至少5-8页。");
        config.setSlidePromptTemplate("请为PPT幻灯片生成详细内容。\n\n幻灯片标题：{slideTitle}\n原始输入类型：{inputType}\n原始输入内容：{inputContent}\n\n请生成：\n1. 3-5个主要内容要点（数组）\n2. 演讲者备注（字符串）\n\n请以JSON格式返回：{\n  \"content\": [\"要点1\", \"要点2\", \"要点3\"],\n  \"notes\": \"演讲者备注内容\"\n}");
        return systemConfigRepository.save(config);
    }
}
