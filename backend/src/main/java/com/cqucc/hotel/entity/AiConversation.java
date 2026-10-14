package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * AiConversation 实体，对应数据库表 ai_conversation。
 */
public class AiConversation {

    /** 主键 */
    private Long id;
    /** 会话号 */
    private String convNo;
    /** 所属用户 */
    private Long userId;
    /** 场景 */
    private String scene;
    /** 会话标题 */
    private String title;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConvNo() {
        return convNo;
    }

    public void setConvNo(String convNo) {
        this.convNo = convNo;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}