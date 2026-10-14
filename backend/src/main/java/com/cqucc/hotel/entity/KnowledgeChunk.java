package com.cqucc.hotel.entity;

import java.math.BigDecimal;

/**
 * KnowledgeChunk 实体，对应数据库表 knowledge_chunk。
 */
public class KnowledgeChunk {

    /** 主键 */
    private Long id;
    /** 所属文档 */
    private Long docId;
    /** 片段序号 */
    private Integer chunkIndex;
    /** 片段正文 */
    private String content;
    /** 片段字数 */
    private Integer tokenCount;
    /** TF-IDF 向量（JSON） */
    private String vectorJson;
    /** 向量模长 */
    private BigDecimal vectorNorm;
    /** 关联查询：文档标题 */
    private String docTitle;
    /** 关联查询：资料来源 */
    private String docSource;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getVectorJson() {
        return vectorJson;
    }

    public void setVectorJson(String vectorJson) {
        this.vectorJson = vectorJson;
    }

    public BigDecimal getVectorNorm() {
        return vectorNorm;
    }

    public void setVectorNorm(BigDecimal vectorNorm) {
        this.vectorNorm = vectorNorm;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public String getDocSource() {
        return docSource;
    }

    public void setDocSource(String docSource) {
        this.docSource = docSource;
    }

}