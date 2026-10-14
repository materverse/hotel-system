package com.cqucc.hotel.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置。
 * <p>所有密钥、地址、模型名均来自 application.properties，并通过环境变量注入：
 * GYM_AI_API_KEY / GYM_AI_BASE_URL / GYM_AI_MODEL / GYM_AI_PROVIDER，
 * 代码中不出现任何密钥，避免密钥进入代码仓库。</p>
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 总开关，关闭后 AI 功能统一走本地降级策略。 */
    private boolean enabled = true;
    /** 提供方标识，仅用于日志与展示。 */
    private String provider = "deepseek";
    /** 兼容 OpenAI 协议的接口地址。 */
    private String baseUrl = "https://api.deepseek.com";
    private String chatPath = "/chat/completions";
    /** 密钥，从环境变量 GYM_AI_API_KEY 注入。 */
    private String apiKey = "";
    private String model = "deepseek-chat";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
    private int maxRetries = 2;
    private long retryBackoffMs = 800;
    private int rateLimitPerMinute = 20;
    private int maxContextChars = 12000;
    private int maxOutputTokens = 1500;
    private double temperature = 0.3;
    private int promptPriceCentsPer1k = 0;
    private int outputPriceCentsPer1k = 0;
    private int agentMaxSteps = 4;
    private String promptVersion = "v1.2";

    /** 是否具备真实调用条件。 */
    public boolean callable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getPromptPriceCentsPer1k() {
        return promptPriceCentsPer1k;
    }

    public void setPromptPriceCentsPer1k(int promptPriceCentsPer1k) {
        this.promptPriceCentsPer1k = promptPriceCentsPer1k;
    }

    public int getOutputPriceCentsPer1k() {
        return outputPriceCentsPer1k;
    }

    public void setOutputPriceCentsPer1k(int outputPriceCentsPer1k) {
        this.outputPriceCentsPer1k = outputPriceCentsPer1k;
    }

    public int getAgentMaxSteps() {
        return agentMaxSteps;
    }

    public void setAgentMaxSteps(int agentMaxSteps) {
        this.agentMaxSteps = agentMaxSteps;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }
}
