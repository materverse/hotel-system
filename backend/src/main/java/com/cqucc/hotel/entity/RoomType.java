package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * RoomType 实体，对应数据库表 room_type。
 */
public class RoomType {

    /** 主键 */
    private Long id;
    /** 房型名称 */
    private String typeName;
    /** 床型 */
    private String bedType;
    /** 面积（平方米） */
    private Integer areaSqm;
    /** 可住人数 */
    private Integer capacity;
    /** 门市价（分/晚） */
    private Integer priceCents;
    /** 会员价（分/晚） */
    private Integer memberPriceCents;
    /** 配套设施 */
    private String facilities;
    /** 房型说明 */
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

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getBedType() {
        return bedType;
    }

    public void setBedType(String bedType) {
        this.bedType = bedType;
    }

    public Integer getAreaSqm() {
        return areaSqm;
    }

    public void setAreaSqm(Integer areaSqm) {
        this.areaSqm = areaSqm;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getMemberPriceCents() {
        return memberPriceCents;
    }

    public void setMemberPriceCents(Integer memberPriceCents) {
        this.memberPriceCents = memberPriceCents;
    }

    public String getFacilities() {
        return facilities;
    }

    public void setFacilities(String facilities) {
        this.facilities = facilities;
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