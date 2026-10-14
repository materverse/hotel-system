package com.cqucc.hotel.service;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.Bill;
import com.cqucc.hotel.entity.BillItem;
import com.cqucc.hotel.entity.ConsumeItem;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.GuestTransaction;
import com.cqucc.hotel.entity.Reservation;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预订与账单服务：房价试算、订房、入住、退房、账单结算，构成系统核心业务闭环。
 *
 * <p>业务规则（与知识库文档保持一致，便于 AI 引用）：</p>
 * <ul>
 *   <li>入住 14:00 之后、离店 12:00 之前；预订保留至当日 18:00；</li>
 *   <li>押金：标准房/高级房 300 元，家庭房 500 元，套房 1000 元（按房型自动计算，可覆盖）；</li>
 *   <li>会员折扣：黄金 9 折、白银 9.5 折；若该日期存在生效的房价策略，则按策略价执行且不再打折；</li>
 *   <li>结算：应收 = 房费 + 消费 − 优惠 − 押金抵扣；押金有剩余则退还，不足则补收。</li>
 * </ul>
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /** 各房型的默认押金（分）。 */
    private static final Map<Long, Integer> DEFAULT_DEPOSIT = Map.of(
            1L, 30000,
            2L, 30000,
            3L, 30000,
            4L, 50000,
            5L, 100000);

    private final BusinessDao businessDao;
    private final CrudDao<Reservation> reservationDao;
    private final CrudDao<Room> roomDao;
    private final CrudDao<RoomType> roomTypeDao;
    private final CrudDao<Guest> guestDao;
    private final CrudDao<Bill> billDao;
    private final CrudDao<BillItem> billItemDao;
    private final CrudDao<GuestTransaction> txDao;
    private final BizNoGenerator noGenerator;
    private final AuthService authService;

    public ReservationService(BusinessDao businessDao, CrudDao<Reservation> reservationCrudDao,
                              CrudDao<Room> roomCrudDao, CrudDao<RoomType> roomTypeDao,
                              CrudDao<Guest> guestCrudDao, CrudDao<Bill> billCrudDao,
                              CrudDao<BillItem> billItemDao, CrudDao<GuestTransaction> guestTransactionDao,
                              BizNoGenerator noGenerator, AuthService authService) {
        this.businessDao = businessDao;
        this.reservationDao = reservationCrudDao;
        this.roomDao = roomCrudDao;
        this.roomTypeDao = roomTypeDao;
        this.guestDao = guestCrudDao;
        this.billDao = billCrudDao;
        this.billItemDao = billItemDao;
        this.txDao = guestTransactionDao;
        this.noGenerator = noGenerator;
        this.authService = authService;
    }

    // ------------------------------------------------------------------
    // 房价试算
    // ------------------------------------------------------------------

    /** 报价明细。 */
    public record QuoteDetail(Long typeId, String typeName, LocalDate checkIn, LocalDate checkOut, int nights,
                              int roomCount, int unitPriceCents, int originUnitPriceCents, int discountRate,
                              String priceSource, int totalCents, int depositCents, double occupancyRate) {
    }

    /**
     * 计算房价：优先使用生效的房价策略，其次按会员等级折扣使用会员价/门市价。
     */
    public QuoteDetail quote(Long typeId, LocalDate checkIn, LocalDate checkOut, int roomCount, Long guestId) {
        RoomType type = businessDao.findRoomTypeById(typeId);
        if (type == null) {
            throw new BusinessException("房型不存在");
        }
        if (!"ON_SALE".equals(type.getStatus())) {
            throw new BusinessException("房型《" + type.getTypeName() + "》已停售，暂不接受预订");
        }
        validateDates(checkIn, checkOut);
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        int count = roomCount <= 0 ? 1 : roomCount;

        int originPrice = type.getPriceCents();
        int unitPrice = originPrice;
        int discountRate = 100;
        String priceSource = "门市价";

        var plan = businessDao.findRatePlan(typeId, checkIn);
        if (plan != null) {
            unitPrice = plan.getPriceCents();
            priceSource = "房价策略：" + plan.getPlanName();
        } else {
            Guest guest = guestId == null ? null : businessDao.findGuestById(guestId);
            String level = guest == null ? "NORMAL" : guest.getMemberLevel();
            discountRate = discountRate(level);
            if (discountRate < 100) {
                unitPrice = type.getMemberPriceCents() > 0
                        ? type.getMemberPriceCents() * discountRate / 100
                        : originPrice * discountRate / 100;
                priceSource = levelName(level) + "会员价（" + (discountRate / 10.0) + " 折）";
            }
        }
        int total = unitPrice * nights * count;
        int deposit = depositOf(type.getId()) * count;
        int available = businessDao.countAvailableRooms(typeId, checkIn, checkOut);
        return new QuoteDetail(typeId, type.getTypeName(), checkIn, checkOut, nights, count,
                unitPrice, originPrice, discountRate, priceSource, total, deposit,
                available * 100.0 / Math.max(1, countRoomsOfType(typeId)));
    }

    /** 会员折扣（百分数）。 */
    public int discountRate(String level) {
        if (level == null) {
            return 100;
        }
        return switch (level) {
            case "GOLD" -> 90;
            case "SILVER" -> 95;
            default -> 100;
        };
    }

    public String levelName(String level) {
        if (level == null) {
            return "普通";
        }
        return switch (level) {
            case "GOLD" -> "黄金";
            case "SILVER" -> "白银";
            default -> "普通";
        };
    }

    public int depositOf(Long typeId) {
        return DEFAULT_DEPOSIT.getOrDefault(typeId, 30000);
    }

    private int countRoomsOfType(Long typeId) {
        Long c = roomDao.count(CrudDao.cond("type_id", typeId));
        return c == null || c == 0 ? 1 : c.intValue();
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("请选择入住日期与离店日期");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("离店日期必须晚于入住日期（最少入住 1 晚）");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 30) {
            throw new BusinessException("单笔预订最长 30 晚，长期住宿请联系前台签订协议价");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BusinessException("入住日期不能早于今天");
        }
    }

    // ------------------------------------------------------------------
    // 订房
    // ------------------------------------------------------------------

    /**
     * 创建预订：校验日期、房量、客人状态，计算价格，生成预订单，若指定房间则占用房态。
     */
    @Transactional
    public Map<String, Object> book(Long guestId, Long typeId, Long roomId, LocalDate checkIn, LocalDate checkOut,
                                    int roomCount, int guestCount, String channel, String specialRequest,
                                    Integer depositOverride, String operator) {
        Guest guest = businessDao.findGuestById(guestId);
        if (guest == null) {
            throw new BusinessException("客人档案不存在，请先登记客人信息");
        }
        if ("BLACKLIST".equals(guest.getStatus())) {
            throw new BusinessException("该客人已被列入黑名单，无法办理预订，请联系值班经理");
        }
        int count = roomCount <= 0 ? 1 : roomCount;
        QuoteDetail quote = quote(typeId, checkIn, checkOut, count, guestId);
        int available = businessDao.countAvailableRooms(typeId, checkIn, checkOut);
        if (available < count) {
            throw new BusinessException("该房型在所选日期仅剩 " + available + " 间可订，无法满足 " + count + " 间的需求");
        }
        if (roomId != null) {
            Room room = businessDao.findRoomById(roomId);
            if (room == null) {
                throw new BusinessException("指定房间不存在");
            }
            if (!room.getTypeId().equals(typeId)) {
                throw new BusinessException("指定房间（" + room.getRoomNo() + "）与所选房型不一致");
            }
            if (!businessDao.isRoomAvailable(roomId, checkIn, checkOut, null)) {
                throw new BusinessException("房间 " + room.getRoomNo() + " 在该日期区间已被预订，请选择其它房间或日期");
            }
        }

        RoomType type = businessDao.findRoomTypeById(typeId);
        if (guestCount > type.getCapacity() * count) {
            throw new BusinessException("入住人数超过房型可住人数（每间最多 " + type.getCapacity() + " 人）");
        }

        Reservation reservation = new Reservation();
        reservation.setReservationNo(noGenerator.next("RS"));
        reservation.setGuestId(guestId);
        reservation.setRoomId(roomId);
        reservation.setTypeId(typeId);
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setNights(quote.nights());
        reservation.setRoomCount(count);
        reservation.setGuestCount(guestCount <= 0 ? 1 : guestCount);
        reservation.setUnitPriceCents(quote.unitPriceCents());
        reservation.setTotalCents(quote.totalCents());
        reservation.setDepositCents(depositOverride == null ? quote.depositCents() : depositOverride);
        reservation.setChannel(channel == null || channel.isBlank() ? "FRONT_DESK" : channel);
        reservation.setStatus("BOOKED");
        reservation.setSpecialRequest(specialRequest);
        reservation.setCreatedBy(operator);
        Long id = reservationDao.insertReturnKey(Mappers.reservation(reservation));

        // 指定了房间时立即把房态改为已预订（不占用则在入住时分配）
        if (roomId != null) {
            markRoomBooked(roomId);
        }
        // 预建账单（未结算），入住期间可继续追加消费
        createBillFor(id, guestId, reservation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reservationId", id);
        result.put("reservationNo", reservation.getReservationNo());
        result.put("guestName", guest.getRealName());
        result.put("typeName", type.getTypeName());
        result.put("nights", quote.nights());
        result.put("roomCount", count);
        result.put("unitPriceCents", quote.unitPriceCents());
        result.put("priceSource", quote.priceSource());
        result.put("totalCents", quote.totalCents());
        result.put("depositCents", reservation.getDepositCents());
        result.put("checkInDate", checkIn);
        result.put("checkOutDate", checkOut);
        result.put("notice", "预订保留至入住当日 18:00，超时未到店将自动释放房间");
        log.info("生成预订 {}：客人 {}，房型 {}，{} 晚，总额 {} 分",
                reservation.getReservationNo(), guest.getRealName(), type.getTypeName(), quote.nights(), quote.totalCents());
        return result;
    }

    private void createBillFor(Long reservationId, Long guestId, Reservation reservation) {
        Bill bill = new Bill();
        bill.setBillNo(noGenerator.next("BL"));
        bill.setReservationId(reservationId);
        bill.setGuestId(guestId);
        bill.setRoomChargeCents(reservation.getTotalCents());
        bill.setConsumeChargeCents(0);
        bill.setDiscountCents(0);
        bill.setDepositCents(0);
        bill.setTotalCents(reservation.getTotalCents());
        bill.setPaidCents(0);
        bill.setRefundCents(0);
        bill.setStatus("UNSETTLED");
        Long billId = billDao.insertReturnKey(Mappers.bill(bill));
        // 房费明细
        BillItem roomItem = new BillItem();
        roomItem.setBillId(billId);
        roomItem.setItemType("ROOM");
        roomItem.setItemName("房费（" + reservation.getNights() + " 晚 × " + reservation.getRoomCount() + " 间）");
        roomItem.setUnitPriceCents(reservation.getUnitPriceCents());
        roomItem.setQuantity(reservation.getNights() * reservation.getRoomCount());
        roomItem.setAmountCents(reservation.getTotalCents());
        billItemDao.insertReturnKey(Mappers.billItem(roomItem));
    }

    private void markRoomBooked(Long roomId) {
        Room room = roomDao.findById(roomId);
        if (room != null && "FREE".equals(room.getStatus())) {
            Room update = new Room();
            update.setStatus("BOOKED");
            roomDao.updateById(roomId, Mappers.room(update));
        }
    }

    // ------------------------------------------------------------------
    // 入住
    // ------------------------------------------------------------------

    /**
     * 办理入住：校验状态与房态、分配房间、收取押金（记入客人预存流水）、更新房态与客人入住次数。
     */
    @Transactional
    public Map<String, Object> checkIn(Long reservationId, Long roomId, String idCard, String operator) {
        Reservation reservation = businessDao.lockReservation(reservationId);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        if (!"BOOKED".equals(reservation.getStatus())) {
            throw new BusinessException("当前预订单状态为 " + statusText(reservation.getStatus()) + "，无法办理入住");
        }
        LocalDate today = LocalDate.now();
        if (reservation.getCheckInDate().isAfter(today)) {
            throw new BusinessException("该预订单的入住日期为 " + reservation.getCheckInDate() + "，尚未到入住日期");
        }
        if (reservation.getCheckOutDate().isBefore(today) || reservation.getCheckOutDate().isEqual(today)) {
            throw new BusinessException("该预订单已过期（离店日期 " + reservation.getCheckOutDate() + "），请重新预订");
        }

        Long targetRoomId = roomId != null ? roomId : reservation.getRoomId();
        if (targetRoomId == null) {
            List<Room> candidates = businessDao.findAvailableRooms(reservation.getTypeId(),
                    reservation.getCheckInDate(), reservation.getCheckOutDate(), reservation.getRoomCount());
            if (candidates.isEmpty()) {
                throw new BusinessException("所选房型当前没有空闲房间可分配，请更换房型或调整日期");
            }
            targetRoomId = candidates.get(0).getId();
        }
        Room room = businessDao.lockRoom(targetRoomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if ("MAINTENANCE".equals(room.getStatus())) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 正在维修，无法入住");
        }
        if ("OCCUPIED".equals(room.getStatus())) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 已有人入住，请选择其它房间");
        }
        if (!businessDao.isRoomAvailable(targetRoomId, reservation.getCheckInDate(), reservation.getCheckOutDate(),
                reservation.getId())) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 在该日期区间已有预订，请选择其它房间");
        }
        if (!room.getTypeId().equals(reservation.getTypeId())) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 的房型与预订单不一致，请确认后重新选择");
        }

        // 押金：从客人预存余额扣除（不足时线下收取，这里记录押金流水）
        int deposit = reservation.getDepositCents();
        Guest guest = businessDao.lockGuest(reservation.getGuestId());
        int balanceAfter = (guest.getBalanceCents() == null ? 0 : guest.getBalanceCents()) - deposit;
        boolean paidByBalance = balanceAfter >= 0;
        if (paidByBalance) {
            Map<String, Object> guestValues = new LinkedHashMap<>();
            guestValues.put("balance_cents", balanceAfter);
            guestValues.put("total_stay_count", (guest.getTotalStayCount() == null ? 0 : guest.getTotalStayCount()));
            guestDao.updateById(guest.getId(), guestValues);
            writeTx(guest.getId(), "CONSUME", -deposit, balanceAfter, "RESERVATION", reservationId, operator,
                    "办理入住收取押金 " + yuan(deposit));
        } else {
            // 预存不足：押金以线下方式收取，不扣预存余额
            balanceAfter = guest.getBalanceCents() == null ? 0 : guest.getBalanceCents();
            writeTx(guest.getId(), "CONSUME", 0, balanceAfter, "RESERVATION", reservationId, operator,
                    "押金 " + yuan(deposit) + " 由前台线下收取（预存余额不足）");
        }

        Map<String, Object> resValues = new LinkedHashMap<>();
        resValues.put("room_id", targetRoomId);
        resValues.put("status", "CHECKED_IN");
        resValues.put("check_in_at", LocalDateTime.now());
        if (idCard != null && !idCard.isBlank()) {
            Map<String, Object> guestValues = new LinkedHashMap<>();
            guestValues.put("id_card", idCard);
            guestDao.updateById(guest.getId(), guestValues);
        }
        reservationDao.updateById(reservationId, resValues);

        Room roomUpdate = new Room();
        roomUpdate.setStatus("OCCUPIED");
        roomUpdate.setCleanStatus("CLEAN");
        roomDao.updateById(targetRoomId, Mappers.room(roomUpdate));

        // 账单登记押金
        Bill bill = businessDao.findBillByReservation(reservationId);
        if (bill != null) {
            Map<String, Object> billValues = new LinkedHashMap<>();
            billValues.put("deposit_cents", deposit);
            billValues.put("total_cents", Math.max(0, bill.getRoomChargeCents() + bill.getConsumeChargeCents()
                    - bill.getDiscountCents() - deposit));
            billDao.updateById(bill.getId(), billValues);
        }

        authService.writeLog(null, operator, "前台业务", "办理入住",
                "预订单 " + reservation.getReservationNo() + "，房间 " + room.getRoomNo(), null, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reservationNo", reservation.getReservationNo());
        result.put("roomNo", room.getRoomNo());
        result.put("roomId", targetRoomId);
        result.put("depositCents", deposit);
        result.put("paidByBalance", paidByBalance);
        result.put("guestBalanceAfter", balanceAfter);
        result.put("checkOutDate", reservation.getCheckOutDate());
        result.put("notice", "退房时间为 " + reservation.getCheckOutDate() + " 12:00 前，逾期将加收房费");
        return result;
    }

    // ------------------------------------------------------------------
    // 退房
    // ------------------------------------------------------------------

    /**
     * 办理退房：按实际住宿晚数重算房费、释放房间为待打扫、返回账单待结算。
     */
    @Transactional
    public Map<String, Object> checkOut(Long reservationId, String operator) {
        Reservation reservation = businessDao.lockReservation(reservationId);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        if (!"CHECKED_IN".equals(reservation.getStatus())) {
            throw new BusinessException("只有已入住的订单可以办理退房，当前状态：" + statusText(reservation.getStatus()));
        }
        if (reservation.getRoomId() == null) {
            throw new BusinessException("该订单没有分配房间，数据异常，请联系管理员");
        }
        // 实际住宿晚数：按入住日期到今天计算，最少 1 晚
        long stayNights = Math.max(1, ChronoUnit.DAYS.between(reservation.getCheckInDate(), LocalDate.now()));
        int actualRoomCharge = (int) (reservation.getUnitPriceCents() * stayNights * reservation.getRoomCount());

        Bill bill = businessDao.findBillByReservation(reservationId);
        if (bill == null) {
            throw new BusinessException("账单数据缺失，请联系管理员");
        }
        int lateFee = lateCheckoutFee();
        int roomCharge = actualRoomCharge + lateFee;
        int total = roomCharge + (bill.getConsumeChargeCents() == null ? 0 : bill.getConsumeChargeCents())
                - (bill.getDiscountCents() == null ? 0 : bill.getDiscountCents())
                - (bill.getDepositCents() == null ? 0 : bill.getDepositCents());
        Map<String, Object> billValues = new LinkedHashMap<>();
        billValues.put("room_charge_cents", roomCharge);
        billValues.put("total_cents", Math.max(0, total));
        billDao.updateById(bill.getId(), billValues);
        // 更新房费明细为实际晚数
        List<BillItem> items = businessDao.listBillItems(bill.getId());
        for (BillItem item : items) {
            if ("ROOM".equals(item.getItemType())) {
                Map<String, Object> itemValues = new LinkedHashMap<>();
                itemValues.put("item_name", "房费（实际 " + stayNights + " 晚 × " + reservation.getRoomCount() + " 间"
                        + (lateFee > 0 ? " + 延迟退房费" : "") + "）");
                itemValues.put("quantity", (int) stayNights * reservation.getRoomCount());
                itemValues.put("amount_cents", roomCharge);
                billItemDao.updateById(item.getId(), itemValues);
            }
        }

        Map<String, Object> resValues = new LinkedHashMap<>();
        resValues.put("status", "CHECKED_OUT");
        resValues.put("check_out_at", LocalDateTime.now());
        reservationDao.updateById(reservationId, resValues);

        Room roomUpdate = new Room();
        roomUpdate.setStatus("CLEANING");
        roomUpdate.setCleanStatus("DIRTY");
        roomDao.updateById(reservation.getRoomId(), Mappers.room(roomUpdate));

        Guest guest = businessDao.findGuestById(reservation.getGuestId());
        Map<String, Object> guestValues = new LinkedHashMap<>();
        guestValues.put("total_stay_count", (guest.getTotalStayCount() == null ? 0 : guest.getTotalStayCount()) + 1);
        guestDao.updateById(guest.getId(), guestValues);

        Bill latest = businessDao.findBillById(bill.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reservationNo", reservation.getReservationNo());
        result.put("stayNights", stayNights);
        result.put("roomChargeCents", roomCharge);
        result.put("lateFeeCents", lateFee);
        result.put("consumeChargeCents", latest.getConsumeChargeCents());
        result.put("discountCents", latest.getDiscountCents());
        result.put("depositCents", latest.getDepositCents());
        result.put("payableCents", latest.getTotalCents());
        result.put("billId", latest.getId());
        result.put("billNo", latest.getBillNo());
        result.put("notice", "房间已置为待打扫，请到账单页完成结算");
        return result;
    }

    /** 延迟退房费：14:00 之后加收半天房费，18:00 之后加收全天（简化判断）。 */
    private int lateCheckoutFee() {
        int hour = LocalDateTime.now().getHour();
        return hour >= 18 ? 5000 * 2 : (hour >= 14 ? 5000 : 0);
    }

    // ------------------------------------------------------------------
    // 取消与查询
    // ------------------------------------------------------------------

    /** 取消预订：释放房间，退还预存押金（若有）。 */
    @Transactional
    public Map<String, Object> cancel(Long reservationId, String reason, String operator) {
        Reservation reservation = businessDao.lockReservation(reservationId);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        if (!"BOOKED".equals(reservation.getStatus())) {
            throw new BusinessException("只有未入住的预订可以取消，当前状态：" + statusText(reservation.getStatus()));
        }
        reservationDao.updateById(reservationId, Map.of("status", "CANCELLED", "cancel_reason",
                reason == null ? "客人取消" : reason));
        if (reservation.getRoomId() != null) {
            Room room = roomDao.findById(reservation.getRoomId());
            if (room != null && "BOOKED".equals(room.getStatus())) {
                Room update = new Room();
                update.setStatus("FREE");
                roomDao.updateById(room.getId(), Mappers.room(update));
            }
        }
        Bill bill = businessDao.findBillByReservation(reservationId);
        if (bill != null) {
            billDao.updateById(bill.getId(), Map.of("status", "VOID", "total_cents", 0));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reservationNo", reservation.getReservationNo());
        result.put("status", "CANCELLED");
        result.put("reason", reason == null ? "客人取消" : reason);
        result.put("notice", "预订已取消，房间已释放；如已通过线上支付，押金将在 3 个工作日内退回");
        return result;
    }

    /** 标记未到店（超过保留时间未入住）。 */
    @Transactional
    public void markNoShow(Long reservationId) {
        Reservation reservation = reservationDao.findById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        if (!"BOOKED".equals(reservation.getStatus())) {
            throw new BusinessException("只有未入住的预订可以标记未到店");
        }
        reservationDao.updateById(reservationId, Map.of("status", "NO_SHOW", "cancel_reason", "超过保留时间未到店"));
        if (reservation.getRoomId() != null) {
            Room update = new Room();
            update.setStatus("FREE");
            roomDao.updateById(reservation.getRoomId(), Mappers.room(update));
        }
    }

    public Map<String, Object> page(String status, Long guestId, String keyword, LocalDate date, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", businessDao.listReservations(status, guestId, keyword, date, p, s));
        result.put("total", businessDao.countReservations(status, guestId, keyword, date));
        result.put("page", p);
        result.put("size", s);
        return result;
    }

    public Reservation detail(Long id) {
        Reservation reservation = businessDao.findReservationById(id);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        return reservation;
    }

    /** 前台当日看板：应到店、在住、应离店。 */
    public Map<String, Object> todayBoard() {
        LocalDate today = LocalDate.now();
        List<Reservation> all = businessDao.todayBoard(today);
        List<Reservation> arrivals = new ArrayList<>();
        List<Reservation> inHouse = new ArrayList<>();
        List<Reservation> departures = new ArrayList<>();
        for (Reservation r : all) {
            if ("BOOKED".equals(r.getStatus()) && today.equals(r.getCheckInDate())) {
                arrivals.add(r);
            } else if ("CHECKED_IN".equals(r.getStatus())) {
                inHouse.add(r);
                if (today.equals(r.getCheckOutDate())) {
                    departures.add(r);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today);
        result.put("arrivals", arrivals);
        result.put("inHouse", inHouse);
        result.put("departures", departures);
        result.put("occupancyRate", businessDao.occupancyRate(today));
        return result;
    }

    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "BOOKED" -> "已预订";
            case "CHECKED_IN" -> "已入住";
            case "CHECKED_OUT" -> "已退房";
            case "CANCELLED" -> "已取消";
            case "NO_SHOW" -> "未到店";
            default -> status;
        };
    }

    public String yuan(int cents) {
        return String.format("%.2f 元", cents / 100.0);
    }

    private void writeTx(Long guestId, String txType, int amount, int balanceAfter, String bizType, Long bizId,
                         String operator, String remark) {
        GuestTransaction tx = new GuestTransaction();
        tx.setGuestId(guestId);
        tx.setTxNo(noGenerator.next("TX"));
        tx.setTxType(txType);
        tx.setAmountCents(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setBizType(bizType);
        tx.setBizId(bizId);
        tx.setOperator(operator);
        tx.setRemark(remark);
        txDao.insertReturnKey(Mappers.transaction(tx));
    }
}
