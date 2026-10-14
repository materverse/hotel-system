package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.RatePlan;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客房、房型与房价策略管理接口（酒店员工可用）。
 */
@RestController
@RequestMapping("/api/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // ---------------- 客房 ----------------

    @GetMapping("/list")
    public Result<List<Room>> list(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) Long typeId,
                                   @RequestParam(required = false) Integer floorNo,
                                   @RequestParam(required = false) String keyword) {
        return Result.ok(roomService.listRooms(status, typeId, floorNo, keyword));
    }

    @GetMapping("/{id}")
    public Result<Room> detail(@PathVariable Long id) {
        return Result.ok(roomService.roomDetail(id));
    }

    @PostMapping("/save")
    public Result<Long> save(@RequestBody Room room) {
        return Result.ok("客房已保存", roomService.saveRoom(room));
    }

    /** 批量新增房间。 */
    @PostMapping("/batch")
    public Result<Integer> batch(@RequestBody Map<String, Object> body) {
        int floor = body.get("floorNo") == null ? 1 : Integer.parseInt(String.valueOf(body.get("floorNo")));
        Long typeId = Long.parseLong(String.valueOf(body.get("typeId")));
        String prefix = String.valueOf(body.get("roomNoPrefix"));
        int start = Integer.parseInt(String.valueOf(body.get("startNo")));
        int end = Integer.parseInt(String.valueOf(body.get("endNo")));
        String orientation = body.get("orientation") == null ? null : String.valueOf(body.get("orientation"));
        int created = roomService.batchCreateRooms(floor, typeId, prefix, start, end, orientation);
        return Result.ok("已新增 " + created + " 间客房", created);
    }

    /** 修改房态（FREE/OCCUPIED/CLEANING/MAINTENANCE/BOOKED）。 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        roomService.changeStatus(id, String.valueOf(body.get("status")),
                body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        return Result.ok("房态已更新", null);
    }

    /** 客房服务反馈打扫完成。 */
    @PostMapping("/clean/{id}")
    public Result<Void> finishCleaning(@PathVariable Long id, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        roomService.finishCleaning(id, user.getRealName());
        return Result.ok("已反馈打扫完成，房间转为空闲", null);
    }

    /** 房态总览（含按楼层分组与出租率）。 */
    @GetMapping("/status-overview")
    public Result<Map<String, Object>> statusOverview() {
        return Result.ok(roomService.statusOverview());
    }

    // ---------------- 房型 ----------------

    @GetMapping("/types")
    public Result<List<RoomType>> types(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(roomService.listTypes(status, keyword));
    }

    @PostMapping("/types/save")
    public Result<Long> saveType(@RequestBody RoomType type) {
        return Result.ok("房型已保存", roomService.saveType(type));
    }

    @PutMapping("/types/{id}/status")
    public Result<Void> changeTypeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        roomService.changeTypeStatus(id, String.valueOf(body.get("status")));
        return Result.ok("房型状态已更新", null);
    }

    // ---------------- 房价策略 ----------------

    @GetMapping("/rate-plans")
    public Result<List<RatePlan>> ratePlans(@RequestParam(required = false) Long typeId,
                                            @RequestParam(required = false) String status) {
        return Result.ok(roomService.listRatePlans(typeId, status));
    }

    @PostMapping("/rate-plans/save")
    public Result<Long> saveRatePlan(@RequestBody RatePlan plan) {
        return Result.ok("房价策略已保存", roomService.saveRatePlan(plan));
    }

    @PutMapping("/rate-plans/{id}/status")
    public Result<Void> changeRatePlanStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        roomService.changeRatePlanStatus(id, String.valueOf(body.get("status")));
        return Result.ok("房价策略状态已更新", null);
    }
}
