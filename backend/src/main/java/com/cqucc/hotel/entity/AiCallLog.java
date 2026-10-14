package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * AiCallLog 实体，对应数据库表 ai_call_log。
 */
public class AiCallLog {

    /** 主键 */
    private Long id;
    /** 调用用户 */
    private Long userId;
    /** 业务场景 */
    private String scene;
    /** 模型提供方 */
    private String provider;
    /** 模型名称 */
    private String model;
    /** 提示词版本 */
    private String promptVersion;
    /** 输入 token */
    private Integer promptTokens;
    /** 输出 token */
    private Integer outputTokens;
    /** 估算成本（分） */
    private Integer costCents;
    /** 耗时(ms) */
    private Integer latencyMs;
    /** 重试次数 */
    private Integer retryCount;
    /** 是否成功 */
    private Integer success;
    /** 是否降级 */
    private Integer degraded;
    /** 错误信息 */
    private String errorMsg;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getCostCents() {
        return costCents;
    }

    public void setCostCents(Integer costCents) {
        this.costCents = costCents;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public Integer getDegraded() {
        return degraded;
    }

    public void setDegraded(Integer degraded) {
        this.degraded = degraded;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}