package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.Staff;
import com.cqucc.hotel.entity.SysUser;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工与账号管理接口（仅管理员）。
 * <p>/api/staff/** 与 /api/users/** 均由拦截器限制为管理员。</p>
 */
@RestController
@RequestMapping("/api")
public class StaffController {

    private final AuthService authService;
    private final BusinessDao businessDao;

    public StaffController(AuthService authService, BusinessDao businessDao) {
        this.authService = authService;
        this.businessDao = businessDao;
    }

    // ---------------- 员工档案 ----------------

    @GetMapping("/staff/list")
    public Result<List<Staff>> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword) {
        return Result.ok(businessDao.listStaff(status, keyword));
    }

    /** 新增员工账号（同时建立员工档案）。 */
    @PostMapping("/staff/save")
    public Result<Long> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long id = authService.createStaff(
                String.valueOf(body.get("username")),
                body.get("password") == null ? null : String.valueOf(body.get("password")),
                body.get("realName") == null ? null : String.valueOf(body.get("realName")),
                body.get("role") == null ? "FRONTDESK" : String.valueOf(body.get("role")),
                body.get("phone") == null ? null : String.valueOf(body.get("phone")),
                body.get("gender") == null ? null : String.valueOf(body.get("gender")),
                body.get("position") == null ? null : String.valueOf(body.get("position")),
                body.get("workShift") == null ? null : String.valueOf(body.get("workShift")),
                body.get("intro") == null ? null : String.valueOf(body.get("intro")),
                request);
        return Result.ok("员工账号创建成功", id);
    }

    /** 修改员工档案（岗位、班次、状态）。 */
    @PutMapping("/staff/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (body.get("position") != null) {
            values.put("position", body.get("position"));
        }
        if (body.get("work_shift") != null) {
            values.put("work_shift", body.get("work_shift"));
        }
        if (body.get("intro") != null) {
            values.put("intro", body.get("intro"));
        }
        if (body.get("status") != null) {
            values.put("status", body.get("status"));
        }
        if (values.isEmpty()) {
            throw new BusinessException("没有需要更新的字段");
        }
        businessDao.updateStaff(id, values);
        return Result.ok("员工档案已更新", null);
    }

    // ---------------- 账号 ----------------

    @GetMapping("/users/list")
    public Result<List<SysUser>> users(@RequestParam(required = false) String role) {
        return Result.ok(authService.listUsers(role));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        authService.updateStatus(id, body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status"))));
        return Result.ok("账号状态已更新", null);
    }

    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        authService.resetPassword(id, body.get("newPassword") == null ? null : String.valueOf(body.get("newPassword")));
        return Result.ok("口令已重置", null);
    }

    /** 当前登录用户（供前端刷新页面时恢复登录态）。 */
    @GetMapping("/users/me")
    public Result<LoginUser> me(HttpServletRequest request) {
        return Result.ok(AuthInterceptor.currentUser(request));
    }
}
