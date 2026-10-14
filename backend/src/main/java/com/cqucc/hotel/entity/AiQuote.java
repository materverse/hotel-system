package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * AiQuote 实体，对应数据库表 ai_quote。
 */
public class AiQuote {

    /** 主键 */
    private Long id;
    /** 关联客人 */
    private Long guestId;
    /** 报价单名称 */
    private String title;
    /** 房型 */
    private Long typeId;
    /** 房间数 */
    private Integer roomCount;
    /** 间夜数 */
    private Integer nights;
    /** 结构化报价（JSON） */
    private String quoteJson;
    /** 提示信息 */
    private String riskNotice;
    /** DRAFT/CONFIRMED/REJECTED */
    private String status;
    /** 确认人 */
    private String confirmedBy;
    /** 确认时间 */
    private LocalDateTime confirmedAt;
    /** 生成模型 */
    private String model;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGuestId() {
        return guestId;
    }

    public void setGuestId(Long guestId) {
        this.guestId = guestId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Integer getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(Integer roomCount) {
        this.roomCount = roomCount;
    }

    public Integer getNights() {
        return nights;
    }

    public void setNights(Integer nights) {
        this.nights = nights;
    }

    public String getQuoteJson() {
        return quoteJson;
    }

    public void setQuoteJson(String quoteJson) {
        this.quoteJson = quoteJson;
    }

    public String getRiskNotice() {
        return riskNotice;
    }

    public void setRiskNotice(String riskNotice) {
        this.riskNotice = riskNotice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}