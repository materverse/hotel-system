package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.BillService;
import com.cqucc.hotel.service.GuestService;
import com.cqucc.hotel.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客人管理接口。
 * <p>/api/guest/** 仅酒店员工可用；/api/me/** 为客人自助接口。</p>
 */
@RestController
@RequestMapping("/api")
public class GuestController {

    private final GuestService guestService;
    private final ReservationService reservationService;
    private final BillService billService;

    public GuestController(GuestService guestService, ReservationService reservationService, BillService billService) {
        this.guestService = guestService;
        this.reservationService = reservationService;
        this.billService = billService;
    }

    // ---------------- 客人自助 ----------------

    @GetMapping("/me/profile")
    public Result<Map<String, Object>> myProfile(HttpServletRequest request) {
        return Result.ok(guestService.center(requireGuestId(request)));
    }

    @PutMapping("/me/profile")
    public Result<Void> updateMyProfile(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        guestService.updateSelf(requireGuestId(request),
                body.get("address") == null ? null : String.valueOf(body.get("address")),
                body.get("remark") == null ? null : String.valueOf(body.get("remark")),
                body.get("idCard") == null ? null : String.valueOf(body.get("idCard")));
        return Result.ok("资料已更新", null);
    }

    /** 客人自助预存押金（可用于入住抵扣押金与结算）。 */
    @PostMapping("/me/deposit")
    public Result<Map<String, Object>> deposit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        int amount = intOf(body, "amountCents", "amount_cents");
        if (amount <= 0 && body.get("amountYuan") != null) {
            amount = (int) Math.round(Double.parseDouble(String.valueOf(body.get("amountYuan"))) * 100);
        }
        return Result.ok("预存成功", guestService.deposit(requireGuestId(request), amount, user.getRealName(),
                body.get("remark") == null ? "客人自助预存" : String.valueOf(body.get("remark"))));
    }

    @GetMapping("/me/transactions")
    public Result<Map<String, Object>> myTransactions(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) String txType,
                                                      HttpServletRequest request) {
        return Result.ok(guestService.pageTransactions(requireGuestId(request), txType, page, size));
    }

    /** 客人自助查询自己的预订（等价于 /api/reservation/list，但不需要员工角色）。 */
    @GetMapping("/me/reservations")
    public Result<Map<String, Object>> myReservations(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) String status,
                                                      HttpServletRequest request) {
        return Result.ok(reservationService.page(status, requireGuestId(request), null, null, page, size));
    }

    /** 客人自助查询自己的账单。 */
    @GetMapping("/me/bills")
    public Result<Map<String, Object>> myBills(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String status,
                                               HttpServletRequest request) {
        return Result.ok(billService.page(status, requireGuestId(request), page, size));
    }

    // ---------------- 员工端客人管理 ----------------

    @GetMapping("/guest/list")
    public Result<GuestService.PageResult<Guest>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String level,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return Result.ok(guestService.page(keyword, level, page, size));
    }

    @GetMapping("/guest/{id}")
    public Result<Map<String, Object>> profile(@PathVariable Long id) {
        return Result.ok(guestService.profile(id));
    }

    @PostMapping("/guest/save")
    public Result<Long> save(@RequestBody Guest guest) {
        if (guest.getId() == null) {
            return Result.ok("客人登记成功", guestService.create(guest));
        }
        guestService.update(guest.getId(), guest);
        return Result.ok("客人资料已更新", guest.getId());
    }

    @PutMapping("/guest/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        guestService.changeStatus(id, String.valueOf(body.get("status")));
        return Result.ok("客人状态已更新", null);
    }

    @GetMapping("/guest/{id}/transactions")
    public Result<Map<String, Object>> transactions(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String txType) {
        return Result.ok(guestService.pageTransactions(id, txType, page, size));
    }

    /** 前台代客预存。 */
    @PostMapping("/guest/{id}/deposit")
    public Result<Map<String, Object>> depositForGuest(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        int amount = intOf(body, "amountCents", "amount_cents");
        if (amount <= 0 && body.get("amountYuan") != null) {
            amount = (int) Math.round(Double.parseDouble(String.valueOf(body.get("amountYuan"))) * 100);
        }
        return Result.ok("预存成功", guestService.deposit(id, amount, user.getRealName(),
                body.get("remark") == null ? "前台代客预存" : String.valueOf(body.get("remark"))));
    }

    private Long requireGuestId(HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        if (user.getGuestId() == null) {
            throw new BusinessException(403, "当前账号未绑定客人档案，请使用客人账号登录或到前台登记");
        }
        return user.getGuestId();
    }

    private int intOf(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return Integer.parseInt(String.valueOf(v).trim());
            }
        }
        return 0;
    }
}
