package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.security.TokenStore;
import com.cqucc.hotel.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：登录、客人自助注册、退出、当前用户、修改口令。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenStore tokenStore;

    public AuthController(AuthService authService, TokenStore tokenStore) {
        this.authService = authService;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        AuthService.LoginResult result = authService.login(body.get("username"), body.get("password"), request);
        return Result.ok("登录成功", toData(result));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body, HttpServletRequest request) {
        AuthService.LoginResult result = authService.register(body.get("username"), body.get("password"),
                body.get("realName"), body.get("phone"), body.get("gender"), body.get("idCard"),
                body.get("birthday"), request);
        return Result.ok("注册成功，已为你建立客人档案", toData(result));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = AuthInterceptor.resolveToken(request);
        LoginUser user = tokenStore.get(token);
        authService.logout(token, user == null ? null : user.getUserId());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        return Result.ok(toData(AuthInterceptor.currentUser(request)));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        authService.changePassword(user.getUserId(), body.get("oldPassword"), body.get("newPassword"));
        return Result.ok("口令修改成功，请重新登录", null);
    }

    private Map<String, Object> toData(AuthService.LoginResult result) {
        Map<String, Object> data = toData(result.user());
        data.put("token", result.token());
        data.put("guestId", result.guestId());
        data.put("staffId", result.staffId());
        return data;
    }

    private Map<String, Object> toData(LoginUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole());
        data.put("guestId", user.getGuestId());
        data.put("staffId", user.getStaffId());
        return data;
    }
}
