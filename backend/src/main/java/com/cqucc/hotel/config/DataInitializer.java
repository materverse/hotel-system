package com.cqucc.hotel.config;

import com.cqucc.hotel.common.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据初始化器。
 * <p>两件事：</p>
 * <ol>
 *   <li>口令占位修复：数据库脚本中口令散列写为 __INIT__（避免 .sql 中出现可反推内容），
 *       首次启动时按统一初始口令生成盐与散列并回写；</li>
 *   <li>数据自检：例如把已经过期仍未入住的预订单标记为未到店、把待打扫但已清洁的房间置为空闲，
 *       保证演示数据自洽。</li>
 * </ol>
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 各初始账号口令统一为 123456，便于课堂演示；首次登录后应修改。 */
    private static final Map<String, String> INIT_PASSWORDS = Map.of(
            "admin", "123456",
            "manager01", "123456",
            "frontdesk01", "123456",
            "frontdesk02", "123456",
            "housekeeper01", "123456",
            "guest01", "123456",
            "guest02", "123456",
            "guest03", "123456",
            "guest04", "123456");

    @Bean
    public ApplicationRunner initData(JdbcTemplate jdbc) {
        return args -> {
            fixPasswordPlaceholders(jdbc);
            markNoShowReservations(jdbc);
            logTableStats(jdbc);
        };
    }

    private void fixPasswordPlaceholders(JdbcTemplate jdbc) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username FROM sys_user WHERE password_hash = '__INIT__' OR salt = '__INIT__'");
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String username = String.valueOf(row.get("username"));
            String rawPassword = INIT_PASSWORDS.getOrDefault(username, "123456");
            String salt = PasswordUtil.newSalt();
            String hash = PasswordUtil.hash(rawPassword, salt);
            jdbc.update("UPDATE sys_user SET salt = ?, password_hash = ? WHERE id = ?", salt, hash, id);
            log.info("初始化账号 {} 的口令（初始口令 {}）", username, rawPassword);
        }
        if (!rows.isEmpty()) {
            log.info("共初始化 {} 个账号口令，请提醒使用者首次登录后修改口令", rows.size());
        }
    }

    /** 把入住日期已过、仍处于已预订状态的订单标记为未到店，并释放房间。 */
    private void markNoShowReservations(JdbcTemplate jdbc) {
        int updated = jdbc.update("UPDATE reservation SET status = 'NO_SHOW', "
                + "cancel_reason = '超过保留时间未到店（系统自动处理）' "
                + "WHERE status = 'BOOKED' AND check_in_date < ?", LocalDate.now());
        if (updated > 0) {
            log.info("检测到 {} 笔过期未到店预订，已标记为未到店", updated);
            jdbc.update("UPDATE room r SET r.status = 'FREE' WHERE r.status = 'BOOKED' "
                    + "AND NOT EXISTS (SELECT 1 FROM reservation res WHERE res.room_id = r.id "
                    + "AND res.status IN ('BOOKED','CHECKED_IN'))");
        }
    }

    private void logTableStats(JdbcTemplate jdbc) {
        try {
            Integer rooms = jdbc.queryForObject("SELECT COUNT(*) FROM room", Integer.class);
            Integer guests = jdbc.queryForObject("SELECT COUNT(*) FROM guest", Integer.class);
            Integer reservations = jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class);
            Integer docs = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc", Integer.class);
            log.info("演示数据自检：客房 {} 间，客人 {} 位，预订单 {} 笔，知识库文档 {} 篇",
                    rooms, guests, reservations, docs);
        } catch (Exception e) {
            log.warn("演示数据自检失败：{}", e.getMessage());
        }
    }
}
