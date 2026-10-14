# -*- coding: utf-8 -*-
"""生成报告所需的矢量风格插图（PNG）：用例图、E-R 图、功能模块图、业务流程图、系统架构图。

用法：python scripts/build_diagrams.py
输出：docs/diagrams/*.png
说明：用 Pillow 手工绘制，避免对 Graphviz 的依赖；中文字体使用系统 msyh.ttc。
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "diagrams")
os.makedirs(OUT, exist_ok=True)

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

WHITE = (255, 255, 255)
INK = (34, 45, 58)
BLUE = (28, 93, 153)
BLUE_L = (219, 234, 247)
GREEN = (56, 142, 96)
GREEN_L = (223, 242, 231)
ORANGE = (198, 120, 42)
ORANGE_L = (252, 238, 218)
PURPLE = (108, 92, 158)
PURPLE_L = (233, 229, 245)
GREY = (120, 132, 146)
GREY_L = (240, 243, 247)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def text(d, xy, s, size=15, color=INK, bold=False, anchor="mm"):
    d.text(xy, s, font=font(size, bold), fill=color, anchor=anchor)


def ellipse(d, box, fill, outline, width=2):
    d.ellipse(box, fill=fill, outline=outline, width=width)


def rrect(d, box, fill, outline, width=2, radius=14):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def line(d, a, b, color=GREY, width=2, dash=None):
    if not dash:
        d.line([a, b], fill=color, width=width)
        return
    # 手绘虚线
    import math
    x1, y1 = a
    x2, y2 = b
    total = math.hypot(x2 - x1, y2 - y1)
    if total == 0:
        return
    step = dash[0] + dash[1]
    n = int(total // step) + 1
    for i in range(n):
        s = i * step / total
        e = min((i * step + dash[0]) / total, 1.0)
        d.line([(x1 + (x2 - x1) * s, y1 + (y2 - y1) * s),
                (x1 + (x2 - x1) * e, y1 + (y2 - y1) * e)], fill=color, width=width)


def arrow(d, a, b, color=GREY, width=2, size=9):
    import math
    d.line([a, b], fill=color, width=width)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    for s in (2.7, -2.7):
        d.line([b, (b[0] + size * math.cos(ang + s), b[1] + size * math.sin(ang + s))],
               fill=color, width=width)


# ----------------------------------------------------------------------
# 图 1：用例图
# ----------------------------------------------------------------------

def use_case_diagram():
    W, H = 1820, 1240
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)

    # 边界
    bx0, by0, bx1, by1 = 400, 60, 1420, 1180
    rrect(d, (bx0, by0, bx1, by1), (248, 251, 254), BLUE, 3, 18)
    text(d, ((bx0 + bx1) / 2, by0 + 32), "城科酒店管理系统", 23, BLUE, True)

    # 四组用例：名称 + 归组
    groups = [
        ("公共访问", BLUE, BLUE_L, [
            "浏览房型与房价", "按日期试算房价", "客人自助注册", "登录 / 退出",
        ]),
        ("客人自助", GREEN, GREEN_L, [
            "查看我的预订", "查看我的账单", "预存押金", "AI 问答与报告",
        ]),
        ("前台业务", ORANGE, ORANGE_L, [
            "登记 / 检索客人", "创建预订", "办理入住收押金", "登记客房消费",
            "办理退房", "账单结算",
        ]),
        ("管理与统计", PURPLE, PURPLE_L, [
            "客房与房价维护", "房态与打扫反馈", "财务与出租率统计",
            "知识库维护", "AI 评测", "员工与权限管理",
        ]),
    ]

    x_center = (bx0 + bx1) / 2
    span = 760
    ucs = []           # (name, cx, cy, w, h)
    y = by0 + 100
    for gname, gcolor, gfill, items in groups:
        text(d, (x_center, y), gname, 17, gcolor, True)
        y += 36
        per_row = 3
        for i, name in enumerate(items):
            row = i // per_row
            col = i % per_row
            n_in_row = min(per_row, len(items) - row * per_row)
            step = span / max(n_in_row, 1)
            # 单行时居中
            offset = (span - step * n_in_row) / 2
            cx = x_center - span / 2 + offset + step * (col + 0.5)
            cy = y + row * 70
            w, h = 224, 54
            ellipse(d, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), gfill, gcolor, 2)
            text(d, (cx, cy), name, 14.5, INK)
            ucs.append((name, cx, cy, w, h))
        rows = (len(items) + per_row - 1) // per_row
        y += rows * 70 + 30

    # 参与者：左侧 3 个，右侧 2 个
    actors = [
        ("客人", 185, 330, GREEN),
        ("前台人员", 185, 700, ORANGE),
        ("客房服务", 185, 1010, ORANGE),
        ("未登录访客", 1635, 330, BLUE),
        ("管理员", 1635, 780, PURPLE),
    ]
    actor_pos = {}
    for name, ax, ay, acolor in actors:
        d.ellipse((ax - 24, ay - 82, ax + 24, ay - 34), outline=acolor, width=3, fill=WHITE)
        d.line([(ax, ay - 34), (ax, ay + 18)], fill=acolor, width=3)
        d.line([(ax - 36, ay - 14), (ax + 36, ay - 14)], fill=acolor, width=3)
        d.line([(ax, ay + 18), (ax - 28, ay + 64)], fill=acolor, width=3)
        d.line([(ax, ay + 18), (ax + 28, ay + 64)], fill=acolor, width=3)
        text(d, (ax, ay + 90), name, 17, acolor, True)
        actor_pos[name] = (ax, ay)

    # 关系：每个参与者只连语义相关的用例，且就近接入椭圆边缘
    rel = {
        "客人": ["浏览房型与房价", "按日期试算房价", "客人自助注册", "登录 / 退出",
                 "查看我的预订", "查看我的账单", "预存押金", "AI 问答与报告"],
        "前台人员": ["登记 / 检索客人", "创建预订", "办理入住收押金", "登记客房消费",
                     "办理退房", "账单结算", "按日期试算房价", "房态与打扫反馈"],
        "客房服务": ["房态与打扫反馈"],
        "未登录访客": ["浏览房型与房价", "按日期试算房价", "客人自助注册", "登录 / 退出"],
        "管理员": ["客房与房价维护", "房态与打扫反馈", "财务与出租率统计",
                   "知识库维护", "AI 评测", "员工与权限管理"],
    }
    uc_pos = {n: (cx, cy, w, h) for n, cx, cy, w, h in ucs}
    for actor, names in rel.items():
        ax, ay = actor_pos[actor]
        for n in names:
            if n not in uc_pos:
                continue
            cx, cy, w, h = uc_pos[n]
            # 椭圆边缘：按方向求交点
            import math
            dx, dy = cx - ax, cy - ay
            dist = math.hypot(dx, dy) or 1
            ex = cx - (w / 2) * (dx / dist)
            ey = cy - (h / 2) * (dy / dist)
            start = (ax + (36 if ax < W / 2 else -36), ay)
            d.line([start, (ex, ey)], fill=(186, 198, 212), width=1)

    img.save(os.path.join(OUT, "图4-5-系统用例图.png"))
    print("生成 图4-5-系统用例图.png")


# ----------------------------------------------------------------------
# 图 2：E-R 图
# ----------------------------------------------------------------------

def er_diagram():
    W, H = 1900, 1420
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (W / 2, 40), "城科酒店管理系统 E-R 图", 25, BLUE, True)

    entities = [
        ("sys_user 用户", 230, 160, 250, BLUE, ["username", "password_hash", "role"]),
        ("staff 员工", 230, 420, 250, BLUE, ["staff_no", "position", "status"]),
        ("guest 客人", 230, 760, 250, GREEN,
         ["guest_no", "id_card", "member_level", "balance_cents", "points"]),
        ("ai_conversation AI会话", 230, 1090, 250, GREY, ["conv_no", "scene", "guest_id"]),
        ("ai_message AI消息", 230, 1330, 250, GREY, ["role", "content", "citations"]),

        ("room_type 房型", 640, 150, 250, PURPLE,
         ["type_name", "bed_type", "capacity", "price_cents"]),
        ("room 客房", 640, 430, 250, PURPLE,
         ["room_no", "floor_no", "status", "clean_status"]),
        ("rate_plan 房价策略", 640, 700, 250, PURPLE,
         ["type_id (FK)", "plan_name", "start_date", "end_date", "price_cents"]),
        ("ai_call_log AI调用日志", 640, 980, 250, GREY,
         ["scene", "model", "cost_cents", "degraded"]),

        ("reservation 预订", 1060, 210, 270, ORANGE,
         ["reservation_no", "guest_id (FK)", "room_id (FK)", "check_in_date",
          "check_out_date", "status", "total_cents"]),
        ("bill 账单", 1060, 640, 270, ORANGE,
         ["bill_no", "reservation_id (FK)", "room_charge_cents", "consume_charge_cents",
          "deposit_cents", "total_cents", "status"]),
        ("knowledge_doc 知识库文档", 1060, 1010, 270, PURPLE, ["title", "category", "status"]),
        ("ai_quote AI报价单", 1060, 1290, 270, GREY,
         ["guest_id (FK)", "type_id (FK)", "quote_json", "status", "confirmed_by"]),
        ("guest_transaction 预存流水", 1060, 1490, 270, GREEN,
         ["tx_no", "tx_type", "amount_cents", "balance_after"]),

        ("bill_item 账单明细", 1490, 640, 260, ORANGE,
         ["bill_id (FK)", "item_type", "item_name", "amount_cents"]),
        ("consume_item 消费项目", 1490, 330, 260, ORANGE,
         ["item_name", "category", "price_cents", "status"]),
        ("knowledge_chunk 知识库片段", 1490, 1010, 260, PURPLE,
         ["doc_id (FK)", "chunk_index", "content", "vector_json"]),
        ("operation_log 操作日志", 1490, 1290, 260, GREY,
         ["module", "action", "operator", "ip", "success"]),
    ]

    boxes = {}

    def draw_entity(name, cx, cy, w, color, attrs):
        header_h = 36
        attr_h = len(attrs) * 23 + 12
        h = header_h + attr_h
        x0, y0 = cx - w / 2, cy - h / 2
        rrect(d, (x0, y0, x0 + w, y0 + h), WHITE, color, 2, 10)
        d.rounded_rectangle((x0, y0, x0 + w, y0 + header_h), radius=10, fill=color)
        d.rectangle((x0, y0 + header_h - 12, x0 + w, y0 + header_h), fill=color)
        text(d, (cx, y0 + header_h / 2), name, 15.5, WHITE, True)
        ty = y0 + header_h + 9
        for a in attrs:
            text(d, (x0 + 12, ty + 11), a, 13, INK, anchor="lm")
            ty += 23
        boxes[name] = (x0, y0, x0 + w, y0 + h, cx, cy)
        return boxes[name]

    for name, cx, cy, w, color, attrs in entities:
        draw_entity(name, cx, cy, w, color, attrs)

    def label_box(x, y, s, color=BLUE):
        f = font(13, True)
        tw = d.textlength(s, font=f)
        d.rectangle((x - tw / 2 - 5, y - 11, x + tw / 2 + 5, y + 11), fill=WHITE)
        d.text((x, y), s, font=f, fill=color, anchor="mm")

    def link(a, b, label, lane=0):
        ax0, ay0, ax1, ay1, acx, acy = boxes[a]
        bx0, by0, bx1, by1, bcx, bcy = boxes[b]
        # 同一列（竖直关系）：直接连
        if abs(acx - bcx) < 12:
            p1 = (acx, ay1 if bcy > acy else ay0)
            p2 = (bcx, by0 if bcy > acy else by1)
            d.line([p1, p2], fill=(150, 165, 182), width=2)
            d.line([(p1[0] - 9, p1[1] + (4 if bcy > acy else -4)),
                    (p1[0] + 9, p1[1] + (4 if bcy > acy else -4))], fill=GREEN, width=2)
            label_box(p1[0] + 64, (p1[1] + p2[1]) / 2, label)
            return
        # 不同列：先横 → 再竖（走独立通道，避免穿过中间的实体）→ 再横
        if bcx > acx:
            p1 = (ax1, acy)
            p2 = (bx0, bcy)
        else:
            p1 = (ax0, acy)
            p2 = (bx1, bcy)
        midx = (p1[0] + p2[0]) / 2 + lane
        d.line([p1, (midx, p1[1])], fill=(150, 165, 182), width=2)
        d.line([(midx, p1[1]), (midx, p2[1])], fill=(150, 165, 182), width=2)
        d.line([(midx, p2[1]), p2], fill=(150, 165, 182), width=2)
        d.line([(p1[0], p1[1] - 9), (p1[0], p1[1] + 9)], fill=GREEN, width=2)
        label_box(midx + 4, p1[1] - 20, label)

    link("sys_user 用户", "staff 员工", "1 : 1")
    link("staff 员工", "guest 客人", "0..1 : 1")
    link("ai_conversation AI会话", "ai_message AI消息", "1 : N")
    link("room_type 房型", "room 客房", "1 : N")
    link("room 客房", "rate_plan 房价策略", "1 : N")
    link("room 客房", "reservation 预订", "1 : N", lane=-55)
    link("guest 客人", "reservation 预订", "1 : N", lane=253)
    link("room_type 房型", "rate_plan 房价策略", "1 : N", lane=-96)
    link("reservation 预订", "bill 账单", "1 : 1")
    link("bill 账单", "bill_item 账单明细", "1 : N")
    link("consume_item 消费项目", "bill_item 账单明细", "1 : N")
    link("knowledge_doc 知识库文档", "knowledge_chunk 知识库片段", "1 : N")
    link("guest 客人", "ai_quote AI报价单", "1 : N", lane=580)

    text(d, (W / 2, H - 30),
         "说明：各表主键均为 id（BIGINT 自增）；金额字段以“分”为单位存储；"
         "room_no / reservation_no / bill_no / guest_no / tx_no 建立唯一索引；"
         "知识库文档与片段、AI 会话与消息、账单与明细均为一对多。",
         14, GREY)
    img.save(os.path.join(OUT, "图4-4-系统E-R图.png"))
    print("生成 图4-4-系统E-R图.png")


# ----------------------------------------------------------------------
# 图 3：功能模块图
# ----------------------------------------------------------------------

def module_diagram():
    W, H = 1700, 1000
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (W / 2, 36), "城科酒店管理系统 功能模块图", 24, BLUE, True)

    # 顶层
    rrect(d, (W / 2 - 220, 76, W / 2 + 220, 134), BLUE, BLUE, 2, 14)
    text(d, (W / 2, 105), "城科酒店管理系统", 19, WHITE, True)

    modules = [
        ("身份与权限", BLUE, BLUE_L, ["登录 / 退出 / 令牌", "客人自助注册",
                                      "员工账号维护", "口令重置与启停"]),
        ("客房与房价", PURPLE, PURPLE_L, ["房型管理", "客房与房态管理",
                                          "房价策略（周末/节假日）", "维修与打扫反馈"]),
        ("客人档案", GREEN, GREEN_L, ["客人登记与检索", "会员等级与折扣",
                                      "预存押金与流水", "黑名单管理"]),
        ("预订与入住", ORANGE, ORANGE_L, ["房价试算", "可售房查询与分配",
                                          "创建 / 取消预订", "入住 / 退房 / 未到店"]),
        ("账单与消费", ORANGE, ORANGE_L, ["自动建账单与房费明细", "登记客房消费",
                                          "优惠减免（需原因）", "结算与押金多退少补"]),
        ("统计报表", PURPLE, PURPLE_L, ["前台看板", "营收与房型业绩",
                                        "出租率与房态统计", "操作与 AI 调用日志"]),
        ("AI 智能服务", BLUE, BLUE_L, ["知识库问答（RAG）", "客人入住报告",
                                       "结构化报价单 + 人工确认", "业务工具调用智能体"]),
    ]
    col_w = 226
    gap = 16
    total = len(modules) * col_w + (len(modules) - 1) * gap
    x = (W - total) / 2
    for name, color, fill, funcs in modules:
        # 主干线
        d.line([(W / 2, 134), (W / 2, 172)], fill=GREY, width=2)
        d.line([(x + col_w / 2, 172), (x + (len(modules) - 1) * (col_w + gap) + col_w / 2, 172)],
               fill=GREY, width=2)
        d.line([(x + col_w / 2, 172), (x + col_w / 2, 200)], fill=GREY, width=2)

        rrect(d, (x, 200, x + col_w, 254), color, color, 2, 12)
        text(d, (x + col_w / 2, 227), name, 16, WHITE, True)
        y = 268
        for f in funcs:
            rrect(d, (x, y, x + col_w, y + 56), fill, color, 1, 10)
            # 自动换行
            if len(f) > 11:
                mid = len(f) // 2
                text(d, (x + col_w / 2, y + 20), f[:mid], 13, INK)
                text(d, (x + col_w / 2, y + 38), f[mid:], 13, INK)
            else:
                text(d, (x + col_w / 2, y + 28), f, 13.5, INK)
            y += 66
        x += col_w + gap

    img.save(os.path.join(OUT, "图4-2-系统功能模块图.png"))
    print("生成 图4-2-系统功能模块图.png")


# ----------------------------------------------------------------------
# 图 4：核心业务流程图
# ----------------------------------------------------------------------

def flow_diagram():
    W, H = 1240, 1560
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (W / 2, 36), "核心业务流程图（订房—入住—消费—退房—结算）", 23, BLUE, True)

    cx = W / 2
    y = 90

    def box(label, color, fill, h=62, w=430, sub=None):
        nonlocal y
        rrect(d, (cx - w / 2, y, cx + w / 2, y + h), fill, color, 2, 12)
        if sub:
            text(d, (cx, y + h / 2 - 11), label, 16, INK, True)
            text(d, (cx, y + h / 2 + 12), sub, 13, GREY)
        else:
            text(d, (cx, y + h / 2), label, 16, INK, True)
        box_y = y
        y += h
        return box_y

    def diamond(label, color=ORANGE, fill=ORANGE_L, h=86, w=380):
        nonlocal y
        pts = [(cx, y), (cx + w / 2, y + h / 2), (cx, y + h), (cx - w / 2, y + h / 2)]
        d.polygon(pts, fill=fill, outline=color, width=2)
        if len(label) > 13:
            mid = len(label) // 2
            text(d, (cx, y + h / 2 - 10), label[:mid], 14, INK)
            text(d, (cx, y + h / 2 + 10), label[mid:], 14, INK)
        else:
            text(d, (cx, y + h / 2), label, 14.5, INK)
        y += h

    def gap(px=42, label=None, color=GREY):
        nonlocal y
        if label:
            d.line([(cx, y), (cx, y + px)], fill=color, width=2)
            arrow(d, (cx, y + px - 12), (cx, y + px), color, 2)
            text(d, (cx + 46, y + px / 2), label, 13, GREY, anchor="lm")
        else:
            d.line([(cx, y), (cx, y + px)], fill=GREY, width=2)
            arrow(d, (cx, y + px - 12), (cx, y + px), GREY, 2)
        y += px

    def side(texts, color, dy):
        # 右侧异常分支
        bx = cx + 300
        rrect(d, (bx, dy - 26, bx + 300, dy + 26 + 22 * (len(texts) - 1)), GREY_L, color, 1, 8)
        for i, t in enumerate(texts):
            text(d, (bx + 12, dy - 12 + i * 22), t, 12.5, INK, anchor="lm")
        d.line([(cx + 380 / 2, dy), (bx, dy)], fill=(200, 120, 120), width=2)
        text(d, (cx + 200, dy - 14), "否", 13, (192, 80, 80))

    box("前台登记或检索客人", BLUE, BLUE_L, sub="姓名 / 手机号 / 客人编号 / 身份证")
    gap()
    box("选择房型与入住离店日期", BLUE, BLUE_L)
    gap()
    box("房价试算", PURPLE, PURPLE_L, sub="房价策略优先 → 会员价 → 门市价")
    gap()
    diamond("日期与房量校验通过？")
    side(["· 离店须晚于入住且不早于今天", "· 可售房数不足", "· 指定房间已被预订", "· 入住人数超过房型上限"], ORANGE, y - 43)
    gap(label="是")
    box("创建预订（事务）", ORANGE, ORANGE_L, h=82,
        sub="① 生成预订单号  ② 写 reservation(BOOKED)  ③ 占用房态  ④ 预建账单与房费明细")
    gap()
    diamond("客人到店办理入住？")
    side(["· 超时未到 → 标记未到店，释放房态", "· 客人取消 → 订单置 CANCELLED"], ORANGE, y - 43)
    gap(label="是")
    box("办理入住（事务 + 房间行锁）", ORANGE, ORANGE_L, h=82,
        sub="① 分配房间  ② 收押金（预存扣款 + 写流水）  ③ 订单 CHECKED_IN  ④ 房态 OCCUPIED")
    gap()
    box("在住期间登记客房消费", GREEN, GREEN_L, sub="餐饮 / 洗衣 / 迷你吧 / 加床，可多次登记，累加消费小计")
    gap()
    box("办理退房", ORANGE, ORANGE_L, h=82,
        sub="① 按实际晚数重算房费 + 延迟退房费  ② 订单 CHECKED_OUT  ③ 房态 CLEANING/DIRTY")
    gap()
    box("客房服务反馈打扫完成", PURPLE, PURPLE_L, sub="房态回到 FREE，清洁状态置 CLEAN")
    gap()
    box("账单结算（事务 + 客人行锁）", BLUE, BLUE_L, h=82,
        sub="应收 = 房费 + 消费 − 优惠 − 押金抵扣；正数补收，负数退还预存余额")
    gap()
    rrect(d, (cx - 160, y, cx + 160, y + 54), GREEN, GREEN, 2, 12)
    text(d, (cx, y + 27), "流程结束，账目可追溯", 16, WHITE, True)
    y += 54

    img.save(os.path.join(OUT, "图4-3-核心业务流程图.png"))
    print("生成 图4-3-核心业务流程图.png")


# ----------------------------------------------------------------------
# 图 5：系统架构图
# ----------------------------------------------------------------------

def arch_diagram():
    W, H = 1320, 1080
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (W / 2, 36), "系统总体架构图", 24, BLUE, True)

    layers = [
        ("表现层（Vue 3 + Vite + Element Plus + Vue Router + Pinia）", BLUE, BLUE_L,
         ["前台：首页 / 房型房价 / AI 前台助手 / 登录 / 客人注册",
          "客人端：客人中心 / 我的预订 / 我的账单 / 预存与消费流水",
          "管理端：前台看板 / 预订 / 客房 / 客人 / 账单结算 / 消费项目",
          "房价策略 / 数据概览 / 房态统计 / 财务报表 / 知识库 / 评测 / 权限"]),
        ("接口层（Spring MVC RestController + 全局异常 + 鉴权拦截器）", ORANGE, ORANGE_L,
         ["/api/auth  /api/public  /api/me  /api/front  /api/room",
          "/api/reservation  /api/guest  /api/bill  /api/staff",
          "/api/stats  /api/kb  /api/users  /api/ai  /api/health"]),
        ("业务层（Service）", PURPLE, PURPLE_L,
         ["AuthService  GuestService  RoomService  StatsService",
          "ReservationService：试算 / 预订 / 入住 / 退房 / 取消 / 未到店",
          "BillService：消费登记 / 优惠 / 结算     KnowledgeBaseService：RAG",
          "AiAssistantService：AI 场景编排与人工确认"]),
        ("AI 能力层", GREEN, GREEN_L,
         ["LlmClient：超时 / 重试 / 限流 / 降级 / 成本日志   PromptTemplates（v1.0）",
          "JsonSchemas + QuoteValidator：结构化输出校验   TextChunker + TextVectorizer：RAG",
          "HotelTools：8 个业务只读工具   AiGuard：注入检测 / 脱敏 / 输出审查",
          "AiEvalService：26 条测试集     AiCallLogger：调用可追溯"]),
        ("数据访问层（JdbcTemplate + CrudDao + BusinessDao + KnowledgeDao + AiDao）", GREY, GREY_L,
         ["通用单表 CRUD、驼峰下划线映射、行锁查询、聚合统计 SQL"]),
        ("数据层（MySQL 8.0 · InnoDB · utf8mb4 · 18 张表）", BLUE, (232, 240, 250),
         ["身份权限 / 客房房价 / 客人档案 / 预订入住 / 账单消费 / 统计日志 / AI 与知识库"]),
    ]

    y = 78
    for title, color, fill, lines in layers:
        h = 52 + len(lines) * 26
        rrect(d, (60, y, W - 60, y + h), fill, color, 2, 14)
        d.rounded_rectangle((60, y, W - 60, y + 40), radius=14, fill=color)
        d.rectangle((60, y + 28, W - 60, y + 40), fill=color)
        text(d, (W / 2, y + 22), title, 16, WHITE, True)
        ty = y + 50
        for ln in lines:
            text(d, (W / 2, ty + 10), ln, 13.5, INK)
            ty += 26
        y += h
        if y < H - 120:
            arrow(d, (W / 2, y + 6), (W / 2, y + 30), GREY, 2)
            y += 36

    text(d, (W / 2, H - 26), "说明：前端通过 HTTP + JSON 调用后端接口；密钥与数据库口令全部通过环境变量注入，不入库不入仓。",
         13, GREY)
    img.save(os.path.join(OUT, "图4-1-系统总体架构图.png"))
    print("生成 图4-1-系统总体架构图.png")


if __name__ == "__main__":
    use_case_diagram()
    er_diagram()
    module_diagram()
    flow_diagram()
    arch_diagram()
    print("\n全部图片输出目录：", OUT)
