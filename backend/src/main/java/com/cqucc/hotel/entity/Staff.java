package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * Staff 实体，对应数据库表 staff。
 */
public class Staff {

    /** 主键 */
    private Long id;
    /** 关联 sys_user.id */
    private Long userId;
    /** 工号 */
    private String staffNo;
    /** 性别 */
    private String gender;
    /** 岗位 */
    private String position;
    /** 班次 */
    private String workShift;
    /** 备注 */
    private String intro;
    /** ON_DUTY/REST/LEFT */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 关联查询：姓名 */
    private String realName;
    /** 关联查询：手机号 */
    private String phone;

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

    public String getStaffNo() {
        return staffNo;
    }

    public void setStaffNo(String staffNo) {
        this.staffNo = staffNo;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getWorkShift() {
        return workShift;
    }

    public void setWorkShift(String workShift) {
        this.workShift = workShift;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
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

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

}