package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（无需登录），用于启动自检与部署验证。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("service", "城科酒店管理系统");
        map.put("time", LocalDateTime.now().toString());
        return Result.ok(map);
    }
}
