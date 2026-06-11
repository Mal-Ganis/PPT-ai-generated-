package com.example.pptbackend.dto;



/**

 * 管理员配置的 LLM API 密钥预设；列表接口对普通用户只返回 maskedKey。

 */

public class LlmApiKeyPresetDto {



    private String id;

    private String label;

    /** 脱敏展示，如 sk-abc****xyz */

    private String maskedKey;

    /** 完整密钥，仅管理员保存/读取配置时使用 */

    private String apiKey;

    /** OpenAI 兼容接口 Base URL，如 https://api.openai.com/v1 */

    private String baseUrl;

    /** 该预设默认模型，如 gpt-4o-mini、deepseek-chat */

    private String model;



    public String getId() {

        return id;

    }



    public void setId(String id) {

        this.id = id;

    }



    public String getLabel() {

        return label;

    }



    public void setLabel(String label) {

        this.label = label;

    }



    public String getMaskedKey() {

        return maskedKey;

    }



    public void setMaskedKey(String maskedKey) {

        this.maskedKey = maskedKey;

    }



    public String getApiKey() {

        return apiKey;

    }



    public void setApiKey(String apiKey) {

        this.apiKey = apiKey;

    }



    public String getBaseUrl() {

        return baseUrl;

    }



    public void setBaseUrl(String baseUrl) {

        this.baseUrl = baseUrl;

    }



    public String getModel() {

        return model;

    }



    public void setModel(String model) {

        this.model = model;

    }

}

