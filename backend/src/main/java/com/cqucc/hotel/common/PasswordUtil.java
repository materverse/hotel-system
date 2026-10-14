package com.cqucc.hotel.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 口令工具：加盐 SHA-256 迭代散列，避免数据库中保存明文口令。
 * 说明：课程设计场景下不引入额外的加密依赖，算法固定为 SHA-256 + 1024 次迭代。
 */
public final class PasswordUtil {

    private static final int ITERATIONS = 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** 生成 16 字节随机盐（Base64 无填充，22 字符）。 */
    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    /** 计算口令散列。 */
    public static String hash(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] current = (rawPassword + "#" + salt).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < ITERATIONS; i++) {
                digest.reset();
                current = digest.digest(current);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(current);
        } catch (Exception e) {
            throw new IllegalStateException("口令散列计算失败", e);
        }
    }

    /** 校验口令。 */
    public static boolean matches(String rawPassword, String salt, String expectedHash) {
        if (rawPassword == null || salt == null || expectedHash == null) {
            return false;
        }
        return constantTimeEquals(hash(rawPassword, salt), expectedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
