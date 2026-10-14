package com.cqucc.hotel.dao;

import com.cqucc.hotel.entity.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务查询数据访问：多表关联查询与统计报表。
 * <p>本类只做查询（以及与业务强相关的锁行查询），写操作由各 Service 通过 CrudDao 完成，
 * 目的是让关键 SQL 集中可见，便于审查与在报告中引用。</p>
 */
public class BusinessDao {

    private final JdbcTemplate jdbc;

    public BusinessDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 客房 + 房型 + 当前在住客人（用于房态图与客房列表）。 */
    private static final String ROOM_JOIN =
            "SELECT r.*, t.type_name, t.price_cents, t.member_price_cents, "
                    + "g.real_name AS guest_name, res.id AS current_reservation_id, res.check_out_date AS current_check_out_date "
                    + "FROM room r "
                    + "JOIN room_type t ON t.id = r.type_id "
                    + "LEFT JOIN reservation res ON res.room_id = r.id AND res.status = 'CHECKED_IN' "
                    + "LEFT JOIN guest g ON g.id = res.guest_id";

    private static final String RESERVATION_JOIN =
            "SELECT res.*, g.real_name AS guest_name, g.phone AS guest_phone, r.room_no, t.type_name, "
                    + "(SELECT b.total_cents FROM bill b WHERE b.reservation_id = res.id) AS bill_total_cents "
                    + "FROM reservation res "
                    + "JOIN guest g ON g.id = res.guest_id "
                    + "JOIN room_type t ON t.id = res.type_id "
                    + "LEFT JOIN room r ON r.id = res.room_id";

    // ------------------------------------------------------------------
    // 客人
    // ------------------------------------------------------------------

    public List<Guest> searchGuests(String keyword, String level, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM guest WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (real_name LIKE ? OR phone LIKE ? OR guest_no LIKE ? OR id_card LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND member_level = ?");
            args.add(level);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(Guest.class), args.toArray());
    }

    public long countGuests(String keyword, String level) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM guest WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (real_name LIKE ? OR phone LIKE ? OR guest_no LIKE ? OR id_card LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND member_level = ?");
            args.add(level);
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    public Guest findGuestById(Long guestId) {
        List<Guest> list = jdbc.query("SELECT * FROM guest WHERE id = ?", new BeanPropertyRowMapper<>(Guest.class), guestId);
        return list.isEmpty() ? null : list.get(0);
    }

    public Guest findGuestByUserId(Long userId) {
        List<Guest> list = jdbc.query("SELECT * FROM guest WHERE user_id = ?", new BeanPropertyRowMapper<>(Guest.class), userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public Guest findGuestByPhone(String phone) {
        List<Guest> list = jdbc.query("SELECT * FROM guest WHERE phone = ?", new BeanPropertyRowMapper<>(Guest.class), phone);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 悲观锁读取客人（用于押金扣减，避免并发超扣）。 */
    public Guest lockGuest(Long guestId) {
        List<Guest> list = jdbc.query("SELECT * FROM guest WHERE id = ? FOR UPDATE",
                new BeanPropertyRowMapper<>(Guest.class), guestId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ------------------------------------------------------------------
    // 房型与客房
    // ------------------------------------------------------------------

    public List<RoomType> listRoomTypes(String status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT * FROM room_type WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (type_name LIKE ? OR bed_type LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY price_cents");
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(RoomType.class), args.toArray());
    }

    public RoomType findRoomTypeById(Long id) {
        List<RoomType> list = jdbc.query("SELECT * FROM room_type WHERE id = ?",
                new BeanPropertyRowMapper<>(RoomType.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Room> listRooms(String status, Long typeId, Integer floorNo, String keyword) {
        StringBuilder sql = new StringBuilder(ROOM_JOIN).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            args.add(status);
        }
        if (typeId != null) {
            sql.append(" AND r.type_id = ?");
            args.add(typeId);
        }
        if (floorNo != null) {
            sql.append(" AND r.floor_no = ?");
            args.add(floorNo);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (r.room_no LIKE ? OR t.type_name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY r.room_no");
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(Room.class), args.toArray());
    }

    public Room findRoomById(Long roomId) {
        List<Room> list = jdbc.query(ROOM_JOIN + " WHERE r.id = ?", new BeanPropertyRowMapper<>(Room.class), roomId);
        return list.isEmpty() ? null : list.get(0);
    }

    public Room lockRoom(Long roomId) {
        List<Room> list = jdbc.query("SELECT * FROM room WHERE id = ? FOR UPDATE",
                new BeanPropertyRowMapper<>(Room.class), roomId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询某房型在指定日期区间内空闲的房间。
     * <p>判断标准：房间本身不是维修状态，且没有与该区间重叠的有效预订（已预订/已入住）。</p>
     */
    public List<Room> findAvailableRooms(Long typeId, LocalDate checkIn, LocalDate checkOut, int limit) {
        String sql = ROOM_JOIN + " WHERE r.type_id = ? AND r.status <> 'MAINTENANCE' "
                + "AND r.id NOT IN (SELECT res.room_id FROM reservation res WHERE res.room_id IS NOT NULL "
                + "  AND res.status IN ('BOOKED','CHECKED_IN') "
                + "  AND res.check_in_date < ? AND res.check_out_date > ?) "
                + "ORDER BY r.floor_no, r.room_no LIMIT ?";
        return jdbc.query(sql, new BeanPropertyRowMapper<>(Room.class), typeId, checkOut, checkIn, limit);
    }

    /** 判断某房间在指定区间是否可售。 */
    public boolean isRoomAvailable(Long roomId, LocalDate checkIn, LocalDate checkOut, Long excludeReservationId) {
        String sql = "SELECT COUNT(*) FROM reservation WHERE room_id = ? AND status IN ('BOOKED','CHECKED_IN') "
                + "AND check_in_date < ? AND check_out_date > ?"
                + (excludeReservationId == null ? "" : " AND id <> " + excludeReservationId);
        Long c = jdbc.queryForObject(sql, Long.class, roomId, checkOut, checkIn);
        return c == null || c == 0;
    }

    /** 房型在指定日期的可售房间数（用于房价与库存展示）。 */
    public int countAvailableRooms(Long typeId, LocalDate checkIn, LocalDate checkOut) {
        return findAvailableRooms(typeId, checkIn, checkOut, 999).size();
    }

    // ------------------------------------------------------------------
    // 预订与账单
    // ------------------------------------------------------------------

    public List<Reservation> listReservations(String status, Long guestId, String keyword, LocalDate date, int page, int size) {
        StringBuilder sql = new StringBuilder(RESERVATION_JOIN).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND res.status = ?");
            args.add(status);
        }
        if (guestId != null) {
            sql.append(" AND res.guest_id = ?");
            args.add(guestId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (res.reservation_no LIKE ? OR g.real_name LIKE ? OR g.phone LIKE ? OR r.room_no LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (date != null) {
            sql.append(" AND res.check_in_date <= ? AND res.check_out_date > ?");
            args.add(date);
            args.add(date);
        }
        sql.append(" ORDER BY res.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(Reservation.class), args.toArray());
    }

    public long countReservations(String status, Long guestId, String keyword, LocalDate date) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM reservation res "
                + "JOIN guest g ON g.id = res.guest_id LEFT JOIN room r ON r.id = res.room_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND res.status = ?");
            args.add(status);
        }
        if (guestId != null) {
            sql.append(" AND res.guest_id = ?");
            args.add(guestId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (res.reservation_no LIKE ? OR g.real_name LIKE ? OR g.phone LIKE ? OR r.room_no LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (date != null) {
            sql.append(" AND res.check_in_date <= ? AND res.check_out_date > ?");
            args.add(date);
            args.add(date);
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    public Reservation findReservationById(Long id) {
        List<Reservation> list = jdbc.query(RESERVATION_JOIN + " WHERE res.id = ?",
                new BeanPropertyRowMapper<>(Reservation.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Reservation findReservationByNo(String reservationNo) {
        List<Reservation> list = jdbc.query(RESERVATION_JOIN + " WHERE res.reservation_no = ?",
                new BeanPropertyRowMapper<>(Reservation.class), reservationNo);
        return list.isEmpty() ? null : list.get(0);
    }

    public Reservation lockReservation(Long id) {
        List<Reservation> list = jdbc.query("SELECT * FROM reservation WHERE id = ? FOR UPDATE",
                new BeanPropertyRowMapper<>(Reservation.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 当日应到店 / 在住 / 应离店 的预订（前台账板使用）。 */
    public List<Reservation> todayBoard(LocalDate today) {
        String sql = RESERVATION_JOIN + " WHERE (res.status = 'BOOKED' AND res.check_in_date = ?) "
                + "OR res.status = 'CHECKED_IN' OR (res.status = 'CHECKED_OUT' AND DATE(res.check_out_at) = ?) "
                + "ORDER BY res.status, res.check_in_date";
        return jdbc.query(sql, new BeanPropertyRowMapper<>(Reservation.class), today, today);
    }

    public List<Bill> listBills(String status, Long guestId, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT b.*, g.real_name AS guest_name, res.reservation_no, r.room_no "
                + "FROM bill b JOIN guest g ON g.id = b.guest_id "
                + "JOIN reservation res ON res.id = b.reservation_id LEFT JOIN room r ON r.id = res.room_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND b.status = ?");
            args.add(status);
        }
        if (guestId != null) {
            sql.append(" AND b.guest_id = ?");
            args.add(guestId);
        }
        sql.append(" ORDER BY b.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(Bill.class), args.toArray());
    }

    public long countBills(String status, Long guestId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM bill WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (guestId != null) {
            sql.append(" AND guest_id = ?");
            args.add(guestId);
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    public Bill findBillById(Long id) {
        List<Bill> list = jdbc.query("SELECT b.*, g.real_name AS guest_name, res.reservation_no, r.room_no "
                        + "FROM bill b JOIN guest g ON g.id = b.guest_id "
                        + "JOIN reservation res ON res.id = b.reservation_id LEFT JOIN room r ON r.id = res.room_id "
                        + "WHERE b.id = ?", new BeanPropertyRowMapper<>(Bill.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Bill findBillByReservation(Long reservationId) {
        List<Bill> list = jdbc.query("SELECT b.*, g.real_name AS guest_name, res.reservation_no, r.room_no "
                        + "FROM bill b JOIN guest g ON g.id = b.guest_id "
                        + "JOIN reservation res ON res.id = b.reservation_id LEFT JOIN room r ON r.id = res.room_id "
                        + "WHERE b.reservation_id = ?", new BeanPropertyRowMapper<>(Bill.class), reservationId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<BillItem> listBillItems(Long billId) {
        return jdbc.query("SELECT * FROM bill_item WHERE bill_id = ? ORDER BY id",
                new BeanPropertyRowMapper<>(BillItem.class), billId);
    }

    public List<GuestTransaction> listGuestTransactions(Long guestId, String txType, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT t.*, g.real_name AS guest_name FROM guest_transaction t "
                + "JOIN guest g ON g.id = t.guest_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (guestId != null) {
            sql.append(" AND t.guest_id = ?");
            args.add(guestId);
        }
        if (txType != null && !txType.isBlank()) {
            sql.append(" AND t.tx_type = ?");
            args.add(txType);
        }
        sql.append(" ORDER BY t.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(GuestTransaction.class), args.toArray());
    }

    public long countGuestTransactions(Long guestId, String txType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM guest_transaction WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (guestId != null) {
            sql.append(" AND guest_id = ?");
            args.add(guestId);
        }
        if (txType != null && !txType.isBlank()) {
            sql.append(" AND tx_type = ?");
            args.add(txType);
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    // ------------------------------------------------------------------
    // 消费项目、房价策略、员工
    // ------------------------------------------------------------------

    public List<ConsumeItem> listConsumeItems(String category, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM consume_item WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY category, id");
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(ConsumeItem.class), args.toArray());
    }

    public List<RatePlan> listRatePlans(Long typeId, String status) {
        StringBuilder sql = new StringBuilder("SELECT p.*, t.type_name FROM rate_plan p "
                + "JOIN room_type t ON t.id = p.type_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (typeId != null) {
            sql.append(" AND p.type_id = ?");
            args.add(typeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY p.type_id, p.start_date");
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(RatePlan.class), args.toArray());
    }

    /** 查询某房型某天生效的房价策略（没有则返回 null，使用门市价/会员价）。 */
    public RatePlan findRatePlan(Long typeId, LocalDate date) {
        List<RatePlan> list = jdbc.query("SELECT p.*, t.type_name FROM rate_plan p JOIN room_type t ON t.id = p.type_id "
                        + "WHERE p.type_id = ? AND p.status = 'ACTIVE' AND ? BETWEEN p.start_date AND p.end_date "
                        + "ORDER BY p.price_cents DESC LIMIT 1",
                new BeanPropertyRowMapper<>(RatePlan.class), typeId, date);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Staff> listStaff(String status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT s.*, u.real_name, u.phone FROM staff s "
                + "JOIN sys_user u ON u.id = s.user_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.real_name LIKE ? OR s.staff_no LIKE ? OR s.position LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY s.staff_no");
        return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(Staff.class), args.toArray());
    }

    public Staff findStaffByUserId(Long userId) {
        List<Staff> list = jdbc.query("SELECT s.*, u.real_name, u.phone FROM staff s JOIN sys_user u ON u.id = s.user_id "
                + "WHERE s.user_id = ?", new BeanPropertyRowMapper<>(Staff.class), userId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 更新员工档案（岗位、班次、状态、备注）。 */
    public int updateStaff(Long staffId, Map<String, Object> values) {
        StringBuilder sql = new StringBuilder("UPDATE staff SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(CrudDao.column(e.getKey())).append(" = ?");
            args.add(e.getValue());
        }
        sql.append(" WHERE id = ?");
        args.add(staffId);
        return jdbc.update(sql.toString(), args.toArray());
    }

    // ------------------------------------------------------------------
    // 统计报表
    // ------------------------------------------------------------------

    /** 首页概览指标。 */
    public Map<String, Object> dashboardSummary() {
        Map<String, Object> map = new HashMap<>();
        map.put("roomTotal", queryLong("SELECT COUNT(*) FROM room"));
        map.put("roomFree", queryLong("SELECT COUNT(*) FROM room WHERE status = 'FREE'"));
        map.put("roomOccupied", queryLong("SELECT COUNT(*) FROM room WHERE status = 'OCCUPIED'"));
        map.put("roomCleaning", queryLong("SELECT COUNT(*) FROM room WHERE status = 'CLEANING'"));
        map.put("roomMaintenance", queryLong("SELECT COUNT(*) FROM room WHERE status = 'MAINTENANCE'"));
        map.put("guestTotal", queryLong("SELECT COUNT(*) FROM guest"));
        map.put("guestToday", queryLong("SELECT COUNT(*) FROM reservation WHERE check_in_date = CURDATE() AND status IN ('BOOKED','CHECKED_IN')"));
        map.put("checkoutToday", queryLong("SELECT COUNT(*) FROM reservation WHERE check_out_date = CURDATE() AND status = 'CHECKED_IN'"));
        map.put("bookingActive", queryLong("SELECT COUNT(*) FROM reservation WHERE status = 'BOOKED'"));
        map.put("checkedIn", queryLong("SELECT COUNT(*) FROM reservation WHERE status = 'CHECKED_IN'"));
        map.put("revenueTotalCents", queryLong("SELECT COALESCE(SUM(paid_cents),0) FROM bill WHERE status = 'SETTLED'"));
        map.put("revenueTodayCents", queryLong("SELECT COALESCE(SUM(paid_cents),0) FROM bill WHERE status = 'SETTLED' AND DATE(settled_at) = CURDATE()"));
        map.put("unsettledCents", queryLong("SELECT COALESCE(SUM(total_cents - paid_cents),0) FROM bill WHERE status = 'UNSETTLED'"));
        map.put("depositBalanceCents", queryLong("SELECT COALESCE(SUM(balance_cents),0) FROM guest"));
        map.put("consumeTotalCents", queryLong("SELECT COALESCE(SUM(amount_cents),0) FROM bill_item WHERE item_type = 'CONSUME'"));
        map.put("aiCallTotal", queryLong("SELECT COUNT(*) FROM ai_call_log"));
        map.put("aiDegradedTotal", queryLong("SELECT COUNT(*) FROM ai_call_log WHERE degraded = 1"));
        map.put("occupancyRate", occupancyRate(LocalDate.now()));
        return map;
    }

    /** 出租率 = 已入住房间数 / 可售房间数（维修房不计入分母）。 */
    public double occupancyRate(LocalDate date) {
        Long sellable = jdbc.queryForObject("SELECT COUNT(*) FROM room WHERE status <> 'MAINTENANCE'", Long.class);
        Long occupied = jdbc.queryForObject("SELECT COUNT(DISTINCT room_id) FROM reservation "
                + "WHERE status = 'CHECKED_IN' AND check_in_date <= ? AND check_out_date > ?", Long.class, date, date);
        if (sellable == null || sellable == 0) {
            return 0;
        }
        return Math.round((occupied == null ? 0 : occupied) * 1000.0 / sellable) / 10.0;
    }

    /** 近 N 天营收走势（按结算时间统计）。 */
    public List<Map<String, Object>> revenueTrend(int days) {
        String sql = "SELECT DATE(settled_at) AS day, COUNT(*) AS bill_count, "
                + "COALESCE(SUM(paid_cents),0) AS revenue_cents, "
                + "COALESCE(SUM(room_charge_cents),0) AS room_cents, "
                + "COALESCE(SUM(consume_charge_cents),0) AS consume_cents "
                + "FROM bill WHERE status = 'SETTLED' AND settled_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
                + "GROUP BY DATE(settled_at) ORDER BY day";
        return jdbc.queryForList(sql, days);
    }

    /** 近 N 天入住/退房量。 */
    public List<Map<String, Object>> stayTrend(int days) {
        String sql = "SELECT d.day, "
                + "(SELECT COUNT(*) FROM reservation r WHERE r.check_in_date = d.day) AS check_in_count, "
                + "(SELECT COUNT(*) FROM reservation r WHERE r.check_out_date = d.day AND r.status = 'CHECKED_OUT') AS check_out_count "
                + "FROM (SELECT DATE_SUB(CURDATE(), INTERVAL seq DAY) AS day FROM "
                + "  (SELECT 0 AS seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) s) d "
                + "WHERE d.day >= DATE_SUB(CURDATE(), INTERVAL ? DAY) ORDER BY d.day";
        return jdbc.queryForList(sql, days);
    }

    /** 房态分布。 */
    public List<Map<String, Object>> roomStatusDistribution() {
        return jdbc.queryForList("SELECT r.status, COUNT(*) AS cnt FROM room r GROUP BY r.status ORDER BY cnt DESC");
    }

    /** 各房型出租情况。 */
    public List<Map<String, Object>> roomTypeStats() {
        String sql = "SELECT t.type_name, t.price_cents, COUNT(r.id) AS room_count, "
                + "SUM(CASE WHEN r.status = 'OCCUPIED' THEN 1 ELSE 0 END) AS occupied_count, "
                + "(SELECT COUNT(*) FROM reservation res WHERE res.type_id = t.id) AS reservation_count, "
                + "(SELECT COALESCE(SUM(b.room_charge_cents),0) FROM bill b JOIN reservation res2 ON res2.id = b.reservation_id "
                + " WHERE res2.type_id = t.id AND b.status = 'SETTLED') AS room_revenue_cents "
                + "FROM room_type t LEFT JOIN room r ON r.type_id = t.id GROUP BY t.id ORDER BY t.price_cents";
        return jdbc.queryForList(sql);
    }

    /** 客人消费排行。 */
    public List<Map<String, Object>> guestRanking(int limit) {
        return jdbc.queryForList("SELECT real_name AS guest_name, guest_no, member_level, phone, "
                + "total_stay_count, total_consume_cents, points, balance_cents FROM guest "
                + "ORDER BY total_consume_cents DESC LIMIT ?", limit);
    }

    /** 消费项目销售排行。 */
    public List<Map<String, Object>> consumeRanking(int limit) {
        return jdbc.queryForList("SELECT item_name, SUM(quantity) AS qty, SUM(amount_cents) AS amount_cents "
                + "FROM bill_item WHERE item_type = 'CONSUME' GROUP BY item_name ORDER BY amount_cents DESC LIMIT ?", limit);
    }

    /** 预订渠道分布。 */
    public List<Map<String, Object>> channelStats() {
        return jdbc.queryForList("SELECT channel, COUNT(*) AS cnt, COALESCE(SUM(total_cents),0) AS amount_cents "
                + "FROM reservation GROUP BY channel ORDER BY cnt DESC");
    }

    /** AI 调用统计（按场景）。 */
    public List<Map<String, Object>> aiSceneStats() {
        return jdbc.queryForList("SELECT scene, COUNT(*) AS call_count, SUM(success) AS success_count, "
                + "SUM(degraded) AS degraded_count, ROUND(AVG(latency_ms)) AS avg_latency, "
                + "SUM(cost_cents) AS cost_cents FROM ai_call_log GROUP BY scene ORDER BY call_count DESC");
    }

    private long queryLong(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }

    /** 客人画像（供 AI 生成报表与报价使用）。 */
    public Map<String, Object> guestProfile(Long guestId) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT g.*, TIMESTAMPDIFF(YEAR, g.birthday, CURDATE()) AS age, "
                        + "(SELECT COUNT(*) FROM reservation r WHERE r.guest_id = g.id AND r.status = 'CHECKED_OUT') AS stay_count, "
                        + "(SELECT MAX(r.check_out_date) FROM reservation r WHERE r.guest_id = g.id) AS last_check_out "
                        + "FROM guest g WHERE g.id = ?", guestId);
        return list.isEmpty() ? Map.of() : list.get(0);
    }

    /** 客人的历史入住摘要。 */
    public List<Map<String, Object>> guestStayHistory(Long guestId, int limit) {
        return jdbc.queryForList("SELECT r.reservation_no, r.check_in_date, r.check_out_date, r.nights, "
                + "r.unit_price_cents, r.total_cents, r.status, t.type_name, rm.room_no "
                + "FROM reservation r JOIN room_type t ON t.id = r.type_id LEFT JOIN room rm ON rm.id = r.room_id "
                + "WHERE r.guest_id = ? ORDER BY r.check_in_date DESC LIMIT ?", guestId, limit);
    }

    public BigDecimal sumColumn(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
