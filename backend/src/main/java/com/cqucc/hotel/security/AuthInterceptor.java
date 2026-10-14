package com.cqucc.hotel.security;

import com.cqucc.hotel.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 登录鉴权与角色校验拦截器。
 * <p>令牌通过请求头 Authorization: Bearer xxx 传递。
 * 权限分三层：免登录白名单、酒店内部员工（管理员/前台/客房服务）、仅管理员。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_LOGIN_USER = "LOGIN_USER";

    /** 无需登录即可访问的路径前缀。 */
    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/public/",
            "/api/health"
    );

    /** 仅酒店内部员工（含前台、客房服务）可访问；客人只允许 GET 自己的数据。 */
    private static final Set<String> STAFF_ONLY = Set.of(
            "/api/room/",
            "/api/reservation/",
            "/api/guest/",
            "/api/bill/",
            "/api/front/"
    );

    /** 仅管理员可访问（员工档案、统计报表、知识库、账号权限）。 */
    private static final Set<String> ADMIN_ONLY = Set.of(
            "/api/staff",
            "/api/stats/",
            "/api/kb/",
            "/api/users/"
    );

    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String white : WHITE_LIST) {
            if (uri.startsWith(white)) {
                return true;
            }
        }

        LoginUser user = tokenStore.get(resolveToken(request));
        if (user == null) {
            throw new BusinessException(401, "登录已过期或未登录，请重新登录");
        }
        request.setAttribute(ATTR_LOGIN_USER, user);

        for (String prefix : ADMIN_ONLY) {
            if (uri.startsWith(prefix) && !user.isAdmin()) {
                throw new BusinessException(403, "该操作仅管理员可执行");
            }
        }
        for (String prefix : STAFF_ONLY) {
            if (uri.startsWith(prefix) && !user.isStaff()) {
                throw new BusinessException(403, "该操作仅酒店员工可执行，客人请使用自助功能");
            }
        }
        // 方法粒度限制：客人只能读取（GET），写操作一律需要员工角色
        String method = request.getMethod();
        boolean write = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (write && user.isGuest()) {
            for (String prefix : STAFF_ONLY) {
                if (uri.startsWith(prefix)) {
                    throw new BusinessException(403, "客人账号不能执行该写操作，请使用自助功能或联系前台");
                }
            }
        }
        return true;
    }

    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return request.getHeader("X-Token");
    }

    /** 供 Controller 便捷获取当前登录用户。 */
    public static LoginUser currentUser(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_LOGIN_USER);
        if (attr instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(401, "未获取到登录信息，请重新登录");
    }
}
