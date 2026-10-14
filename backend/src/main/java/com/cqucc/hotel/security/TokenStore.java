package com.cqucc.hotel.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录令牌仓库。
 * 说明：课程设计单机部署场景下使用内存会话，令牌 12 小时过期；
 *      生产环境可替换为 Redis 或 JWT 实现，接口保持不变。
 */
@Component
public class TokenStore {

    private static final long EXPIRE_MILLIS = 12 * 60 * 60 * 1000L;

    private static class Session {
        LoginUser user;
        long expireAt;
        long lastAccessAt;
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Session session = new Session();
        session.user = user;
        session.expireAt = System.currentTimeMillis() + EXPIRE_MILLIS;
        session.lastAccessAt = System.currentTimeMillis();
        sessions.put(token, session);
        return token;
    }

    public LoginUser get(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expireAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        session.lastAccessAt = System.currentTimeMillis();
        return session.user;
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** 用户信息变更（如角色、客人绑定）后刷新会话中的用户。 */
    public void refresh(LoginUser user) {
        sessions.forEach((token, session) -> {
            if (session.user.getUserId().equals(user.getUserId())) {
                session.user = user;
            }
        });
    }

    public int onlineCount() {
        long now = System.currentTimeMillis();
        return (int) sessions.values().stream().filter(s -> s.expireAt > now).count();
    }

    public void cleanExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }
}
