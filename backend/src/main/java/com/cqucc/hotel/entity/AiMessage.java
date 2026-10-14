package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * AiMessage 实体，对应数据库表 ai_message。
 */
public class AiMessage {

    /** 主键 */
    private Long id;
    /** 会话 */
    private Long conversationId;
    /** 角色 */
    private String role;
    /** 消息内容 */
    private String content;
    /** 引用来源（JSON） */
    private String citations;
    /** 工具调用轨迹（JSON） */
    private String toolTrace;
    /** 模型名称 */
    private String model;
    /** 提示词版本 */
    private String promptVersion;
    /** 耗时(ms) */
    private Integer latencyMs;
    /** 是否降级 */
    private Integer degraded;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public String getToolTrace() {
        return toolTrace;
    }

    public void setToolTrace(String toolTrace) {
        this.toolTrace = toolTrace;
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

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getDegraded() {
        return degraded;
    }

    public void setDegraded(Integer degraded) {
        this.degraded = degraded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}