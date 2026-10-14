package com.cqucc.hotel.ai.agent;

import com.cqucc.hotel.common.MaskUtil;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.Reservation;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体工具集（酒店场景）。
 * <p>把系统真实的业务查询能力封装为模型可调用的只读工具：客人档案、房态与可售房、
 * 房价试算、预订情况、营收统计、消费项目与房价策略。所有工具只读，
 * 写操作（落单、扣款、生成报价单）一律走"生成草稿 → 人工确认"。</p>
 */
@Component
public class HotelTools {

    private final BusinessDao businessDao;
    private final CrudDao<Reservation> reservationDao;
    private final CrudDao<Room> roomDao;
    private final ObjectMapper json;

    private final Map<String, ToolDefinition> registry = new LinkedHashMap<>();

    public HotelTools(BusinessDao businessDao, CrudDao<Reservation> reservationCrudDao,
                      CrudDao<Room> roomCrudDao, ObjectMapper json) {
        this.businessDao = businessDao;
        this.reservationDao = reservationCrudDao;
        this.roomDao = roomCrudDao;
        this.json = json;
        registerAll();
    }

    private void registerAll() {
        register(new ToolDefinition("query_guest",
                "按姓名、手机号或客人编号查询客人档案，返回会员等级、累计入住次数、累计消费、预存余额与历史入住记录。",
                """
                {"type":"object","properties":{"keyword":{"type":"string","description":"姓名/手机号/客人编号，例如 张伟 或 13900000001"}},"required":["keyword"]}
                """,
                this::queryGuest));

        register(new ToolDefinition("query_room_availability",
                "查询指定房型在某个日期区间是否还有可售房间，返回房型名称、门市价、会员价、可售数量与空闲房号。",
                """
                {"type":"object","properties":{"typeName":{"type":"string","description":"房型名称关键字，如 标准大床房、套房"},"checkIn":{"type":"string","description":"入住日期 yyyy-MM-dd"},"checkOut":{"type":"string","description":"离店日期 yyyy-MM-dd"}},"required":["typeName","checkIn","checkOut"]}
                """,
                this::queryAvailability));

        register(new ToolDefinition("room_status",
                "查询当前房态统计：总房数、空闲、在住、待打扫、维修数量，以及各房型出租情况与出租率。",
                """
                {"type":"object","properties":{}}
                """,
                this::roomStatus));

        register(new ToolDefinition("reservation_query",
                "查询预订单，可按状态（BOOKED/CHECKED_IN/CHECKED_OUT/CANCELLED/NO_SHOW）或客人姓名筛选，返回单号、房型、入住离店日期、金额与状态。",
                """
                {"type":"object","properties":{"status":{"type":"string","enum":["BOOKED","CHECKED_IN","CHECKED_OUT","CANCELLED","NO_SHOW"]},"guestKeyword":{"type":"string","description":"客人姓名关键字"}}}
                """,
                this::reservationQuery));

        register(new ToolDefinition("revenue_stats",
                "查询营收统计：累计营收、今日营收、未结算金额、客人预存余额总额、餐饮洗衣等消费收入，以及近 N 天营收走势。",
                """
                {"type":"object","properties":{"days":{"type":"integer","description":"统计最近多少天的走势，默认 7"}}}
                """,
                this::revenueStats));

        register(new ToolDefinition("consume_items",
                "查询可登记的消费项目（餐饮、洗衣、迷你吧、叫醒服务、加床等）及其价格，用于向客人报价。",
                """
                {"type":"object","properties":{"category":{"type":"string","description":"分类，如 餐饮、洗衣、迷你吧"}}}
                """,
                this::consumeItems));

        register(new ToolDefinition("rate_plans",
                "查询房价策略（周末价、节假日价等按日期区间的定价规则），返回房型、生效区间与价格。",
                """
                {"type":"object","properties":{"typeName":{"type":"string","description":"房型名称关键字"}}}
                """,
                this::ratePlans));

        register(new ToolDefinition("hotel_info",
                "查询酒店基础信息：房型数量与可住人数、酒店规模、设施配置，用于回答“你们有哪些房型”这类问题。",
                """
                {"type":"object","properties":{}}
                """,
                this::hotelInfo));
    }

    private void register(ToolDefinition def) {
        registry.put(def.name(), def);
    }

    public ArrayNode toolSchemaArray() {
        ArrayNode array = json.createArrayNode();
        for (ToolDefinition def : registry.values()) {
            ObjectNode tool = array.addObject();
            tool.put("type", "function");
            ObjectNode fn = tool.putObject("function");
            fn.put("name", def.name());
            fn.put("description", def.description());
            try {
                fn.set("parameters", json.readTree(def.parameters()));
            } catch (Exception e) {
                fn.putObject("parameters").put("type", "object");
            }
        }
        return array;
    }

    public String toolDescriptionText() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition def : registry.values()) {
            sb.append("- ").append(def.name()).append("：").append(def.description()).append('\n');
        }
        return sb.toString();
    }

    public boolean exists(String name) {
        return registry.containsKey(name);
    }

    public String execute(String name, String argumentsJson) {
        ToolDefinition def = registry.get(name);
        if (def == null) {
            return "错误：工具 " + name + " 不存在";
        }
        try {
            return def.executor().execute(argumentsJson);
        } catch (Exception e) {
            return "工具执行失败：" + e.getMessage();
        }
    }

    // ---------------- 工具实现 ----------------

    private String queryGuest(String argsJson) {
        String keyword = text(argsJson, "keyword");
        if (keyword.isBlank()) {
            return "错误：keyword 不能为空";
        }
        List<Guest> guests = businessDao.searchGuests(keyword, null, 1, 5);
        if (guests.isEmpty()) {
            return "未查询到匹配的客人（关键字：" + keyword + "）";
        }
        StringBuilder sb = new StringBuilder("查询到 " + guests.size() + " 位客人：\n");
        for (Guest g : guests) {
            sb.append("· ").append(MaskUtil.maskName(g.getRealName()))
                    .append("（客人编号 ").append(g.getGuestNo()).append("，")
                    .append(levelName(g.getMemberLevel())).append("，手机 ")
                    .append(MaskUtil.maskPhone(g.getPhone())).append("）\n")
                    .append("  累计入住 ").append(g.getTotalStayCount()).append(" 次，累计消费 ")
                    .append(yuan(g.getTotalConsumeCents())).append("，预存余额 ")
                    .append(yuan(g.getBalanceCents())).append("，积分 ").append(g.getPoints()).append("\n")
                    .append("  备注：").append(g.getRemark() == null ? "无" : g.getRemark()).append("\n");
            for (Map<String, Object> stay : businessDao.guestStayHistory(g.getId(), 3)) {
                sb.append("  历史入住：").append(stay.get("reservation_no"))
                        .append(" ").append(stay.get("type_name"))
                        .append(" ").append(stay.get("check_in_date")).append(" 至 ").append(stay.get("check_out_date"))
                        .append(" ").append(statusText(String.valueOf(stay.get("status")))).append("\n");
            }
        }
        return sb.toString();
    }

    private String queryAvailability(String argsJson) {
        String typeName = text(argsJson, "typeName");
        String checkIn = text(argsJson, "checkIn");
        String checkOut = text(argsJson, "checkOut");
        if (checkIn.isBlank() || checkOut.isBlank()) {
            return "错误：必须提供入住与离店日期（yyyy-MM-dd）";
        }
        LocalDate in;
        LocalDate out;
        try {
            in = LocalDate.parse(checkIn);
            out = LocalDate.parse(checkOut);
        } catch (Exception e) {
            return "错误：日期格式应为 yyyy-MM-dd";
        }
        List<RoomType> types = businessDao.listRoomTypes("ON_SALE", typeName.isBlank() ? null : typeName);
        if (types.isEmpty()) {
            return "未找到名称包含“" + typeName + "”的在售房型";
        }
        StringBuilder sb = new StringBuilder(checkIn + " 至 " + checkOut + " 的可售情况：\n");
        for (RoomType type : types) {
            List<Room> available = businessDao.findAvailableRooms(type.getId(), in, out, 20);
            var plan = businessDao.findRatePlan(type.getId(), in);
            int price = plan == null ? type.getPriceCents() : plan.getPriceCents();
            sb.append("· ").append(type.getTypeName())
                    .append("（可住 ").append(type.getCapacity()).append(" 人，")
                    .append(type.getBedType()).append("）\n")
                    .append("  门市价 ").append(yuan(type.getPriceCents()))
                    .append("，会员价 ").append(yuan(type.getMemberPriceCents()))
                    .append(plan == null ? "" : "，该日期执行 " + plan.getPlanName() + " " + yuan(plan.getPriceCents()))
                    .append("\n")
                    .append("  可售 ").append(available.size()).append(" 间");
            if (!available.isEmpty()) {
                List<String> nos = new ArrayList<>();
                for (Room r : available.subList(0, Math.min(6, available.size()))) {
                    nos.add(r.getRoomNo());
                }
                sb.append("，房号示例：").append(String.join("、", nos));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String roomStatus(String argsJson) {
        Map<String, Object> summary = businessDao.dashboardSummary();
        List<Map<String, Object>> byStatus = businessDao.roomStatusDistribution();
        StringBuilder sb = new StringBuilder("房态统计（实时）：\n");
        sb.append("· 总房数 ").append(summary.get("roomTotal"))
                .append("，空闲 ").append(summary.get("roomFree"))
                .append("，在住 ").append(summary.get("roomOccupied"))
                .append("，待打扫 ").append(summary.get("roomCleaning"))
                .append("，维修 ").append(summary.get("roomMaintenance")).append("\n");
        sb.append("· 当前出租率 ").append(summary.get("occupancyRate")).append("%\n");
        sb.append("· 今日应到店 ").append(summary.get("guestToday"))
                .append(" 单，在住 ").append(summary.get("checkedIn"))
                .append(" 单，今日应离店 ").append(summary.get("checkoutToday")).append(" 单\n");
        sb.append("· 房态明细：");
        for (Map<String, Object> row : byStatus) {
            sb.append(statusText(String.valueOf(row.get("status")))).append(" ").append(row.get("cnt")).append(" 间；");
        }
        sb.append("\n· 各房型情况：\n");
        for (Map<String, Object> row : businessDao.roomTypeStats()) {
            sb.append("  ").append(row.get("type_name"))
                    .append("：共 ").append(row.get("room_count")).append(" 间，在住 ")
                    .append(row.get("occupied_count")).append(" 间，累计房费收入 ")
                    .append(yuan(longOf(row.get("room_revenue_cents")))).append("\n");
        }
        return sb.toString();
    }

    private String reservationQuery(String argsJson) {
        String status = text(argsJson, "status");
        String guestKeyword = text(argsJson, "guestKeyword");
        Long guestId = null;
        if (!guestKeyword.isBlank()) {
            List<Guest> guests = businessDao.searchGuests(guestKeyword, null, 1, 1);
            if (guests.isEmpty()) {
                return "未找到客人：" + guestKeyword;
            }
            guestId = guests.get(0).getId();
        }
        List<Reservation> list = businessDao.listReservations(status.isBlank() ? null : status, guestId, null, null, 1, 10);
        if (list.isEmpty()) {
            return "没有符合条件的预订单";
        }
        StringBuilder sb = new StringBuilder("共 " + list.size() + " 条预订单：\n");
        for (Reservation r : list) {
            sb.append("· ").append(r.getReservationNo())
                    .append("  客人 ").append(MaskUtil.maskName(r.getGuestName()))
                    .append("，房型 ").append(r.getTypeName())
                    .append("，房间 ").append(r.getRoomNo() == null ? "未分配" : r.getRoomNo()).append("\n")
                    .append("  ").append(r.getCheckInDate()).append(" 至 ").append(r.getCheckOutDate())
                    .append("（").append(r.getNights()).append(" 晚，").append(r.getRoomCount()).append(" 间）")
                    .append("，单价 ").append(yuan(r.getUnitPriceCents()))
                    .append("，总额 ").append(yuan(r.getTotalCents()))
                    .append("，押金 ").append(yuan(r.getDepositCents()))
                    .append("，状态 ").append(statusText(r.getStatus())).append("\n");
        }
        return sb.toString();
    }

    private String revenueStats(String argsJson) {
        int days = intValue(argsJson, "days", 7);
        Map<String, Object> summary = businessDao.dashboardSummary();
        StringBuilder sb = new StringBuilder("经营统计（来源：bill / guest / reservation 表）：\n");
        sb.append("· 累计营收 ").append(yuan(longOf(summary.get("revenueTotalCents"))))
                .append("，今日营收 ").append(yuan(longOf(summary.get("revenueTodayCents")))).append("\n");
        sb.append("· 未结算金额 ").append(yuan(longOf(summary.get("unsettledCents"))))
                .append("，客人预存余额总额 ").append(yuan(longOf(summary.get("depositBalanceCents")))).append("\n");
        sb.append("· 餐饮/洗衣等消费收入 ").append(yuan(longOf(summary.get("consumeTotalCents")))).append("\n");
        sb.append("· 出租率 ").append(summary.get("occupancyRate")).append("%\n");
        sb.append("· 近 ").append(days).append(" 天营收走势：\n");
        for (Map<String, Object> row : businessDao.revenueTrend(days)) {
            sb.append("  ").append(row.get("day"))
                    .append(" 结算 ").append(row.get("bill_count")).append(" 单，营收 ")
                    .append(yuan(longOf(row.get("revenue_cents"))))
                    .append("（房费 ").append(yuan(longOf(row.get("room_cents"))))
                    .append("，消费 ").append(yuan(longOf(row.get("consume_cents")))).append("）\n");
        }
        return sb.toString();
    }

    private String consumeItems(String argsJson) {
        String category = text(argsJson, "category");
        List<com.cqucc.hotel.entity.ConsumeItem> items =
                businessDao.listConsumeItems(category.isBlank() ? null : category, "ON_SALE");
        if (items.isEmpty()) {
            return "没有符合条件的消费项目";
        }
        StringBuilder sb = new StringBuilder("可登记消费项目：\n");
        for (var item : items) {
            sb.append("· ").append(item.getItemName())
                    .append("（").append(item.getCategory()).append("）")
                    .append(" 单价 ").append(yuan(item.getPriceCents()))
                    .append("/").append(item.getUnit());
            if (item.getDescription() != null) {
                sb.append("，").append(item.getDescription());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String ratePlans(String argsJson) {
        String typeName = text(argsJson, "typeName");
        Long typeId = null;
        if (!typeName.isBlank()) {
            List<RoomType> types = businessDao.listRoomTypes(null, typeName);
            if (types.isEmpty()) {
                return "未找到名称包含“" + typeName + "”的房型";
            }
            typeId = types.get(0).getId();
        }
        List<com.cqucc.hotel.entity.RatePlan> plans = businessDao.listRatePlans(typeId, "ACTIVE");
        if (plans.isEmpty()) {
            return "当前没有生效的房价策略，全部按门市价/会员价执行";
        }
        StringBuilder sb = new StringBuilder("生效中的房价策略：\n");
        for (var plan : plans) {
            sb.append("· ").append(plan.getTypeName()).append(" | ").append(plan.getPlanName())
                    .append("：").append(plan.getStartDate()).append(" 至 ").append(plan.getEndDate())
                    .append("，价格 ").append(yuan(plan.getPriceCents())).append("/晚\n");
        }
        return sb.toString();
    }

    private String hotelInfo(String argsJson) {
        List<RoomType> types = businessDao.listRoomTypes("ON_SALE", null);
        List<Room> rooms = businessDao.listRooms(null, null, null, null);
        StringBuilder sb = new StringBuilder("酒店基础信息：\n");
        sb.append("· 在售房型 ").append(types.size()).append(" 种，客房共 ").append(rooms.size()).append(" 间\n");
        for (RoomType type : types) {
            sb.append("  - ").append(type.getTypeName())
                    .append("：").append(type.getAreaSqm()).append(" 平方米，")
                    .append(type.getBedType()).append("，可住 ").append(type.getCapacity()).append(" 人，")
                    .append("门市价 ").append(yuan(type.getPriceCents())).append("/晚，")
                    .append("设施：").append(type.getFacilities() == null ? "见房型说明" : type.getFacilities()).append("\n");
        }
        sb.append("· 知识库文档 ").append(businessDao.dashboardSummary() == null ? "" : "")
                .append("（可回答房价、入住、退房、会员、投诉等制度类问题）");
        return sb.toString();
    }

    // ---------------- 辅助 ----------------

    private String text(String argsJson, String field) {
        try {
            JsonNode node = json.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            JsonNode v = node.get(field);
            return v == null || v.isNull() ? "" : v.asText("").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private int intValue(String argsJson, String field, int defaultValue) {
        try {
            JsonNode node = json.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            JsonNode v = node.get(field);
            return v == null || !v.canConvertToInt() ? defaultValue : v.asInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String yuan(Integer cents) {
        return cents == null ? "0.00 元" : String.format("%.2f 元", cents / 100.0);
    }

    private String yuan(long cents) {
        return String.format("%.2f 元", cents / 100.0);
    }

    private long longOf(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private String levelName(String level) {
        if (level == null) {
            return "普通会员";
        }
        return switch (level) {
            case "GOLD" -> "黄金会员";
            case "SILVER" -> "白银会员";
            default -> "普通会员";
        };
    }

    private String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "FREE" -> "空闲";
            case "OCCUPIED" -> "在住";
            case "CLEANING" -> "待打扫";
            case "MAINTENANCE" -> "维修";
            case "BOOKED" -> "已预订";
            case "CHECKED_IN" -> "已入住";
            case "CHECKED_OUT" -> "已退房";
            case "CANCELLED" -> "已取消";
            case "NO_SHOW" -> "未到店";
            default -> status;
        };
    }
}
