package com.cqucc.hotel.dao;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用数据访问基类。
 * <p>封装基于 JdbcTemplate 的单表增删改查，避免每个实体重复编写样板代码；
 * 复杂查询（多表关联、统计）在 {@link BusinessDao} 中用显式 SQL 实现，保证 SQL 可读可审查。</p>
 *
 * @param <T> 实体类型
 */
public class CrudDao<T> {

    /** MySQL 保留字列名，生成 SQL 时需要用反引号包裹。 */
    private static final List<String> RESERVED = List.of("action", "status_key", "desc", "key", "order", "group");

    protected final JdbcTemplate jdbc;
    private final String table;
    private final Class<T> type;
    private final RowMapper<T> mapper;

    public CrudDao(JdbcTemplate jdbc, String table, Class<T> type) {
        this.jdbc = jdbc;
        this.table = table;
        this.type = type;
        BeanPropertyRowMapper<T> bp = new BeanPropertyRowMapper<>(type);
        bp.setPrimitivesDefaultedForNullValue(true);
        this.mapper = bp;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public String table() {
        return table;
    }

    public RowMapper<T> mapper() {
        return mapper;
    }

    /** 驼峰转下划线列名。 */
    public static String column(String field) {
        StringBuilder sb = new StringBuilder();
        for (char c : field.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        String col = sb.toString();
        return RESERVED.contains(col) ? "`" + col + "`" : col;
    }

    public T findById(Long id) {
        List<T> list = jdbc.query("SELECT * FROM " + table + " WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<T> findList(Map<String, Object> conditions) {
        return findList(conditions, null, null, null);
    }

    public List<T> findList(Map<String, Object> conditions, String orderBy, Integer limit, Integer offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<Object> args = new ArrayList<>();
        appendWhere(sql, conditions, args);
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        if (limit != null) {
            sql.append(" LIMIT ?").append(offset != null ? " OFFSET ?" : "");
            args.add(limit);
            if (offset != null) {
                args.add(offset);
            }
        }
        return jdbc.query(sql.toString(), mapper, args.toArray());
    }

    public long count(Map<String, Object> conditions) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
        List<Object> args = new ArrayList<>();
        appendWhere(sql, conditions, args);
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0L : c;
    }

    /** 条件为等值匹配；值为 Query 对象时使用自定义运算符。 */
    private void appendWhere(StringBuilder sql, Map<String, Object> conditions, List<Object> args) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> e : conditions.entrySet()) {
            String col = column(e.getKey());
            Object v = e.getValue();
            if (v instanceof Query q) {
                parts.add(col + " " + q.op() + " ?");
                args.add(q.value());
            } else if (v == null) {
                parts.add(col + " IS NULL");
            } else {
                parts.add(col + " = ?");
                args.add(v);
            }
        }
        sql.append(" WHERE ").append(String.join(" AND ", parts));
    }

    /** 自定义条件，例如 like / in / >=。 */
    public record Query(String op, Object value) {
    }

    public int insert(Map<String, Object> values) {
        List<String> cols = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            cols.add(column(e.getKey()));
            args.add(e.getValue());
        }
        String sql = "INSERT INTO " + table + " (" + String.join(", ", cols) + ") VALUES ("
                + "?,".repeat(cols.size()).replaceAll(",$", "") + ")";
        return jdbc.update(sql, args.toArray());
    }

    /** 插入并返回自增主键。 */
    public Long insertReturnKey(Map<String, Object> values) {
        List<String> cols = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            cols.add(column(e.getKey()));
            args.add(e.getValue());
        }
        String sql = "INSERT INTO " + table + " (" + String.join(", ", cols) + ") VALUES ("
                + "?,".repeat(cols.size()).replaceAll(",$", "") + ")";
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }

    public int updateById(Long id, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(column(e.getKey())).append(" = ?");
            args.add(e.getValue());
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        return jdbc.update(sql.toString(), args.toArray());
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
    }

    public int delete(Map<String, Object> conditions) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        List<Object> args = new ArrayList<>();
        appendWhere(sql, conditions, args);
        return jdbc.update(sql.toString(), args.toArray());
    }

    /** 便捷构造条件 Map。 */
    public static Map<String, Object> cond(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    public static Query like(Object value) {
        return new Query("LIKE", value);
    }

    public static Query ge(Object value) {
        return new Query(">=", value);
    }

    public static Query le(Object value) {
        return new Query("<=", value);
    }

    public Class<T> type() {
        return type;
    }
}
