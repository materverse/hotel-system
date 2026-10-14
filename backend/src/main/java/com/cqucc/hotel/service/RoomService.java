package com.cqucc.hotel.service;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.RatePlan;
import com.cqucc.hotel.entity.Room;
import com.cqucc.hotel.entity.RoomType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客房服务：房型、客房、房态、房价策略。
 * <p>对应任务书"客房信息设置、房态管理、房价设置"的要求。
 * 房态流转：FREE → BOOKED（被预订）→ OCCUPIED（在住）→ CLEANING（待打扫）→ FREE（打扫完成）；
 * MAINTENANCE 为维修状态，不参与售卖。</p>
 */
@Service
public class RoomService {

    private final BusinessDao businessDao;
    private final CrudDao<Room> roomDao;
    private final CrudDao<RoomType> roomTypeDao;
    private final CrudDao<RatePlan> ratePlanDao;

    public RoomService(BusinessDao businessDao, CrudDao<Room> roomCrudDao, CrudDao<RoomType> roomTypeDao,
                       CrudDao<RatePlan> ratePlanDao) {
        this.businessDao = businessDao;
        this.roomDao = roomCrudDao;
        this.roomTypeDao = roomTypeDao;
        this.ratePlanDao = ratePlanDao;
    }

    // ------------------------------------------------------------------
    // 房型
    // ------------------------------------------------------------------

    public List<RoomType> listTypes(String status, String keyword) {
        return businessDao.listRoomTypes(status, keyword);
    }

    public RoomType typeDetail(Long id) {
        RoomType type = businessDao.findRoomTypeById(id);
        if (type == null) {
            throw new BusinessException("房型不存在");
        }
        return type;
    }

    @Transactional
    public Long saveType(RoomType type) {
        if (type.getTypeName() == null || type.getTypeName().isBlank()) {
            throw new BusinessException("房型名称不能为空");
        }
        if (type.getPriceCents() == null || type.getPriceCents() <= 0) {
            throw new BusinessException("门市价必须大于 0");
        }
        if (type.getCapacity() == null || type.getCapacity() <= 0) {
            type.setCapacity(2);
        }
        if (type.getMemberPriceCents() == null || type.getMemberPriceCents() <= 0) {
            type.setMemberPriceCents(type.getPriceCents());
        }
        if (type.getId() == null) {
            type.setStatus(type.getStatus() == null ? "ON_SALE" : type.getStatus());
            return roomTypeDao.insertReturnKey(Mappers.roomType(type));
        }
        roomTypeDao.updateById(type.getId(), Mappers.roomType(type));
        return type.getId();
    }

    /** 房型上架/停售。 */
    public void changeTypeStatus(Long typeId, String status) {
        if (!List.of("ON_SALE", "OFF_SALE").contains(status)) {
            throw new BusinessException("房型状态取值不合法");
        }
        typeDetail(typeId);
        roomTypeDao.updateById(typeId, Map.of("status", status));
    }

    // ------------------------------------------------------------------
    // 客房
    // ------------------------------------------------------------------

    public List<Room> listRooms(String status, Long typeId, Integer floorNo, String keyword) {
        return businessDao.listRooms(status, typeId, floorNo, keyword);
    }

    public Room roomDetail(Long roomId) {
        Room room = businessDao.findRoomById(roomId);
        if (room == null) {
            throw new BusinessException("客房不存在");
        }
        return room;
    }

    @Transactional
    public Long saveRoom(Room room) {
        if (room.getRoomNo() == null || room.getRoomNo().isBlank()) {
            throw new BusinessException("房间号不能为空");
        }
        if (room.getTypeId() == null) {
            throw new BusinessException("请选择房型");
        }
        typeDetail(room.getTypeId());
        if (room.getId() == null) {
            room.setStatus(room.getStatus() == null ? "FREE" : room.getStatus());
            room.setCleanStatus(room.getCleanStatus() == null ? "CLEAN" : room.getCleanStatus());
            if (room.getFloorNo() == null) {
                room.setFloorNo(1);
            }
            try {
                return roomDao.insertReturnKey(Mappers.room(room));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(409, "房间号 " + room.getRoomNo() + " 已存在");
            }
        }
        Room exist = roomDao.findById(room.getId());
        if (exist == null) {
            throw new BusinessException("客房不存在");
        }
        // 在住房间不允许直接改房型与房态
        if ("OCCUPIED".equals(exist.getStatus()) && (room.getStatus() != null && !"OCCUPIED".equals(room.getStatus()))) {
            throw new BusinessException("房间 " + exist.getRoomNo() + " 有客人入住，请先办理退房再修改房态");
        }
        roomDao.updateById(room.getId(), Mappers.room(room));
        return room.getId();
    }

    /** 批量新增房间，例如 8 楼的 8801-8810。 */
    @Transactional
    public int batchCreateRooms(int floorNo, Long typeId, String roomNoPrefix, int startNo, int endNo, String orientation) {
        if (startNo > endNo) {
            throw new BusinessException("起始房号不能大于结束房号");
        }
        if (endNo - startNo > 200) {
            throw new BusinessException("单次批量新增不能超过 200 间");
        }
        typeDetail(typeId);
        int created = 0;
        for (int i = startNo; i <= endNo; i++) {
            String roomNo = roomNoPrefix + String.format("%02d", i);
            if (!roomDao.findList(CrudDao.cond("room_no", roomNo)).isEmpty()) {
                continue;
            }
            Room room = new Room();
            room.setRoomNo(roomNo);
            room.setFloorNo(floorNo);
            room.setTypeId(typeId);
            room.setOrientation(orientation);
            room.setStatus("FREE");
            room.setCleanStatus("CLEAN");
            roomDao.insertReturnKey(Mappers.room(room));
            created++;
        }
        return created;
    }

    /**
     * 修改房态（管理员/前台/客房服务）。
     * <p>在住房间必须走退房流程才能变空，避免误操作把有客人的房间标成空闲。</p>
     */
    @Transactional
    public void changeStatus(Long roomId, String status, String remark) {
        if (!List.of("FREE", "OCCUPIED", "CLEANING", "MAINTENANCE", "BOOKED").contains(status)) {
            throw new BusinessException("房态取值不合法");
        }
        Room room = roomDetail(roomId);
        if ("OCCUPIED".equals(room.getStatus()) && !"OCCUPIED".equals(status)) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 有客人入住，请通过「办理退房」释放房间");
        }
        if ("MAINTENANCE".equals(status) && (remark == null || remark.isBlank())) {
            throw new BusinessException("标记维修必须填写原因，便于客房服务跟进");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status);
        if (remark != null && !remark.isBlank()) {
            values.put("remark", remark);
        }
        if ("CLEANING".equals(status)) {
            values.put("clean_status", "DIRTY");
        }
        if ("FREE".equals(status)) {
            values.put("clean_status", "CLEAN");
        }
        roomDao.updateById(roomId, values);
    }

    /** 客房服务反馈打扫完成：待打扫 → 空闲。 */
    @Transactional
    public void finishCleaning(Long roomId, String operator) {
        Room room = roomDetail(roomId);
        if (!"CLEANING".equals(room.getStatus())) {
            throw new BusinessException("房间 " + room.getRoomNo() + " 当前不是待打扫状态（" + room.getStatus() + "）");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", "FREE");
        values.put("clean_status", "CLEAN");
        roomDao.updateById(roomId, values);
    }

    /** 房态统计：按状态、按楼层分组。 */
    public Map<String, Object> statusOverview() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("byStatus", businessDao.roomStatusDistribution());
        map.put("rooms", businessDao.listRooms(null, null, null, null));
        map.put("occupancyRate", businessDao.occupancyRate(LocalDate.now()));
        map.put("byType", businessDao.roomTypeStats());
        return map;
    }

    // ------------------------------------------------------------------
    // 房价策略
    // ------------------------------------------------------------------

    public List<RatePlan> listRatePlans(Long typeId, String status) {
        return businessDao.listRatePlans(typeId, status);
    }

    @Transactional
    public Long saveRatePlan(RatePlan plan) {
        if (plan.getTypeId() == null) {
            throw new BusinessException("请选择房型");
        }
        typeDetail(plan.getTypeId());
        if (plan.getPlanName() == null || plan.getPlanName().isBlank()) {
            throw new BusinessException("请填写策略名称");
        }
        if (plan.getStartDate() == null || plan.getEndDate() == null) {
            throw new BusinessException("请选择生效日期区间");
        }
        if (plan.getEndDate().isBefore(plan.getStartDate())) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        if (plan.getPriceCents() == null || plan.getPriceCents() <= 0) {
            throw new BusinessException("策略价格必须大于 0");
        }
        if (plan.getId() == null) {
            plan.setStatus(plan.getStatus() == null ? "ACTIVE" : plan.getStatus());
            return ratePlanDao.insertReturnKey(Mappers.ratePlan(plan));
        }
        ratePlanDao.updateById(plan.getId(), Mappers.ratePlan(plan));
        return plan.getId();
    }

    public void changeRatePlanStatus(Long id, String status) {
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException("房价策略状态取值不合法");
        }
        if (ratePlanDao.findById(id) == null) {
            throw new BusinessException("房价策略不存在");
        }
        ratePlanDao.updateById(id, Map.of("status", status));
    }
}
