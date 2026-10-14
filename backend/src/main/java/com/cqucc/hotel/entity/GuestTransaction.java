package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * GuestTransaction 实体，对应数据库表 guest_transaction。
 */
public class GuestTransaction {

    /** 主键 */
    private Long id;
    /** 客人 */
    private Long guestId;
    /** 流水号 */
    private String txNo;
    /** DEPOSIT/CONSUME/REFUND/ADJUST */
    private String txType;
    /** 发生金额（分） */
    private Integer amountCents;
    /** 发生后的预存余额（分） */
    private Integer balanceAfter;
    /** 业务类型 */
    private String bizType;
    /** 关联业务主键 */
    private Long bizId;
    /** 操作人 */
    private String operator;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：客人姓名 */
    private String guestName;

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

    public String getTxNo() {
        return txNo;
    }

    public void setTxNo(String txNo) {
        this.txNo = txNo;
    }

    public String getTxType() {
        return txType;
    }

    public void setTxType(String txType) {
        this.txType = txType;
    }

    public Integer getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Integer amountCents) {
        this.amountCents = amountCents;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

}