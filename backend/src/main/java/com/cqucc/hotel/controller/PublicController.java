package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import com.cqucc.hotel.service.KnowledgeBaseService;
import com.cqucc.hotel.service.ReservationService;
import com.cqucc.hotel.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共查询接口（免登录）：房型、房态概览、房价试算。
 * <p>对应"客人未登录也能看房型与价格"的线上订房入口；
 * 只返回房型与房态等公开信息，不返回客人姓名等隐私数据。</p>
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final RoomService roomService;
    private final ReservationService reservationService;
    private final KnowledgeBaseService knowledgeBaseService;

    public PublicController(RoomService roomService, ReservationService reservationService,
                            KnowledgeBaseService knowledgeBaseService) {
        this.roomService = roomService;
        this.reservationService = reservationService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 在售房型。 */
    @GetMapping("/room-types")
    public Result<List<RoomType>> roomTypes(@RequestParam(required = false) String keyword) {
        return Result.ok(roomService.listTypes("ON_SALE", keyword));
    }

    /** 客房列表（公开视图：只包含房号、房型、价格、房态，不含客人信息）。 */
    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> rooms(@RequestParam(required = false) Long typeId,
                                                   @RequestParam(required = false) Integer floorNo) {
        List<Map<String, Object>> list = roomService.listRooms(null, typeId, floorNo, null).stream()
                .map(this::publicView)
                .toList();
        return Result.ok(list);
    }

    /** 首页概览：房量、房态规模、知识库规模。 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> map = new LinkedHashMap<>();
        long total = 0;
        long free = 0;
        long occupied = 0;
        long cleaning = 0;
        long maintenance = 0;
        for (Room room : roomService.listRooms(null, null, null, null)) {
            total++;
            switch (room.getStatus()) {
                case "FREE" -> free++;
                case "OCCUPIED" -> occupied++;
                case "CLEANING" -> cleaning++;
                case "MAINTENANCE" -> maintenance++;
                case "BOOKED" -> free++;
                default -> {
                }
            }
        }
        map.put("roomTotal", total);
        map.put("roomFree", free);
        map.put("roomOccupied", occupied);
        map.put("roomCleaning", cleaning);
        map.put("roomMaintenance", maintenance);
        map.put("roomTypeCount", roomService.listTypes("ON_SALE", null).size());
        map.put("knowledgeDocs", knowledgeBaseService.countDocs());
        map.put("knowledgeChunks", knowledgeBaseService.countChunks());
        return Result.ok(map);
    }

    /** 房价试算：给定房型与日期区间返回每晚单价、总价与押金。 */
    @GetMapping("/quote")
    public Result<ReservationService.QuoteDetail> quote(@RequestParam Long typeId,
                                                        @RequestParam String checkIn,
                                                        @RequestParam String checkOut,
                                                        @RequestParam(defaultValue = "1") int roomCount) {
        return Result.ok(reservationService.quote(typeId, LocalDate.parse(checkIn), LocalDate.parse(checkOut),
                roomCount, null));
    }

    private Map<String, Object> publicView(Room room) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", room.getId());
        map.put("roomNo", room.getRoomNo());
        map.put("floorNo", room.getFloorNo());
        map.put("typeId", room.getTypeId());
        map.put("typeName", room.getTypeName());
        map.put("orientation", room.getOrientation());
        map.put("status", room.getStatus());
        map.put("priceCents", room.getPriceCents());
        map.put("memberPriceCents", room.getMemberPriceCents());
        return map;
    }
}
