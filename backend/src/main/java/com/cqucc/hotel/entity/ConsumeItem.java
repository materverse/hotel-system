package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * ConsumeItem 实体，对应数据库表 consume_item。
 */
public class ConsumeItem {

    /** 主键 */
    private Long id;
    /** 项目名称 */
    private String itemName;
    /** 分类 */
    private String category;
    /** 单价（分） */
    private Integer priceCents;
    /** 单位 */
    private String unit;
    /** 说明 */
    private String description;
    /** ON_SALE/OFF_SALE */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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