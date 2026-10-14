package com.cqucc.hotel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RatePlan 实体，对应数据库表 rate_plan。
 */
public class RatePlan {

    /** 主键 */
    private Long id;
    /** 房型 */
    private Long typeId;
    /** 策略名称 */
    private String planName;
    /** 生效开始日期 */
    private LocalDate startDate;
    /** 生效结束日期 */
    private LocalDate endDate;
    /** 该区间单价（分/晚） */
    private Integer priceCents;
    /** ACTIVE/DISABLED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：房型名称 */
    private String typeName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
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

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

}