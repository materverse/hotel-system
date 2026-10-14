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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单与结算服务。
 * <p>负责：入住期间的消费登记（餐饮/洗衣/迷你吧等）、优惠减免、退房结算、押金退还与预存流水。</p>
 * <p>结算公式：应收 = 房费 + 消费 − 优惠 − 押金抵扣；押金抵扣后如有剩余则退还客人，
 * 不足则要求补收差额，整个过程记录在账单与流水中，保证可追溯。</p>
 */
@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final BusinessDao businessDao;
    private final CrudDao<Bill> billDao;
    private final CrudDao<BillItem> billItemDao;
    private final CrudDao<ConsumeItem> consumeItemDao;
    private final CrudDao<Guest> guestDao;
    private final CrudDao<GuestTransaction> txDao;
    private final CrudDao<Reservation> reservationDao;
    private final BizNoGenerator noGenerator;
    private final AuthService authService;

    public BillService(BusinessDao businessDao, CrudDao<Bill> billCrudDao, CrudDao<BillItem> billItemDao,
                       CrudDao<ConsumeItem> consumeItemDao, CrudDao<Guest> guestCrudDao,
                       CrudDao<GuestTransaction> guestTransactionDao, CrudDao<Reservation> reservationCrudDao,
                       BizNoGenerator noGenerator, AuthService authService) {
        this.businessDao = businessDao;
        this.billDao = billCrudDao;
        this.billItemDao = billItemDao;
        this.consumeItemDao = consumeItemDao;
        this.guestDao = guestCrudDao;
        this.txDao = guestTransactionDao;
        this.reservationDao = reservationCrudDao;
        this.noGenerator = noGenerator;
        this.authService = authService;
    }

    /** 账单详情（含明细）。 */
    public Bill detail(Long billId) {
        Bill bill = businessDao.findBillById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        bill.setItems(businessDao.listBillItems(bill.getId()));
        return bill;
    }

    public Bill detailByReservation(Long reservationId) {
        Bill bill = businessDao.findBillByReservation(reservationId);
        if (bill == null) {
            throw new BusinessException("该预订还没有生成账单");
        }
        bill.setItems(businessDao.listBillItems(bill.getId()));
        return bill;
    }

    public Map<String, Object> page(String status, Long guestId, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        List<Bill> bills = businessDao.listBills(status, guestId, p, s);
        for (Bill bill : bills) {
            bill.setItems(businessDao.listBillItems(bill.getId()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", bills);
        result.put("total", businessDao.countBills(status, guestId));
        result.put("page", p);
        result.put("size", s);
        return result;
    }

    // ------------------------------------------------------------------
    // 消费登记
    // ------------------------------------------------------------------

    /**
     * 为在住订单登记消费（可多次追加）。
     *
     * @param consumeItemId 消费项目 ID
     * @param quantity      数量
     */
    @Transactional
    public Map<String, Object> addConsume(Long reservationId, Long consumeItemId, int quantity,
                                          String remark, String operator) {
        Reservation reservation = reservationDao.findById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预订单不存在");
        }
        if (!"CHECKED_IN".equals(reservation.getStatus())) {
            throw new BusinessException("只有已入住的订单可以登记消费，当前状态："
                    + ReservationService.statusText(reservation.getStatus()));
        }
        ConsumeItem item = consumeItemDao.findById(consumeItemId);
        if (item == null) {
            throw new BusinessException("消费项目不存在");
        }
        if (!"ON_SALE".equals(item.getStatus())) {
            throw new BusinessException("消费项目《" + item.getItemName() + "》已停用");
        }
        if (quantity <= 0) {
            throw new BusinessException("数量必须大于 0");
        }
        Bill bill = businessDao.findBillByReservation(reservationId);
        if (bill == null) {
            throw new BusinessException("账单数据缺失，请联系管理员");
        }
        int amount = item.getPriceCents() * quantity;
        BillItem billItem = new BillItem();
        billItem.setBillId(bill.getId());
        billItem.setItemType("CONSUME");
        billItem.setItemName(item.getItemName());
        billItem.setUnitPriceCents(item.getPriceCents());
        billItem.setQuantity(quantity);
        billItem.setAmountCents(amount);
        billItem.setRemark(remark);
        billItemDao.insertReturnKey(Mappers.billItem(billItem));

        int consumeTotal = (bill.getConsumeChargeCents() == null ? 0 : bill.getConsumeChargeCents()) + amount;
        int total = Math.max(0, (bill.getRoomChargeCents() == null ? 0 : bill.getRoomChargeCents())
                + consumeTotal
                - (bill.getDiscountCents() == null ? 0 : bill.getDiscountCents())
                - (bill.getDepositCents() == null ? 0 : bill.getDepositCents()));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("consume_charge_cents", consumeTotal);
        values.put("total_cents", total);
        billDao.updateById(bill.getId(), values);

        authService.writeLog(null, operator, "前台业务", "登记消费",
                "预订 " + reservation.getReservationNo() + " 登记 " + item.getItemName() + " × " + quantity, null, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", bill.getId());
        result.put("billNo", bill.getBillNo());
        result.put("itemName", item.getItemName());
        result.put("quantity", quantity);
        result.put("amountCents", amount);
        result.put("consumeChargeCents", consumeTotal);
        result.put("payableCents", total);
        return result;
    }

    /** 减免优惠（需填写原因，便于审计）。 */
    @Transactional
    public Map<String, Object> discount(Long billId, int amountCents, String reason, String operator) {
        if (amountCents <= 0) {
            throw new BusinessException("优惠金额必须大于 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("必须填写优惠原因，便于审计追溯");
        }
        Bill bill = billDao.findById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        if ("SETTLED".equals(bill.getStatus())) {
            throw new BusinessException("账单已结算，无法再修改优惠金额");
        }
        int discount = (bill.getDiscountCents() == null ? 0 : bill.getDiscountCents()) + amountCents;
        if (discount > bill.getRoomChargeCents() + bill.getConsumeChargeCents()) {
            throw new BusinessException("优惠金额不能超过房费与消费之和");
        }
        int total = Math.max(0, bill.getRoomChargeCents() + (bill.getConsumeChargeCents() == null ? 0 : bill.getConsumeChargeCents())
                - discount - (bill.getDepositCents() == null ? 0 : bill.getDepositCents()));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("discount_cents", discount);
        values.put("total_cents", total);
        billDao.updateById(billId, values);

        BillItem billItem = new BillItem();
        billItem.setBillId(billId);
        billItem.setItemType("OTHER");
        billItem.setItemName("优惠减免");
        billItem.setUnitPriceCents(-amountCents);
        billItem.setQuantity(1);
        billItem.setAmountCents(-amountCents);
        billItem.setRemark(reason);
        billItemDao.insertReturnKey(Mappers.billItem(billItem));

        authService.writeLog(null, operator, "前台业务", "账单优惠",
                "账单 " + bill.getBillNo() + " 优惠 " + amountCents / 100.0 + " 元，原因：" + reason, null, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billNo", bill.getBillNo());
        result.put("discountCents", discount);
        result.put("payableCents", total);
        return result;
    }

    // ------------------------------------------------------------------
    // 结算
    // ------------------------------------------------------------------

    /**
     * 结算账单：校验状态、计算押金退还或补收、更新账单与客人累计消费、写流水。
     *
     * @param refundToBalance 押金剩余是否退回到客人预存余额（true 退回余额，false 记为现金退还）
     */
    @Transactional
    public Map<String, Object> settle(Long billId, String payType, boolean refundToBalance, String operator) {
        Bill bill = billDao.findById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        if ("SETTLED".equals(bill.getStatus())) {
            throw new BusinessException("账单已结算，无需重复操作");
        }
        if ("VOID".equals(bill.getStatus())) {
            throw new BusinessException("账单已作废，无法结算");
        }
        Reservation reservation = reservationDao.findById(bill.getReservationId());
        if (reservation != null && "CHECKED_IN".equals(reservation.getStatus())) {
            throw new BusinessException("该订单尚未办理退房，请先退房再结算");
        }
        int roomCharge = bill.getRoomChargeCents() == null ? 0 : bill.getRoomChargeCents();
        int consume = bill.getConsumeChargeCents() == null ? 0 : bill.getConsumeChargeCents();
        int discount = bill.getDiscountCents() == null ? 0 : bill.getDiscountCents();
        int deposit = bill.getDepositCents() == null ? 0 : bill.getDepositCents();

        // 押金抵扣后：正数为客人还需补交，负数为应退客人
        int net = roomCharge + consume - discount - deposit;
        int paid = Math.max(0, net);
        int refund = net < 0 ? -net : 0;

        Guest guest = businessDao.lockGuest(bill.getGuestId());
        if (guest == null) {
            throw new BusinessException("客人档案不存在");
        }
        int balance = guest.getBalanceCents() == null ? 0 : guest.getBalanceCents();
        int balanceAfter = balance;
        if (refund > 0 && refundToBalance) {
            balanceAfter = balance + refund;
            writeTx(guest.getId(), "REFUND", refund, balanceAfter, "SETTLE", billId, operator,
                    "账单 " + bill.getBillNo() + " 押金退还 " + refund / 100.0 + " 元");
        } else if (refund > 0) {
            writeTx(guest.getId(), "REFUND", 0, balance, "SETTLE", billId, operator,
                    "账单 " + bill.getBillNo() + " 押金 " + refund / 100.0 + " 元以现金退还客人");
        }
        if (paid > 0) {
            // 补收走现金/刷卡/线上渠道，不动用客人预存余额，
            // 因此流水金额记 0、余额保持不变，仅留一条备注说明，避免对账时误读为"扣款未生效"
            writeTx(guest.getId(), "ADJUST", 0, balanceAfter, "SETTLE", billId, operator,
                    "账单 " + bill.getBillNo() + " 结算补收 " + paid / 100.0 + " 元（"
                            + payTypeText(payType) + "，未动用预存余额）");
        }

        Map<String, Object> guestValues = new LinkedHashMap<>();
        guestValues.put("balance_cents", balanceAfter);
        guestValues.put("total_consume_cents", (guest.getTotalConsumeCents() == null ? 0 : guest.getTotalConsumeCents())
                + roomCharge + consume - discount);
        guestValues.put("points", (guest.getPoints() == null ? 0 : guest.getPoints()) + (roomCharge + consume - discount) / 100);
        guestValues.put("member_level", levelOf(guest.getTotalStayCount() == null ? 0 : guest.getTotalStayCount(),
                (guest.getTotalConsumeCents() == null ? 0 : guest.getTotalConsumeCents()) + roomCharge + consume - discount));
        guestDao.updateById(guest.getId(), guestValues);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("paid_cents", paid);
        values.put("refund_cents", refund);
        values.put("pay_type", payType == null || payType.isBlank() ? "CASH" : payType);
        values.put("status", "SETTLED");
        values.put("settled_by", operator);
        values.put("settled_at", LocalDateTime.now());
        values.put("total_cents", paid);
        billDao.updateById(billId, values);

        authService.writeLog(null, operator, "前台业务", "退房结算",
                "账单 " + bill.getBillNo() + " 应收 " + paid / 100.0 + " 元，退还 " + refund / 100.0 + " 元", null, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billNo", bill.getBillNo());
        result.put("roomChargeCents", roomCharge);
        result.put("consumeChargeCents", consume);
        result.put("discountCents", discount);
        result.put("depositCents", deposit);
        result.put("paidCents", paid);
        result.put("refundCents", refund);
        result.put("refundToBalance", refundToBalance && refund > 0);
        result.put("guestBalanceAfter", balanceAfter);
        result.put("payType", payType == null ? "CASH" : payType);
        result.put("status", "SETTLED");
        log.info("账单 {} 结算完成：应收 {} 分，退还 {} 分", bill.getBillNo(), paid, refund);
        return result;
    }

    /** 客人等级按累计入住次数与消费金额自动升级。 */
    public String levelOf(int stayCount, int totalConsumeCents) {
        if (stayCount >= 8 && totalConsumeCents >= 300000) {
            return "GOLD";
        }
        if (stayCount >= 3 && totalConsumeCents >= 100000) {
            return "SILVER";
        }
        return "NORMAL";
    }

    public String payTypeText(String payType) {
        if (payType == null) {
            return "现金";
        }
        return switch (payType) {
            case "CARD" -> "刷卡";
            case "ONLINE" -> "线上支付";
            case "BALANCE" -> "预存余额";
            default -> "现金";
        };
    }

    public List<ConsumeItem> listConsumeItems(String category, String status) {
        return businessDao.listConsumeItems(category, status);
    }

    @Transactional
    public Long saveConsumeItem(ConsumeItem item) {
        if (item.getItemName() == null || item.getItemName().isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }
        if (item.getPriceCents() == null || item.getPriceCents() < 0) {
            throw new BusinessException("单价不能为负数");
        }
        if (item.getId() == null) {
            item.setStatus(item.getStatus() == null ? "ON_SALE" : item.getStatus());
            item.setUnit(item.getUnit() == null ? "份" : item.getUnit());
            return consumeItemDao.insertReturnKey(Mappers.consumeItem(item));
        }
        consumeItemDao.updateById(item.getId(), Mappers.consumeItem(item));
        return item.getId();
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
