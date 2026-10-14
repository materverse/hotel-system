package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.BillService;
import com.cqucc.hotel.service.GuestService;
import com.cqucc.hotel.service.ReservationService;
import com.cqucc.hotel.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台业务接口（管理员 / 前台 / 客房服务可用）。
 * <p>覆盖核心业务闭环：查客人 → 试算房价 → 下单预订 → 办理入住 → 登记消费 → 办理退房 → 结算。</p>
 */
@RestController
@RequestMapping("/api/front")
public class FrontDeskController {

    private final ReservationService reservationService;
    private final BillService billService;
    private final GuestService guestService;
    private final RoomService roomService;
    private final BusinessDao businessDao;

    public FrontDeskController(ReservationService reservationService, BillService billService,
                               GuestService guestService, RoomService roomService, BusinessDao businessDao) {
        this.reservationService = reservationService;
        this.billService = billService;
        this.guestService = guestService;
        this.roomService = roomService;
        this.businessDao = businessDao;
    }

    /** 前台当日看板：应到店、在住、应离店与出租率。 */
    @GetMapping("/board")
    public Result<Map<String, Object>> board() {
        return Result.ok(reservationService.todayBoard());
    }

    /** 客人检索（登记入住时先找人）。 */
    @GetMapping("/guests")
    public Result<GuestService.PageResult<Guest>> guests(@RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return Result.ok(guestService.page(keyword, null, page, size));
    }

    /** 现场登记客人（散客入住）。 */
    @PostMapping("/guests")
    public Result<Long> createGuest(@RequestBody Guest guest) {
        return Result.ok("客人登记成功", guestService.create(guest));
    }

    /** 房价试算。 */
    @GetMapping("/quote")
    public Result<ReservationService.QuoteDetail> quote(@RequestParam Long typeId,
                                                        @RequestParam String checkIn,
                                                        @RequestParam String checkOut,
                                                        @RequestParam(defaultValue = "1") int roomCount,
                                                        @RequestParam(required = false) Long guestId) {
        return Result.ok(reservationService.quote(typeId, LocalDate.parse(checkIn), LocalDate.parse(checkOut),
                roomCount, guestId));
    }

    /** 某房型在指定区间可分配的房间。 */
    @GetMapping("/rooms/available")
    public Result<List<com.cqucc.hotel.entity.Room>> availableRooms(@RequestParam Long typeId,
                                                                    @RequestParam String checkIn,
                                                                    @RequestParam String checkOut) {
        return Result.ok(businessDao.findAvailableRooms(typeId, LocalDate.parse(checkIn),
                LocalDate.parse(checkOut), 50));
    }

    /** 创建预订。 */
    @PostMapping("/reservations")
    public Result<Map<String, Object>> book(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Long guestId = longOf(body.get("guestId"), body.get("guest_id"));
        Long typeId = longOf(body.get("typeId"), body.get("type_id"));
        Long roomId = longOf(body.get("roomId"), body.get("room_id"));
        if (guestId == null || typeId == null) {
            throw new BusinessException("请选择客人与房型");
        }
        return Result.ok("预订成功", reservationService.book(
                guestId, typeId, roomId,
                LocalDate.parse(str(body, "checkInDate", "check_in_date")),
                LocalDate.parse(str(body, "checkOutDate", "check_out_date")),
                intOf(body, 1, "roomCount", "room_count"),
                intOf(body, 1, "guestCount", "guest_count"),
                str(body, "channel"),
                str(body, "specialRequest", "special_request"),
                intOfNullable(body, "depositCents", "deposit_cents"),
                user.getRealName()));
    }

    /** 办理入住（分配房间 + 收押金）。 */
    @PostMapping("/reservations/{id}/checkin")
    public Result<Map<String, Object>> checkIn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                               HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Long roomId = body == null ? null : longOf(body.get("roomId"), body.get("room_id"));
        String idCard = body == null ? null : str(body, "idCard", "id_card");
        return Result.ok("入住办理成功", reservationService.checkIn(id, roomId, idCard, user.getRealName()));
    }

    /** 办理退房。 */
    @PostMapping("/reservations/{id}/checkout")
    public Result<Map<String, Object>> checkOut(@PathVariable Long id, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        return Result.ok("退房成功，请完成账单结算", reservationService.checkOut(id, user.getRealName()));
    }

    /** 取消预订。 */
    @PostMapping("/reservations/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        String reason = body == null ? null : str(body, "reason");
        return Result.ok("预订已取消", reservationService.cancel(id, reason, user.getRealName()));
    }

    /** 登记消费（餐饮、洗衣、迷你吧等）。路径参数为账单 ID，内部转换为预订 ID 后处理。 */
    @PostMapping("/bills/{id}/consume")
    public Result<Map<String, Object>> addConsume(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Object itemId = body.get("consumeItemId") != null ? body.get("consumeItemId") : body.get("consume_item_id");
        if (itemId == null) {
            throw new BusinessException("请选择消费项目");
        }
        com.cqucc.hotel.entity.Bill bill = billService.detail(id);
        return Result.ok("消费已登记", billService.addConsume(bill.getReservationId(),
                Long.parseLong(String.valueOf(itemId)),
                intOf(body, 1, "quantity"),
                str(body, "remark"),
                user.getRealName()));
    }

    /** 账单优惠减免（需填原因）。 */
    @PostMapping("/bills/{id}/discount")
    public Result<Map<String, Object>> discount(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Integer amount = intOfNullable(body, "amountCents", "amount_cents");
        if (amount == null) {
            throw new BusinessException("请填写优惠金额（分）");
        }
        return Result.ok("优惠已登记", billService.discount(id, amount, str(body, "reason"), user.getRealName()));
    }

    /** 结算账单。 */
    @PostMapping("/bills/{id}/settle")
    public Result<Map<String, Object>> settle(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        String payType = body == null ? "CASH" : str(body, "payType", "pay_type");
        boolean refundToBalance = body == null || body.get("refundToBalance") == null
                || Boolean.parseBoolean(String.valueOf(body.get("refundToBalance")));
        return Result.ok("结算完成", billService.settle(id, payType, refundToBalance, user.getRealName()));
    }

    /** 在住客人的账单（前台查账用）。 */
    @GetMapping("/bills/by-reservation/{reservationId}")
    public Result<com.cqucc.hotel.entity.Bill> billByReservation(@PathVariable Long reservationId) {
        return Result.ok(billService.detailByReservation(reservationId));
    }

    /** 房态图（前台与客房服务共用）。 */
    @GetMapping("/rooms")
    public Result<Map<String, Object>> rooms() {
        return Result.ok(roomService.statusOverview());
    }

    // ---------------- 参数解析辅助 ----------------

    private String str(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    private Long longOf(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank()) {
                return Long.parseLong(String.valueOf(v));
            }
        }
        return null;
    }

    private int intOf(Map<String, Object> body, int defaultValue, String... keys) {
        Integer v = intOfNullable(body, keys);
        return v == null ? defaultValue : v;
    }

    private Integer intOfNullable(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return Integer.parseInt(String.valueOf(v).trim());
            }
        }
        return null;
    }

    /** 供前端获取房型下拉的快捷接口（与公共接口一致，但带上停售房型）。 */
    @GetMapping("/room-types")
    public Result<List<com.cqucc.hotel.entity.RoomType>> roomTypes() {
        return Result.ok(roomService.listTypes(null, null));
    }
}
