package com.cqucc.hotel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Room 实体，对应数据库表 room。
 */
public class Room {

    /** 主键 */
    private Long id;
    /** 房间号 */
    private String roomNo;
    /** 楼层 */
    private Integer floorNo;
    /** 房型 */
    private Long typeId;
    /** 朝向 */
    private String orientation;
    /** 房态 FREE/OCCUPIED/CLEANING/MAINTENANCE/BOOKED */
    private String status;
    /** 清洁状态 CLEAN/DIRTY */
    private String cleanStatus;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：房型名称 */
    private String typeName;
    /** 关联查询：门市价 */
    private Integer priceCents;
    /** 关联查询：会员价 */
    private Integer memberPriceCents;
    /** 关联查询：当前入住客人 */
    private String guestName;
    /** 关联查询：当前订单 */
    private Long currentReservationId;
    /** 关联查询：预计离店日期 */
    private LocalDate currentCheckOutDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public Integer getFloorNo() {
        return floorNo;
    }

    public void setFloorNo(Integer floorNo) {
        this.floorNo = floorNo;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCleanStatus() {
        return cleanStatus;
    }

    public void setCleanStatus(String cleanStatus) {
        this.cleanStatus = cleanStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public Long getCurrentReservationId() {
        return currentReservationId;
    }

    public void setCurrentReservationId(Long currentReservationId) {
        this.currentReservationId = currentReservationId;
    }

    public LocalDate getCurrentCheckOutDate() {
        return currentCheckOutDate;
    }

    public void setCurrentCheckOutDate(LocalDate currentCheckOutDate) {
        this.currentCheckOutDate = currentCheckOutDate;
    }

}