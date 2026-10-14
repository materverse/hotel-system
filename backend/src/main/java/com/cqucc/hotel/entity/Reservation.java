package com.cqucc.hotel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reservation 实体，对应数据库表 reservation。
 */
public class Reservation {

    /** 主键 */
    private Long id;
    /** 预订单号 */
    private String reservationNo;
    /** 客人 */
    private Long guestId;
    /** 房间 */
    private Long roomId;
    /** 房型 */
    private Long typeId;
    /** 入住日期 */
    private LocalDate checkInDate;
    /** 离店日期 */
    private LocalDate checkOutDate;
    /** 间夜数 */
    private Integer nights;
    /** 房间数 */
    private Integer roomCount;
    /** 入住人数 */
    private Integer guestCount;
    /** 成交单价（分/晚） */
    private Integer unitPriceCents;
    /** 订单总额（分） */
    private Integer totalCents;
    /** 已收押金（分） */
    private Integer depositCents;
    /** 渠道 */
    private String channel;
    /** BOOKED/CHECKED_IN/CHECKED_OUT/CANCELLED/NO_SHOW */
    private String status;
    /** 特殊要求 */
    private String specialRequest;
    /** 创建人 */
    private String createdBy;
    /** 实际入住时间 */
    private LocalDateTime checkInAt;
    /** 实际退房时间 */
    private LocalDateTime checkOutAt;
    /** 取消原因 */
    private String cancelReason;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：客人姓名 */
    private String guestName;
    /** 关联查询：客人手机号 */
    private String guestPhone;
    /** 关联查询：房间号 */
    private String roomNo;
    /** 关联查询：房型名称 */
    private String typeName;
    /** 关联查询：账单应付（分） */
    private Integer billTotalCents;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public Long getGuestId() {
        return guestId;
    }

    public void setGuestId(Long guestId) {
        this.guestId = guestId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public Integer getNights() {
        return nights;
    }

    public void setNights(Integer nights) {
        this.nights = nights;
    }

    public Integer getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(Integer roomCount) {
        this.roomCount = roomCount;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public Integer getUnitPriceCents() {
        return unitPriceCents;
    }

    public void setUnitPriceCents(Integer unitPriceCents) {
        this.unitPriceCents = unitPriceCents;
    }

    public Integer getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Integer totalCents) {
        this.totalCents = totalCents;
    }

    public Integer getDepositCents() {
        return depositCents;
    }

    public void setDepositCents(Integer depositCents) {
        this.depositCents = depositCents;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSpecialRequest() {
        return specialRequest;
    }

    public void setSpecialRequest(String specialRequest) {
        this.specialRequest = specialRequest;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(LocalDateTime checkInAt) {
        this.checkInAt = checkInAt;
    }

    public LocalDateTime getCheckOutAt() {
        return checkOutAt;
    }

    public void setCheckOutAt(LocalDateTime checkOutAt) {
        this.checkOutAt = checkOutAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(String guestPhone) {
        this.guestPhone = guestPhone;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getBillTotalCents() {
        return billTotalCents;
    }

    public void setBillTotalCents(Integer billTotalCents) {
        this.billTotalCents = billTotalCents;
    }

}