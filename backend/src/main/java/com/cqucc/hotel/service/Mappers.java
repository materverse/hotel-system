package com.cqucc.hotel.service;

import com.cqucc.hotel.entity.Bill;
import com.cqucc.hotel.entity.BillItem;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.GuestTransaction;
import com.cqucc.hotel.entity.OperationLog;
import com.cqucc.hotel.entity.Reservation;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import com.cqucc.hotel.entity.Staff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体 → 数据库列映射工具。
 * <p>使用显式 Map 而非反射，保证插入/更新涉及哪些字段一目了然，避免误更新主键与创建时间。</p>
 */
public final class Mappers {

    private Mappers() {
    }

    public static Map<String, Object> guest(Guest g) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "user_id", g.getUserId());
        put(m, "guest_no", g.getGuestNo());
        put(m, "real_name", g.getRealName());
        put(m, "gender", g.getGender());
        put(m, "phone", g.getPhone());
        put(m, "id_card", g.getIdCard());
        put(m, "id_type", g.getIdType());
        put(m, "birthday", g.getBirthday());
        put(m, "address", g.getAddress());
        put(m, "member_level", g.getMemberLevel());
        put(m, "balance_cents", g.getBalanceCents());
        put(m, "points", g.getPoints());
        put(m, "total_stay_count", g.getTotalStayCount());
        put(m, "total_consume_cents", g.getTotalConsumeCents());
        put(m, "remark", g.getRemark());
        put(m, "status", g.getStatus());
        return m;
    }

    public static Map<String, Object> room(Room r) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "room_no", r.getRoomNo());
        put(m, "floor_no", r.getFloorNo());
        put(m, "type_id", r.getTypeId());
        put(m, "orientation", r.getOrientation());
        put(m, "status", r.getStatus());
        put(m, "clean_status", r.getCleanStatus());
        put(m, "remark", r.getRemark());
        return m;
    }

    public static Map<String, Object> roomType(RoomType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "type_name", t.getTypeName());
        put(m, "bed_type", t.getBedType());
        put(m, "area_sqm", t.getAreaSqm());
        put(m, "capacity", t.getCapacity());
        put(m, "price_cents", t.getPriceCents());
        put(m, "member_price_cents", t.getMemberPriceCents());
        put(m, "facilities", t.getFacilities());
        put(m, "description", t.getDescription());
        put(m, "status", t.getStatus());
        return m;
    }

    public static Map<String, Object> staff(Staff s) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "user_id", s.getUserId());
        put(m, "staff_no", s.getStaffNo());
        put(m, "gender", s.getGender());
        put(m, "position", s.getPosition());
        put(m, "work_shift", s.getWorkShift());
        put(m, "intro", s.getIntro());
        put(m, "status", s.getStatus());
        return m;
    }

    public static Map<String, Object> reservation(Reservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "reservation_no", r.getReservationNo());
        put(m, "guest_id", r.getGuestId());
        put(m, "room_id", r.getRoomId());
        put(m, "type_id", r.getTypeId());
        put(m, "check_in_date", r.getCheckInDate());
        put(m, "check_out_date", r.getCheckOutDate());
        put(m, "nights", r.getNights());
        put(m, "room_count", r.getRoomCount());
        put(m, "guest_count", r.getGuestCount());
        put(m, "unit_price_cents", r.getUnitPriceCents());
        put(m, "total_cents", r.getTotalCents());
        put(m, "deposit_cents", r.getDepositCents());
        put(m, "channel", r.getChannel());
        put(m, "status", r.getStatus());
        put(m, "special_request", r.getSpecialRequest());
        put(m, "created_by", r.getCreatedBy());
        put(m, "check_in_at", r.getCheckInAt());
        put(m, "check_out_at", r.getCheckOutAt());
        put(m, "cancel_reason", r.getCancelReason());
        return m;
    }

    public static Map<String, Object> bill(Bill b) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "bill_no", b.getBillNo());
        put(m, "reservation_id", b.getReservationId());
        put(m, "guest_id", b.getGuestId());
        put(m, "room_charge_cents", b.getRoomChargeCents());
        put(m, "consume_charge_cents", b.getConsumeChargeCents());
        put(m, "discount_cents", b.getDiscountCents());
        put(m, "deposit_cents", b.getDepositCents());
        put(m, "total_cents", b.getTotalCents());
        put(m, "paid_cents", b.getPaidCents());
        put(m, "refund_cents", b.getRefundCents());
        put(m, "pay_type", b.getPayType());
        put(m, "status", b.getStatus());
        put(m, "settled_by", b.getSettledBy());
        put(m, "settled_at", b.getSettledAt());
        return m;
    }

    public static Map<String, Object> billItem(BillItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "bill_id", i.getBillId());
        put(m, "item_type", i.getItemType());
        put(m, "item_name", i.getItemName());
        put(m, "unit_price_cents", i.getUnitPriceCents());
        put(m, "quantity", i.getQuantity());
        put(m, "amount_cents", i.getAmountCents());
        put(m, "remark", i.getRemark());
        return m;
    }

    public static Map<String, Object> transaction(GuestTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "guest_id", t.getGuestId());
        put(m, "tx_no", t.getTxNo());
        put(m, "tx_type", t.getTxType());
        put(m, "amount_cents", t.getAmountCents());
        put(m, "balance_after", t.getBalanceAfter());
        put(m, "biz_type", t.getBizType());
        put(m, "biz_id", t.getBizId());
        put(m, "operator", t.getOperator());
        put(m, "remark", t.getRemark());
        return m;
    }

    public static Map<String, Object> ratePlan(com.cqucc.hotel.entity.RatePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "type_id", p.getTypeId());
        put(m, "plan_name", p.getPlanName());
        put(m, "start_date", p.getStartDate());
        put(m, "end_date", p.getEndDate());
        put(m, "price_cents", p.getPriceCents());
        put(m, "status", p.getStatus());
        return m;
    }

    public static Map<String, Object> consumeItem(com.cqucc.hotel.entity.ConsumeItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "item_name", i.getItemName());
        put(m, "category", i.getCategory());
        put(m, "price_cents", i.getPriceCents());
        put(m, "unit", i.getUnit());
        put(m, "description", i.getDescription());
        put(m, "status", i.getStatus());
        return m;
    }

    public static Map<String, Object> operationLog(OperationLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "user_id", l.getUserId());
        put(m, "username", l.getUsername());
        put(m, "module", l.getModule());
        put(m, "action", l.getAction());
        put(m, "detail", l.getDetail());
        put(m, "ip", l.getIp());
        put(m, "success", l.getSuccess());
        return m;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
