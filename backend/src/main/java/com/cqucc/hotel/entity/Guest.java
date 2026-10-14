package com.cqucc.hotel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Guest 实体，对应数据库表 guest。
 */
public class Guest {

    /** 主键 */
    private Long id;
    /** 关联登录账号 */
    private Long userId;
    /** 客人编号 */
    private String guestNo;
    /** 姓名 */
    private String realName;
    /** 性别 */
    private String gender;
    /** 手机号 */
    private String phone;
    /** 证件号 */
    private String idCard;
    /** 证件类型 */
    private String idType;
    /** 生日 */
    private LocalDate birthday;
    /** 联系地址 */
    private String address;
    /** 等级 NORMAL/SILVER/GOLD */
    private String memberLevel;
    /** 预存押金余额（分） */
    private Integer balanceCents;
    /** 积分 */
    private Integer points;
    /** 累计入住次数 */
    private Integer totalStayCount;
    /** 累计消费（分） */
    private Integer totalConsumeCents;
    /** 备注 */
    private String remark;
    /** NORMAL/BLACKLIST */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 展示用：脱敏证件号 */
    private String maskedIdCard;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGuestNo() {
        return guestNo;
    }

    public void setGuestNo(String guestNo) {
        this.guestNo = guestNo;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(String memberLevel) {
        this.memberLevel = memberLevel;
    }

    public Integer getBalanceCents() {
        return balanceCents;
    }

    public void setBalanceCents(Integer balanceCents) {
        this.balanceCents = balanceCents;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Integer getTotalStayCount() {
        return totalStayCount;
    }

    public void setTotalStayCount(Integer totalStayCount) {
        this.totalStayCount = totalStayCount;
    }

    public Integer getTotalConsumeCents() {
        return totalConsumeCents;
    }

    public void setTotalConsumeCents(Integer totalConsumeCents) {
        this.totalConsumeCents = totalConsumeCents;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public String getMaskedIdCard() {
        return maskedIdCard;
    }

    public void setMaskedIdCard(String maskedIdCard) {
        this.maskedIdCard = maskedIdCard;
    }

}