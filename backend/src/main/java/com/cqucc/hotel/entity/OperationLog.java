package com.cqucc.hotel.entity;

import java.time.LocalDateTime;

/**
 * OperationLog 实体，对应数据库表 operation_log。
 */
public class OperationLog {

    /** 主键 */
    private Long id;
    /** 操作人 */
    private Long userId;
    /** 操作账号 */
    private String username;
    /** 模块 */
    private String module;
    /** 动作 */
    private String action;
    /** 详情 */
    private String detail;
    /** 来源 IP */
    private String ip;
    /** 是否成功 */
    private Integer success;
    /** 创建时间 */
    private LocalDateTime createdAt;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}