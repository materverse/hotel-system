package com.cqucc.hotel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号生成器。
 * <p>格式：前缀 + yyyyMMdd + 4 位当日流水，例如 RS202610100001。</p>
 * <p>关键点：进程内计数器在应用重启后会归零，若直接从头开始就会与数据库中已存在的单号冲突。
 * 因此首次使用某前缀时先从数据库读取当天最大流水号作为起点，保证单号全局唯一；
 * 数据库层另有唯一索引兜底。</p>
 */
@Component
public class BizNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 前缀 → 表名 + 单号列名。RS 预订、BL 账单、TX 流水、QT 报价单、GT 客人编号。 */
    private static final Map<String, String[]> PREFIX_TABLE = Map.of(
            "RS", new String[]{"reservation", "reservation_no"},
            "BL", new String[]{"bill", "bill_no"},
            "TX", new String[]{"guest_transaction", "tx_no"},
            "QT", new String[]{"ai_quote", "title"});

    private final JdbcTemplate jdbc;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> seeded = new HashMap<>();

    public BizNoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized String next(String prefix) {
        String day = LocalDate.now().format(DAY);
        String key = prefix + day;
        seedFromDatabaseIfNeeded(prefix, day, key);
        int seq = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (counters.size() > 60) {
            counters.keySet().removeIf(k -> !k.endsWith(day));
            seeded.keySet().removeIf(k -> !k.endsWith(day));
        }
        return key + String.format("%04d", seq);
    }

    private void seedFromDatabaseIfNeeded(String prefix, String day, String key) {
        if (seeded.containsKey(key)) {
            return;
        }
        int start = 0;
        String[] table = PREFIX_TABLE.get(prefix);
        if (table != null) {
            try {
                String maxNo = jdbc.queryForObject(
                        "SELECT MAX(" + table[1] + ") FROM " + table[0] + " WHERE " + table[1] + " LIKE ?",
                        String.class, key + "%");
                if (maxNo != null && maxNo.length() >= key.length() + 4) {
                    start = Integer.parseInt(maxNo.substring(key.length(), key.length() + 4));
                }
            } catch (Exception e) {
                start = 0;
            }
        }
        counters.put(key, new AtomicInteger(start));
        seeded.put(key, Boolean.TRUE);
    }
}
