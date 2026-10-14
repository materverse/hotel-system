package com.cqucc.hotel.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bill 实体，对应数据库表 bill。
 */
public class Bill {

    /** 主键 */
    private Long id;
    /** 账单号 */
    private String billNo;
    /** 预订 */
    private Long reservationId;
    /** 客人 */
    private Long guestId;
    /** 房费小计（分） */
    private Integer roomChargeCents;
    /** 消费小计（分） */
    private Integer consumeChargeCents;
    /** 优惠金额（分） */
    private Integer discountCents;
    /** 押金抵扣（分） */
    private Integer depositCents;
    /** 应收合计（分） */
    private Integer totalCents;
    /** 已收金额（分） */
    private Integer paidCents;
    /** 退还押金（分） */
    private Integer refundCents;
    /** 结算方式 */
    private String payType;
    /** UNSETTLED/SETTLED/VOID */
    private String status;
    /** 结算操作人 */
    private String settledBy;
    /** 结算时间 */
    private LocalDateTime settledAt;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：客人姓名 */
    private String guestName;
    /** 关联查询：预订单号 */
    private String reservationNo;
    /** 关联查询：房间号 */
    private String roomNo;
    /** 关联：账单明细（非表字段，由服务层填充） */
    private List<BillItem> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getGuestId() {
        return guestId;
    }

    public void setGuestId(Long guestId) {
        this.guestId = guestId;
    }

    public Integer getRoomChargeCents() {
        return roomChargeCents;
    }

    public void setRoomChargeCents(Integer roomChargeCents) {
        this.roomChargeCents = roomChargeCents;
    }

    public Integer getConsumeChargeCents() {
        return consumeChargeCents;
    }

    public void setConsumeChargeCents(Integer consumeChargeCents) {
        this.consumeChargeCents = consumeChargeCents;
    }

    public Integer getDiscountCents() {
        return discountCents;
    }

    public void setDiscountCents(Integer discountCents) {
        this.discountCents = discountCents;
    }

    public Integer getDepositCents() {
        return depositCents;
    }

    public void setDepositCents(Integer depositCents) {
        this.depositCents = depositCents;
    }

    public Integer getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Integer totalCents) {
        this.totalCents = totalCents;
    }

    public Integer getPaidCents() {
        return paidCents;
    }

    public void setPaidCents(Integer paidCents) {
        this.paidCents = paidCents;
    }

    public Integer getRefundCents() {
        return refundCents;
    }

    public void setRefundCents(Integer refundCents) {
        this.refundCents = refundCents;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettledBy() {
        return settledBy;
    }

    public void setSettledBy(String settledBy) {
        this.settledBy = settledBy;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
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

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public List<BillItem> getItems() {
        return items;
    }

    public void setItems(List<BillItem> items) {
        this.items = items;
    }

}