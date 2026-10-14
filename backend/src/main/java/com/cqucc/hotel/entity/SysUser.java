package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * SysUser 实体，对应数据库表 sys_user。
 */
public class SysUser {

    /** 主键 */
    private Long id;
    /** 登录账号 */
    private String username;
    /** 口令散列 */
    private String passwordHash;
    /** 口令盐值 */
    private String salt;
    /** 真实姓名 */
    private String realName;
    /** 角色 ADMIN/FRONTDESK/HOUSEKEEPING/GUEST */
    private String role;
    /** 手机号 */
    private String phone;
    /** 1 启用 0 禁用 */
    private Integer status;
    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}