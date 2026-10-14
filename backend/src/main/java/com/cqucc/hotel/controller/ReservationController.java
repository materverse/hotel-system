package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.Reservation;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 预订查询与状态管理接口。
 * <p>员工可查询全部预订、标记未到店；客人只能看到自己的预订。</p>
 */
@RestController
@RequestMapping("/api/reservation")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) Long guestId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Long queryGuestId = user.isGuest() ? user.getGuestId() : guestId;
        LocalDate queryDate = date == null || date.isBlank() ? null : LocalDate.parse(date);
        return Result.ok(reservationService.page(status, queryGuestId, keyword, queryDate, page, size));
    }

    @GetMapping("/{id}")
    public Result<Reservation> detail(@PathVariable Long id) {
        return Result.ok(reservationService.detail(id));
    }

    /** 标记未到店（超过预订保留时间）。 */
    @PostMapping("/{id}/noshow")
    public Result<Void> noShow(@PathVariable Long id) {
        reservationService.markNoShow(id);
        return Result.ok("已标记为未到店，房间已释放", null);
    }
}
