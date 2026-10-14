package com.cqucc.hotel.service;

import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.MaskUtil;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.GuestTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客人服务：档案管理、预存押金、消费流水、会员等级。
 * <p>任务书要求"入住客人信息设置"，客人档案是预订与结算的主体；
 * 证件号在列表与接口返回时统一脱敏，避免明文外泄。</p>
 */
@Service
public class GuestService {

    private final BusinessDao businessDao;
    private final CrudDao<Guest> guestDao;
    private final CrudDao<GuestTransaction> txDao;
    private final BizNoGenerator noGenerator;

    public GuestService(BusinessDao businessDao, CrudDao<Guest> guestCrudDao,
                        CrudDao<GuestTransaction> guestTransactionDao, BizNoGenerator noGenerator) {
        this.businessDao = businessDao;
        this.guestDao = guestCrudDao;
        this.txDao = guestTransactionDao;
        this.noGenerator = noGenerator;
    }

    /** 分页结果。 */
    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    public PageResult<Guest> page(String keyword, String level, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        List<Guest> list = businessDao.searchGuests(keyword, level, p, s);
        list.forEach(this::mask);
        return new PageResult<>(list, businessDao.countGuests(keyword, level), p, s);
    }

    public Guest detail(Long guestId) {
        Guest guest = businessDao.findGuestById(guestId);
        if (guest == null) {
            throw new BusinessException("客人档案不存在");
        }
        return mask(guest);
    }

    public Guest byUserId(Long userId) {
        Guest guest = businessDao.findGuestByUserId(userId);
        if (guest == null) {
            throw new BusinessException(403, "当前账号还没有客人档案，请到前台登记或使用客人自助注册");
        }
        return mask(guest);
    }

    /** 客人中心数据：档案 + 最近流水 + 最近订单 + 会员折扣。 */
    public Map<String, Object> center(Long guestId) {
        // 注意：入参是客人档案 ID（不是登录用户 ID），直接按档案 ID 查询
        Guest guest = detail(guestId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guest", guest);
        map.put("discountRate", discountRate(guest.getMemberLevel()));
        map.put("levelName", levelName(guest.getMemberLevel()));
        map.put("recentTransactions", businessDao.listGuestTransactions(guestId, null, 1, 5));
        map.put("recentReservations", businessDao.listReservations(null, guestId, null, null, 1, 5));
        map.put("stayHistory", businessDao.guestStayHistory(guestId, 5));
        map.put("nextLevelTip", nextLevelTip(guest));
        return map;
    }

    @Transactional
    public Long create(Guest guest) {
        if (guest.getRealName() == null || guest.getRealName().isBlank()) {
            throw new BusinessException("请填写客人姓名");
        }
        if (guest.getPhone() == null || !guest.getPhone().matches("1[3-9]\\d{9}")) {
            throw new BusinessException("请填写正确的手机号");
        }
        if (businessDao.findGuestByPhone(guest.getPhone()) != null) {
            throw new BusinessException(409, "该手机号已存在客人档案，请直接检索使用");
        }
        guest.setGuestNo("G" + LocalDate.now().getYear() + String.format("%04d", System.currentTimeMillis() % 10000));
        guest.setGender(guest.getGender() == null ? "男" : guest.getGender());
        guest.setIdType(guest.getIdType() == null ? "身份证" : guest.getIdType());
        guest.setMemberLevel(guest.getMemberLevel() == null ? "NORMAL" : guest.getMemberLevel());
        guest.setBalanceCents(guest.getBalanceCents() == null ? 0 : guest.getBalanceCents());
        guest.setPoints(0);
        guest.setTotalStayCount(0);
        guest.setTotalConsumeCents(0);
        guest.setStatus("NORMAL");
        return guestDao.insertReturnKey(Mappers.guest(guest));
    }

    @Transactional
    public void update(Long guestId, Guest form) {
        Guest exist = businessDao.findGuestById(guestId);
        if (exist == null) {
            throw new BusinessException("客人档案不存在");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (form.getRealName() != null && !form.getRealName().isBlank()) {
            values.put("real_name", form.getRealName());
        }
        if (form.getGender() != null) {
            values.put("gender", form.getGender());
        }
        if (form.getPhone() != null && !form.getPhone().isBlank() && !form.getPhone().equals(exist.getPhone())) {
            if (!form.getPhone().matches("1[3-9]\\d{9}")) {
                throw new BusinessException("手机号格式不正确");
            }
            if (businessDao.findGuestByPhone(form.getPhone()) != null) {
                throw new BusinessException(409, "该手机号已被其它客人使用");
            }
            values.put("phone", form.getPhone());
        }
        if (form.getIdCard() != null) {
            values.put("id_card", form.getIdCard());
        }
        if (form.getBirthday() != null) {
            values.put("birthday", form.getBirthday());
        }
        if (form.getAddress() != null) {
            values.put("address", form.getAddress());
        }
        if (form.getRemark() != null) {
            values.put("remark", form.getRemark());
        }
        if (form.getMemberLevel() != null && !form.getMemberLevel().isBlank()) {
            values.put("member_level", form.getMemberLevel());
        }
        if (values.isEmpty()) {
            throw new BusinessException("没有需要更新的字段");
        }
        guestDao.updateById(guestId, values);
    }

    /** 客人自助修改联系方式。 */
    public void updateSelf(Long guestId, String address, String remark, String idCard) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (address != null && !address.isBlank()) {
            values.put("address", address);
        }
        if (remark != null) {
            values.put("remark", remark);
        }
        if (idCard != null && !idCard.isBlank()) {
            if (!idCard.matches("\\d{17}[0-9Xx]")) {
                throw new BusinessException("证件号格式不正确（应为 18 位身份证号）");
            }
            values.put("id_card", idCard);
        }
        if (values.isEmpty()) {
            throw new BusinessException("没有需要更新的内容");
        }
        guestDao.updateById(guestId, values);
    }

    /** 拉黑 / 解除黑名单。 */
    public void changeStatus(Long guestId, String status) {
        if (!List.of("NORMAL", "BLACKLIST").contains(status)) {
            throw new BusinessException("客人状态取值不合法");
        }
        detail(guestId);
        guestDao.updateById(guestId, Map.of("status", status));
    }

    // ------------------------------------------------------------------
    // 预存押金
    // ------------------------------------------------------------------

    /** 客人预存押金（可用于入住时抵扣押金与结算）。 */
    @Transactional
    public Map<String, Object> deposit(Long guestId, int amountCents, String operator, String remark) {
        if (amountCents < 1000) {
            throw new BusinessException("单次预存金额不得低于 10 元");
        }
        if (amountCents > 5_000_000) {
            throw new BusinessException("单次预存金额不得超过 50000 元");
        }
        Guest guest = businessDao.lockGuest(guestId);
        if (guest == null) {
            throw new BusinessException("客人档案不存在");
        }
        if ("BLACKLIST".equals(guest.getStatus())) {
            throw new BusinessException("该客人已被列入黑名单，无法预存");
        }
        int balanceAfter = (guest.getBalanceCents() == null ? 0 : guest.getBalanceCents()) + amountCents;
        guestDao.updateById(guestId, Map.of("balance_cents", balanceAfter));

        GuestTransaction tx = new GuestTransaction();
        tx.setGuestId(guestId);
        tx.setTxNo(noGenerator.next("TX"));
        tx.setTxType("DEPOSIT");
        tx.setAmountCents(amountCents);
        tx.setBalanceAfter(balanceAfter);
        tx.setBizType("DEPOSIT");
        tx.setOperator(operator);
        tx.setRemark(remark == null || remark.isBlank() ? "客人预存押金" : remark);
        txDao.insertReturnKey(Mappers.transaction(tx));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txNo", tx.getTxNo());
        result.put("amountCents", amountCents);
        result.put("balanceAfter", balanceAfter);
        return result;
    }

    public Map<String, Object> pageTransactions(Long guestId, String txType, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", businessDao.listGuestTransactions(guestId, txType, p, s));
        result.put("total", businessDao.countGuestTransactions(guestId, txType));
        result.put("page", p);
        result.put("size", s);
        return result;
    }

    /** 客人完整档案（含入住历史与流水），供前台与 AI 使用。 */
    public Map<String, Object> profile(Long guestId) {
        Guest guest = detail(guestId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guest", guest);
        map.put("stayHistory", businessDao.guestStayHistory(guestId, 10));
        map.put("transactions", businessDao.listGuestTransactions(guestId, null, 1, 10));
        map.put("discountRate", discountRate(guest.getMemberLevel()));
        map.put("levelName", levelName(guest.getMemberLevel()));
        return map;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

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
            return "普通会员";
        }
        return switch (level) {
            case "GOLD" -> "黄金会员";
            case "SILVER" -> "白银会员";
            default -> "普通会员";
        };
    }

    /** 距离下一等级还差多少。 */
    public String nextLevelTip(Guest guest) {
        int stay = guest.getTotalStayCount() == null ? 0 : guest.getTotalStayCount();
        int consume = guest.getTotalConsumeCents() == null ? 0 : guest.getTotalConsumeCents();
        if ("GOLD".equals(guest.getMemberLevel())) {
            return "已是最高等级，继续入住可累积积分";
        }
        if ("SILVER".equals(guest.getMemberLevel())) {
            return "距离黄金会员还需入住 " + Math.max(0, 8 - stay) + " 次、消费满 3000 元";
        }
        return "距离白银会员还需入住 " + Math.max(0, 3 - stay) + " 次、消费满 1000 元";
    }

    /** 证件号脱敏：只保留前 6 位与后 4 位。 */
    private Guest mask(Guest guest) {
        if (guest == null) {
            return null;
        }
        if (guest.getIdCard() != null && guest.getIdCard().length() >= 10) {
            guest.setMaskedIdCard(MaskUtil.maskIdCard(guest.getIdCard()));
            guest.setIdCard(null);
        }
        return guest;
    }
}
