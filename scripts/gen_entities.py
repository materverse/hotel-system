# -*- coding: utf-8 -*-
"""生成酒店系统实体类（字段与 sql/hotel_system.sql 的表结构一一对应）。

用法：python scripts/gen_entities.py
输出：backend/src/main/java/com/cqucc/hotel/entity/*.java
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "backend", "src", "main", "java", "com", "cqucc", "hotel", "entity")

# 类型代号：I=Integer, L=Long, S=String, B=BigDecimal, D=LocalDate, T=LocalDateTime
TYPES = {
    "I": "Integer",
    "L": "Long",
    "S": "String",
    "B": "BigDecimal",
    "D": "LocalDate",
    "T": "LocalDateTime",
    "LIST": "List<BillItem>",
}

ENTITIES = {
    "SysUser": [
        ("L", "id", "主键"),
        ("S", "username", "登录账号"),
        ("S", "passwordHash", "口令散列"),
        ("S", "salt", "口令盐值"),
        ("S", "realName", "真实姓名"),
        ("S", "role", "角色 ADMIN/FRONTDESK/HOUSEKEEPING/GUEST"),
        ("S", "phone", "手机号"),
        ("I", "status", "1 启用 0 禁用"),
        ("T", "lastLoginAt", "最近登录时间"),
        ("T", "createdAt", "创建时间"),
    ],
    "RoomType": [
        ("L", "id", "主键"),
        ("S", "typeName", "房型名称"),
        ("S", "bedType", "床型"),
        ("I", "areaSqm", "面积（平方米）"),
        ("I", "capacity", "可住人数"),
        ("I", "priceCents", "门市价（分/晚）"),
        ("I", "memberPriceCents", "会员价（分/晚）"),
        ("S", "facilities", "配套设施"),
        ("S", "description", "房型说明"),
        ("S", "status", "ON_SALE/OFF_SALE"),
        ("T", "createdAt", "创建时间"),
    ],
    "Room": [
        ("L", "id", "主键"),
        ("S", "roomNo", "房间号"),
        ("I", "floorNo", "楼层"),
        ("L", "typeId", "房型"),
        ("S", "orientation", "朝向"),
        ("S", "status", "房态 FREE/OCCUPIED/CLEANING/MAINTENANCE/BOOKED"),
        ("S", "cleanStatus", "清洁状态 CLEAN/DIRTY"),
        ("S", "remark", "备注"),
        ("T", "createdAt", "创建时间"),
        ("S", "typeName", "关联查询：房型名称"),
        ("I", "priceCents", "关联查询：门市价"),
        ("I", "memberPriceCents", "关联查询：会员价"),
        ("S", "guestName", "关联查询：当前入住客人"),
        ("L", "currentReservationId", "关联查询：当前订单"),
        ("D", "currentCheckOutDate", "关联查询：预计离店日期"),
    ],
    "Guest": [
        ("L", "id", "主键"),
        ("L", "userId", "关联登录账号"),
        ("S", "guestNo", "客人编号"),
        ("S", "realName", "姓名"),
        ("S", "gender", "性别"),
        ("S", "phone", "手机号"),
        ("S", "idCard", "证件号"),
        ("S", "idType", "证件类型"),
        ("D", "birthday", "生日"),
        ("S", "address", "联系地址"),
        ("S", "memberLevel", "等级 NORMAL/SILVER/GOLD"),
        ("I", "balanceCents", "预存押金余额（分）"),
        ("I", "points", "积分"),
        ("I", "totalStayCount", "累计入住次数"),
        ("I", "totalConsumeCents", "累计消费（分）"),
        ("S", "remark", "备注"),
        ("S", "status", "NORMAL/BLACKLIST"),
        ("T", "createdAt", "创建时间"),
        ("S", "maskedIdCard", "展示用：脱敏证件号"),
    ],
    "Staff": [
        ("L", "id", "主键"),
        ("L", "userId", "关联 sys_user.id"),
        ("S", "staffNo", "工号"),
        ("S", "gender", "性别"),
        ("S", "position", "岗位"),
        ("S", "workShift", "班次"),
        ("S", "intro", "备注"),
        ("S", "status", "ON_DUTY/REST/LEFT"),
        ("T", "createdAt", "创建时间"),
        ("S", "realName", "关联查询：姓名"),
        ("S", "phone", "关联查询：手机号"),
    ],
    "Reservation": [
        ("L", "id", "主键"),
        ("S", "reservationNo", "预订单号"),
        ("L", "guestId", "客人"),
        ("L", "roomId", "房间"),
        ("L", "typeId", "房型"),
        ("D", "checkInDate", "入住日期"),
        ("D", "checkOutDate", "离店日期"),
        ("I", "nights", "间夜数"),
        ("I", "roomCount", "房间数"),
        ("I", "guestCount", "入住人数"),
        ("I", "unitPriceCents", "成交单价（分/晚）"),
        ("I", "totalCents", "订单总额（分）"),
        ("I", "depositCents", "已收押金（分）"),
        ("S", "channel", "渠道"),
        ("S", "status", "BOOKED/CHECKED_IN/CHECKED_OUT/CANCELLED/NO_SHOW"),
        ("S", "specialRequest", "特殊要求"),
        ("S", "createdBy", "创建人"),
        ("T", "checkInAt", "实际入住时间"),
        ("T", "checkOutAt", "实际退房时间"),
        ("S", "cancelReason", "取消原因"),
        ("T", "createdAt", "创建时间"),
        ("S", "guestName", "关联查询：客人姓名"),
        ("S", "guestPhone", "关联查询：客人手机号"),
        ("S", "roomNo", "关联查询：房间号"),
        ("S", "typeName", "关联查询：房型名称"),
        ("I", "billTotalCents", "关联查询：账单应付（分）"),
    ],
    "ConsumeItem": [
        ("L", "id", "主键"),
        ("S", "itemName", "项目名称"),
        ("S", "category", "分类"),
        ("I", "priceCents", "单价（分）"),
        ("S", "unit", "单位"),
        ("S", "description", "说明"),
        ("S", "status", "ON_SALE/OFF_SALE"),
        ("T", "createdAt", "创建时间"),
    ],
    "Bill": [
        ("L", "id", "主键"),
        ("S", "billNo", "账单号"),
        ("L", "reservationId", "预订"),
        ("L", "guestId", "客人"),
        ("I", "roomChargeCents", "房费小计（分）"),
        ("I", "consumeChargeCents", "消费小计（分）"),
        ("I", "discountCents", "优惠金额（分）"),
        ("I", "depositCents", "押金抵扣（分）"),
        ("I", "totalCents", "应收合计（分）"),
        ("I", "paidCents", "已收金额（分）"),
        ("I", "refundCents", "退还押金（分）"),
        ("S", "payType", "结算方式"),
        ("S", "status", "UNSETTLED/SETTLED/VOID"),
        ("S", "settledBy", "结算操作人"),
        ("T", "settledAt", "结算时间"),
        ("T", "createdAt", "创建时间"),
        ("S", "guestName", "关联查询：客人姓名"),
        ("S", "reservationNo", "关联查询：预订单号"),
        ("S", "roomNo", "关联查询：房间号"),
        ("LIST", "items", "关联：账单明细（非表字段，由服务层填充）"),
    ],
    "BillItem": [
        ("L", "id", "主键"),
        ("L", "billId", "账单"),
        ("S", "itemType", "ROOM/CONSUME/OTHER"),
        ("S", "itemName", "项目名称"),
        ("I", "unitPriceCents", "单价（分）"),
        ("I", "quantity", "数量"),
        ("I", "amountCents", "金额（分）"),
        ("S", "remark", "备注"),
        ("T", "createdAt", "创建时间"),
    ],
    "GuestTransaction": [
        ("L", "id", "主键"),
        ("L", "guestId", "客人"),
        ("S", "txNo", "流水号"),
        ("S", "txType", "DEPOSIT/CONSUME/REFUND/ADJUST"),
        ("I", "amountCents", "发生金额（分）"),
        ("I", "balanceAfter", "发生后的预存余额（分）"),
        ("S", "bizType", "业务类型"),
        ("L", "bizId", "关联业务主键"),
        ("S", "operator", "操作人"),
        ("S", "remark", "备注"),
        ("T", "createdAt", "创建时间"),
        ("S", "guestName", "关联查询：客人姓名"),
    ],
    "RatePlan": [
        ("L", "id", "主键"),
        ("L", "typeId", "房型"),
        ("S", "planName", "策略名称"),
        ("D", "startDate", "生效开始日期"),
        ("D", "endDate", "生效结束日期"),
        ("I", "priceCents", "该区间单价（分/晚）"),
        ("S", "status", "ACTIVE/DISABLED"),
        ("T", "createdAt", "创建时间"),
        ("S", "typeName", "关联查询：房型名称"),
    ],
    "KnowledgeDoc": [
        ("L", "id", "主键"),
        ("S", "title", "文档标题"),
        ("S", "category", "分类"),
        ("S", "content", "正文"),
        ("S", "source", "资料来源"),
        ("I", "chunkCount", "切分片段数"),
        ("S", "status", "READY/DISABLED"),
        ("T", "createdAt", "创建时间"),
    ],
    "KnowledgeChunk": [
        ("L", "id", "主键"),
        ("L", "docId", "所属文档"),
        ("I", "chunkIndex", "片段序号"),
        ("S", "content", "片段正文"),
        ("I", "tokenCount", "片段字数"),
        ("S", "vectorJson", "TF-IDF 向量（JSON）"),
        ("B", "vectorNorm", "向量模长"),
        ("S", "docTitle", "关联查询：文档标题"),
        ("S", "docSource", "关联查询：资料来源"),
    ],
    "AiConversation": [
        ("L", "id", "主键"),
        ("S", "convNo", "会话号"),
        ("L", "userId", "所属用户"),
        ("S", "scene", "场景"),
        ("S", "title", "会话标题"),
        ("T", "createdAt", "创建时间"),
        ("T", "updatedAt", "更新时间"),
    ],
    "AiMessage": [
        ("L", "id", "主键"),
        ("L", "conversationId", "会话"),
        ("S", "role", "角色"),
        ("S", "content", "消息内容"),
        ("S", "citations", "引用来源（JSON）"),
        ("S", "toolTrace", "工具调用轨迹（JSON）"),
        ("S", "model", "模型名称"),
        ("S", "promptVersion", "提示词版本"),
        ("I", "latencyMs", "耗时(ms)"),
        ("I", "degraded", "是否降级"),
        ("T", "createdAt", "创建时间"),
    ],
    "AiCallLog": [
        ("L", "id", "主键"),
        ("L", "userId", "调用用户"),
        ("S", "scene", "业务场景"),
        ("S", "provider", "模型提供方"),
        ("S", "model", "模型名称"),
        ("S", "promptVersion", "提示词版本"),
        ("I", "promptTokens", "输入 token"),
        ("I", "outputTokens", "输出 token"),
        ("I", "costCents", "估算成本（分）"),
        ("I", "latencyMs", "耗时(ms)"),
        ("I", "retryCount", "重试次数"),
        ("I", "success", "是否成功"),
        ("I", "degraded", "是否降级"),
        ("S", "errorMsg", "错误信息"),
        ("T", "createdAt", "创建时间"),
    ],
    "AiQuote": [
        ("L", "id", "主键"),
        ("L", "guestId", "关联客人"),
        ("S", "title", "报价单名称"),
        ("L", "typeId", "房型"),
        ("I", "roomCount", "房间数"),
        ("I", "nights", "间夜数"),
        ("S", "quoteJson", "结构化报价（JSON）"),
        ("S", "riskNotice", "提示信息"),
        ("S", "status", "DRAFT/CONFIRMED/REJECTED"),
        ("S", "confirmedBy", "确认人"),
        ("T", "confirmedAt", "确认时间"),
        ("S", "model", "生成模型"),
        ("T", "createdAt", "创建时间"),
    ],
    "OperationLog": [
        ("L", "id", "主键"),
        ("L", "userId", "操作人"),
        ("S", "username", "操作账号"),
        ("S", "module", "模块"),
        ("S", "action", "动作"),
        ("S", "detail", "详情"),
        ("S", "ip", "来源 IP"),
        ("I", "success", "是否成功"),
        ("T", "createdAt", "创建时间"),
    ],
}


def cap(name):
    return name[0].upper() + name[1:]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for cls, fields in ENTITIES.items():
        lines = []
        lines.append("package com.cqucc.hotel.entity;")
        lines.append("")
        needs = {TYPES[t] for t, _, _ in fields}
        if "BigDecimal" in needs:
            lines.append("import java.math.BigDecimal;")
        if "LocalDate" in needs:
            lines.append("import java.time.LocalDate;")
        if "LocalDateTime" in needs:
            lines.append("import java.time.LocalDateTime;")
        if "List<BillItem>" in needs:
            lines.append("import java.util.List;")
        if needs & {"BigDecimal", "LocalDate", "LocalDateTime", "List<BillItem>"}:
            lines.append("")
        lines.append("/**")
        lines.append(" * %s 实体，对应数据库表 %s。" % (cls, cls_to_table(cls)))
        lines.append(" */")
        lines.append("public class %s {" % cls)
        lines.append("")
        for t, name, desc in fields:
            lines.append("    /** %s */" % desc)
            lines.append("    private %s %s;" % (TYPES[t], name))
        lines.append("")
        for t, name, desc in fields:
            java_type = TYPES[t]
            lines.append("    public %s get%s() {" % (java_type, cap(name)))
            lines.append("        return %s;" % name)
            lines.append("    }")
            lines.append("")
            lines.append("    public void set%s(%s %s) {" % (cap(name), java_type, name))
            lines.append("        this.%s = %s;" % (name, name))
            lines.append("    }")
            lines.append("")
        lines.append("}")
        path = os.path.join(OUT_DIR, cls + ".java")
        with io.open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        print("生成", cls + ".java", "字段", len(fields))


def cls_to_table(cls):
    out = []
    for i, c in enumerate(cls):
        if c.isupper() and i > 0:
            out.append("_")
        out.append(c.lower())
    return "".join(out)


if __name__ == "__main__":
    main()
