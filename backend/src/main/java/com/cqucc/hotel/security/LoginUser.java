package com.cqucc.hotel.security;

/**
 * 登录态用户信息（从令牌解析得到，随请求传递）。
 * <p>角色：ADMIN 管理员、FRONTDESK 前台、HOUSEKEEPING 客房服务、GUEST 客人。</p>
 */
public class LoginUser {

    private Long userId;
    private String username;
    private String realName;
    private String role;
    /** 客人账号绑定的客人档案 ID（角色为 GUEST 时有值）。 */
    private Long guestId;
    /** 员工账号绑定的员工档案 ID（前台/客房服务有值）。 */
    private Long staffId;

    public LoginUser() {
    }

    public LoginUser(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isFrontDesk() {
        return "FRONTDESK".equals(role);
    }

    public boolean isHousekeeping() {
        return "HOUSEKEEPING".equals(role);
    }

    public boolean isGuest() {
        return "GUEST".equals(role);
    }

    /** 是否属于酒店内部员工（管理员 / 前台 / 客房服务）。 */
    public boolean isStaff() {
        return isAdmin() || isFrontDesk() || isHousekeeping();
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

    public Long getGuestId() {
        return guestId;
    }

    public void setGuestId(Long guestId) {
        this.guestId = guestId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }
}
