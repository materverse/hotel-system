package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * KnowledgeDoc 实体，对应数据库表 knowledge_doc。
 */
public class KnowledgeDoc {

    /** 主键 */
    private Long id;
    /** 文档标题 */
    private String title;
    /** 分类 */
    private String category;
    /** 正文 */
    private String content;
    /** 资料来源 */
    private String source;
    /** 切分片段数 */
    private Integer chunkCount;
    /** READY/DISABLED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}