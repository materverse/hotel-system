package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用路由转发。
 * <p>前端打包产物放在 resources/static 下，Vue Router 使用 history 模式时，
 * 直接刷新 /admin、/rooms 等路径会命中后端；这里把非 /api 前缀且无静态资源的请求
 * 统一转发到 index.html，由前端路由接管。</p>
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/index", "/login", "/register", "/home", "/rooms", "/ai",
            "/guest/**", "/admin/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
