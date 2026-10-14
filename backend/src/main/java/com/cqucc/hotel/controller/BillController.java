package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.Bill;
import com.cqucc.hotel.entity.ConsumeItem;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.BillService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账单与消费项目接口（酒店员工可用）。
 * <p>约定：账单相关操作一律以<b>账单 ID</b>（bill.id）作为路径参数，
 * 前端通过「按预订查询账单」接口拿到账单 ID 后再调用登记消费、优惠、结算。
 * 这样避免把"预订 ID"与"账单 ID"混用造成 404/400（开发过程中曾出现该问题，
 * 详见测试报告中的缺陷记录 D-02）。</p>
 * <p>为兼容按预订 ID 调用的旧写法，本控制器同时提供 {@code /by-reservation/**}
 * 形式的重载入口，内部会先解析出账单 ID。</p>
 */
@RestController
@RequestMapping("/api/bill")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long guestId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(billService.page(status, guestId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Bill> detail(@PathVariable Long id) {
        return Result.ok(billService.detail(id));
    }

    /** 按预订 ID 查账单（前台办理退房后进入结算用）。 */
    @GetMapping("/by-reservation/{reservationId}")
    public Result<Bill> byReservation(@PathVariable Long reservationId) {
        return Result.ok(billService.detailByReservation(reservationId));
    }

    /** 按预订 ID 登记消费（等价于 /{billId}/consume，便于前台从订单直接操作）。 */
    @PostMapping("/by-reservation/{reservationId}/consume")
    public Result<Map<String, Object>> consumeByReservation(@PathVariable Long reservationId,
                                                            @RequestBody Map<String, Object> body,
                                                            HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Bill bill = billService.detailByReservation(reservationId);
        return addConsume(bill.getId(), body, user);
    }

    /** 按账单 ID 登记消费。 */
    @PostMapping("/{id}/consume")
    public Result<Map<String, Object>> consume(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        return addConsume(id, body, AuthInterceptor.currentUser(request));
    }

    private Result<Map<String, Object>> addConsume(Long billId, Map<String, Object> body, LoginUser user) {
        Object itemId = body.get("consumeItemId") != null ? body.get("consumeItemId") : body.get("consume_item_id");
        if (itemId == null) {
            throw new BusinessException("请选择消费项目");
        }
        Bill bill = billService.detail(billId);
        return Result.ok("消费已登记", billService.addConsume(bill.getReservationId(),
                Long.parseLong(String.valueOf(itemId)),
                body.get("quantity") == null ? 1 : Integer.parseInt(String.valueOf(body.get("quantity"))),
                body.get("remark") == null ? null : String.valueOf(body.get("remark")),
                user.getRealName()));
    }

    // ---------------- 消费项目维护 ----------------

    @GetMapping("/consume-items")
    public Result<List<ConsumeItem>> consumeItems(@RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(billService.listConsumeItems(category, status));
    }

    @PostMapping("/consume-items/save")
    public Result<Long> saveConsumeItem(@RequestBody ConsumeItem item) {
        if (item.getId() != null) {
            ConsumeItem exist = billService.listConsumeItems(null, null).stream()
                    .filter(i -> i.getId().equals(item.getId())).findFirst().orElse(null);
            if (exist == null) {
                throw new BusinessException("消费项目不存在");
            }
            if (item.getStatus() == null) {
                item.setStatus(exist.getStatus());
            }
            if (item.getUnit() == null) {
                item.setUnit(exist.getUnit());
            }
        }
        return Result.ok("消费项目已保存", billService.saveConsumeItem(item));
    }
}
