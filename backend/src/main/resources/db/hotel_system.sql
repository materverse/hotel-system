-- =============================================================
-- 城科酒店管理系统 数据库脚本
-- 项目：基于 Vue 的城科酒店管理系统设计与实现
-- 数据库：MySQL 8.0   字符集：utf8mb4   引擎：InnoDB
-- 说明：本脚本可直接执行，包含建库、建表、索引、初始数据。
--       用户口令字段写为 __INIT__ 占位，后端首次启动时自动生成盐与散列并回写，
--       避免在 .sql 中出现可反推的口令内容。
--       初始账号：admin / manager01 / housekeeper01 / guest01~guest04（口令 123456）
-- =============================================================

DROP DATABASE IF EXISTS hotel_system;
CREATE DATABASE hotel_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hotel_system;

-- -------------------------------------------------------------
-- 1. 用户表（身份认证：管理员 / 前台 / 客房服务 / 客人）
-- -------------------------------------------------------------
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(100) NOT NULL COMMENT '口令散列（加盐 SHA-256，1024 次迭代）',
  salt          VARCHAR(32)  NOT NULL COMMENT '口令盐值',
  real_name     VARCHAR(50)  NOT NULL COMMENT '真实姓名',
  role          VARCHAR(16)  NOT NULL COMMENT '角色：ADMIN/FRONTDESK/HOUSEKEEPING/GUEST',
  phone         VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
  last_login_at DATETIME              DEFAULT NULL COMMENT '最近登录时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role)
) ENGINE = InnoDB COMMENT '系统用户表';

-- -------------------------------------------------------------
-- 2. 房型表
-- -------------------------------------------------------------
CREATE TABLE room_type (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_name     VARCHAR(64)  NOT NULL COMMENT '房型名称，如 标准大床房',
  bed_type      VARCHAR(32)  NOT NULL DEFAULT '大床' COMMENT '床型：大床/双床/套房',
  area_sqm      INT          NOT NULL DEFAULT 25 COMMENT '面积（平方米）',
  capacity      INT          NOT NULL DEFAULT 2 COMMENT '可住人数',
  price_cents   INT          NOT NULL COMMENT '门市价（分/晚）',
  member_price_cents INT     NOT NULL DEFAULT 0 COMMENT '会员价（分/晚）',
  facilities    VARCHAR(255)          DEFAULT NULL COMMENT '配套设施，逗号分隔',
  description   VARCHAR(500)          DEFAULT NULL COMMENT '房型说明',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE 在售 / OFF_SALE 停售',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_type_status (status)
) ENGINE = InnoDB COMMENT '房型表';

-- -------------------------------------------------------------
-- 3. 客房表（任务书：客房信息设置、房态管理）
-- -------------------------------------------------------------
CREATE TABLE room (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  room_no       VARCHAR(16)  NOT NULL COMMENT '房间号，如 8801',
  floor_no      INT          NOT NULL DEFAULT 1 COMMENT '楼层',
  type_id       BIGINT       NOT NULL COMMENT '房型',
  orientation   VARCHAR(16)           DEFAULT NULL COMMENT '朝向：朝南/朝北/朝东/朝西',
  status        VARCHAR(16)  NOT NULL DEFAULT 'FREE' COMMENT '房态：FREE 空闲/OCCUPIED 已入住/CLEANING 待打扫/MAINTENANCE 维修/BOOKED 已预订',
  clean_status  VARCHAR(16)  NOT NULL DEFAULT 'CLEAN' COMMENT '清洁状态：CLEAN 已清洁/DIRTY 待清洁',
  remark        VARCHAR(255)          DEFAULT NULL COMMENT '备注（维修原因等）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_no (room_no),
  KEY idx_room_type (type_id),
  KEY idx_room_status (status)
) ENGINE = InnoDB COMMENT '客房表';

-- -------------------------------------------------------------
-- 4. 入住客人表（任务书：入住客人信息设置）
-- -------------------------------------------------------------
CREATE TABLE guest (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT                DEFAULT NULL COMMENT '关联登录账号（自助注册客人才有）',
  guest_no      VARCHAR(32)  NOT NULL COMMENT '客人编号',
  real_name     VARCHAR(50)  NOT NULL COMMENT '姓名',
  gender        VARCHAR(4)   NOT NULL DEFAULT '男' COMMENT '性别',
  phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
  id_card       VARCHAR(32)           DEFAULT NULL COMMENT '证件号（列表展示时脱敏）',
  id_type       VARCHAR(16)  NOT NULL DEFAULT '身份证' COMMENT '证件类型',
  birthday      DATE                  DEFAULT NULL COMMENT '生日',
  address       VARCHAR(255)          DEFAULT NULL COMMENT '联系地址',
  member_level  VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '等级：NORMAL/SILVER/GOLD',
  balance_cents INT          NOT NULL DEFAULT 0 COMMENT '预存押金余额（分）',
  points        INT          NOT NULL DEFAULT 0 COMMENT '积分',
  total_stay_count INT       NOT NULL DEFAULT 0 COMMENT '累计入住次数',
  total_consume_cents INT    NOT NULL DEFAULT 0 COMMENT '累计消费（分）',
  remark        VARCHAR(255)          DEFAULT NULL COMMENT '备注（过敏、偏好等）',
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/BLACKLIST',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_guest_no (guest_no),
  UNIQUE KEY uk_guest_phone (phone),
  KEY idx_guest_name (real_name)
) ENGINE = InnoDB COMMENT '入住客人表';

-- -------------------------------------------------------------
-- 5. 员工表（前台 / 客房服务）
-- -------------------------------------------------------------
CREATE TABLE staff (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL COMMENT '关联 sys_user.id',
  staff_no      VARCHAR(32)  NOT NULL COMMENT '工号',
  gender        VARCHAR(4)   NOT NULL DEFAULT '男' COMMENT '性别',
  position      VARCHAR(32)  NOT NULL COMMENT '岗位：前台接待/客房服务/值班经理',
  work_shift    VARCHAR(16)  NOT NULL DEFAULT '白班' COMMENT '班次：白班/夜班',
  intro         VARCHAR(255)          DEFAULT NULL COMMENT '备注',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ON_DUTY' COMMENT 'ON_DUTY 在岗/REST 休假/LEFT 离职',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_staff_no (staff_no),
  UNIQUE KEY uk_staff_user (user_id)
) ENGINE = InnoDB COMMENT '员工表';

-- -------------------------------------------------------------
-- 6. 预订表（任务书：订房、入住、退房流程）
-- -------------------------------------------------------------
CREATE TABLE reservation (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  reservation_no VARCHAR(32)  NOT NULL COMMENT '预订单号',
  guest_id       BIGINT       NOT NULL COMMENT '客人',
  room_id        BIGINT                DEFAULT NULL COMMENT '房间（可先不指定，入住时分配）',
  type_id        BIGINT       NOT NULL COMMENT '房型',
  check_in_date  DATE         NOT NULL COMMENT '入住日期',
  check_out_date DATE         NOT NULL COMMENT '离店日期',
  nights         INT          NOT NULL COMMENT '间夜数',
  room_count     INT          NOT NULL DEFAULT 1 COMMENT '房间数',
  guest_count    INT          NOT NULL DEFAULT 1 COMMENT '入住人数',
  unit_price_cents INT        NOT NULL COMMENT '成交单价（分/晚，已含会员折扣）',
  total_cents    INT          NOT NULL COMMENT '订单总额（分）',
  deposit_cents  INT          NOT NULL DEFAULT 0 COMMENT '已收押金（分）',
  channel        VARCHAR(16)  NOT NULL DEFAULT 'FRONT_DESK' COMMENT '渠道：FRONT_DESK 前台/APP 自助/PHONE 电话',
  status         VARCHAR(16)  NOT NULL DEFAULT 'BOOKED' COMMENT 'BOOKED 已预订/CHECKED_IN 已入住/CHECKED_OUT 已退房/CANCELLED 已取消/NO_SHOW 未到店',
  special_request VARCHAR(255)         DEFAULT NULL COMMENT '特殊要求（无烟房、高楼层等）',
  created_by     VARCHAR(50)           DEFAULT NULL COMMENT '创建人',
  check_in_at    DATETIME              DEFAULT NULL COMMENT '实际入住时间',
  check_out_at   DATETIME              DEFAULT NULL COMMENT '实际退房时间',
  cancel_reason  VARCHAR(255)          DEFAULT NULL COMMENT '取消原因',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_no (reservation_no),
  KEY idx_res_guest (guest_id),
  KEY idx_res_status (status),
  KEY idx_res_dates (check_in_date, check_out_date),
  KEY idx_res_room (room_id)
) ENGINE = InnoDB COMMENT '预订表';

-- -------------------------------------------------------------
-- 7. 消费项目表（餐饮、洗衣、迷你吧等，任务书：其它设置）
-- -------------------------------------------------------------
CREATE TABLE consume_item (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  item_name    VARCHAR(64)  NOT NULL COMMENT '项目名称',
  category     VARCHAR(32)  NOT NULL COMMENT '分类：餐饮/洗衣/迷你吧/叫醒服务/加床',
  price_cents  INT          NOT NULL COMMENT '单价（分）',
  unit         VARCHAR(16)  NOT NULL DEFAULT '份' COMMENT '单位',
  description  VARCHAR(255)          DEFAULT NULL COMMENT '说明',
  status       VARCHAR(16)  NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/OFF_SALE',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_item_category (category)
) ENGINE = InnoDB COMMENT '消费项目表';

-- -------------------------------------------------------------
-- 8. 账单表（每笔预订一份账单，退房时结算）
-- -------------------------------------------------------------
CREATE TABLE bill (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  bill_no        VARCHAR(32)  NOT NULL COMMENT '账单号',
  reservation_id BIGINT       NOT NULL COMMENT '预订',
  guest_id       BIGINT       NOT NULL COMMENT '客人',
  room_charge_cents   INT     NOT NULL DEFAULT 0 COMMENT '房费小计（分）',
  consume_charge_cents INT    NOT NULL DEFAULT 0 COMMENT '消费小计（分）',
  discount_cents INT          NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
  deposit_cents  INT          NOT NULL DEFAULT 0 COMMENT '押金抵扣（分）',
  total_cents    INT          NOT NULL DEFAULT 0 COMMENT '应收合计（分）',
  paid_cents     INT          NOT NULL DEFAULT 0 COMMENT '已收金额（分）',
  refund_cents   INT          NOT NULL DEFAULT 0 COMMENT '退还押金（分）',
  pay_type       VARCHAR(16)           DEFAULT NULL COMMENT '结算方式：CASH 现金/CARD 刷卡/ONLINE 线上/BALANCE 押金余额',
  status         VARCHAR(16)  NOT NULL DEFAULT 'UNSETTLED' COMMENT 'UNSETTLED 未结算/SETTLED 已结算/VOID 已作废',
  settled_by     VARCHAR(50)           DEFAULT NULL COMMENT '结算操作人',
  settled_at     DATETIME              DEFAULT NULL COMMENT '结算时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_no (bill_no),
  UNIQUE KEY uk_bill_reservation (reservation_id),
  KEY idx_bill_guest (guest_id),
  KEY idx_bill_status (status)
) ENGINE = InnoDB COMMENT '账单表';

-- -------------------------------------------------------------
-- 9. 账单明细表
-- -------------------------------------------------------------
CREATE TABLE bill_item (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  bill_id     BIGINT       NOT NULL COMMENT '账单',
  item_type   VARCHAR(16)  NOT NULL COMMENT 'ROOM 房费/CONSUME 消费/OTHER 其它',
  item_name   VARCHAR(64)  NOT NULL COMMENT '项目名称',
  unit_price_cents INT     NOT NULL COMMENT '单价（分）',
  quantity    INT          NOT NULL DEFAULT 1 COMMENT '数量',
  amount_cents INT         NOT NULL COMMENT '金额（分）',
  remark      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bill_item_bill (bill_id)
) ENGINE = InnoDB COMMENT '账单明细表';

-- -------------------------------------------------------------
-- 10. 客人预存/结算流水（可追溯）
-- -------------------------------------------------------------
CREATE TABLE guest_transaction (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  guest_id      BIGINT       NOT NULL COMMENT '客人',
  tx_no         VARCHAR(32)  NOT NULL COMMENT '流水号',
  tx_type       VARCHAR(16)  NOT NULL COMMENT 'DEPOSIT 预存/CONSUME 消费/REFUND 退款/ADJUST 调整',
  amount_cents  INT          NOT NULL COMMENT '发生金额（分，预存为正）',
  balance_after INT          NOT NULL COMMENT '发生后的预存余额（分）',
  biz_type      VARCHAR(24)           DEFAULT NULL COMMENT '业务类型：RESERVATION/SETTLE/OTHER',
  biz_id        BIGINT                DEFAULT NULL COMMENT '关联业务主键',
  operator      VARCHAR(50)           DEFAULT NULL COMMENT '操作人',
  remark        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gtx_no (tx_no),
  KEY idx_gtx_guest (guest_id, created_at)
) ENGINE = InnoDB COMMENT '客人预存流水表';

-- -------------------------------------------------------------
-- 11. 房价策略表（按房型与日期区间设置价格，任务书：房价设置）
-- -------------------------------------------------------------
CREATE TABLE rate_plan (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_id     BIGINT       NOT NULL COMMENT '房型',
  plan_name   VARCHAR(64)  NOT NULL COMMENT '策略名称，如 周末价、国庆涨价',
  start_date  DATE         NOT NULL COMMENT '生效开始日期',
  end_date    DATE         NOT NULL COMMENT '生效结束日期',
  price_cents INT          NOT NULL COMMENT '该区间单价（分/晚）',
  status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rate_type_date (type_id, start_date, end_date)
) ENGINE = InnoDB COMMENT '房价策略表';

-- -------------------------------------------------------------
-- 12. 知识库文档（RAG 语料）
-- -------------------------------------------------------------
CREATE TABLE knowledge_doc (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  title       VARCHAR(128) NOT NULL COMMENT '文档标题',
  category    VARCHAR(32)  NOT NULL DEFAULT '通用' COMMENT '分类',
  content     TEXT         NOT NULL COMMENT '正文（纯文本）',
  source      VARCHAR(128)          DEFAULT NULL COMMENT '资料来源',
  chunk_count INT          NOT NULL DEFAULT 0 COMMENT '切分片段数',
  status      VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT 'READY/DISABLED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_doc_category (category)
) ENGINE = InnoDB COMMENT '知识库文档表';

-- -------------------------------------------------------------
-- 13. 知识库片段（切分 + 本地向量）
-- -------------------------------------------------------------
CREATE TABLE knowledge_chunk (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  doc_id      BIGINT       NOT NULL COMMENT '所属文档',
  chunk_index INT          NOT NULL COMMENT '片段序号',
  content     VARCHAR(1000) NOT NULL COMMENT '片段正文',
  token_count INT          NOT NULL DEFAULT 0 COMMENT '片段字数',
  vector_json TEXT         COMMENT 'TF-IDF 稀疏向量（JSON）',
  vector_norm DOUBLE       NOT NULL DEFAULT 0 COMMENT '向量模长',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chunk_doc (doc_id)
) ENGINE = InnoDB COMMENT '知识库片段表';

-- -------------------------------------------------------------
-- 14. AI 会话与消息
-- -------------------------------------------------------------
CREATE TABLE ai_conversation (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  conv_no    VARCHAR(32)  NOT NULL COMMENT '会话号',
  user_id    BIGINT       NOT NULL COMMENT '所属用户',
  scene      VARCHAR(24)  NOT NULL DEFAULT 'QA' COMMENT '场景：QA 问答/PLAN 方案/REPORT 报表/AGENT 工具',
  title      VARCHAR(64)           DEFAULT NULL COMMENT '会话标题',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conv_no (conv_no),
  KEY idx_conv_user (user_id)
) ENGINE = InnoDB COMMENT 'AI 会话表';

CREATE TABLE ai_message (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT      NOT NULL COMMENT '会话',
  role            VARCHAR(12) NOT NULL COMMENT 'user/assistant/tool',
  content         TEXT        NOT NULL COMMENT '消息内容',
  citations       TEXT        COMMENT '引用来源（JSON）',
  tool_trace      TEXT        COMMENT '工具调用轨迹（JSON）',
  model           VARCHAR(48)          DEFAULT NULL COMMENT '模型名称',
  prompt_version  VARCHAR(24)          DEFAULT NULL COMMENT '提示词版本',
  latency_ms      INT         NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
  degraded        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否降级应答',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_msg_conv (conversation_id)
) ENGINE = InnoDB COMMENT 'AI 消息表';

-- -------------------------------------------------------------
-- 15. AI 调用日志（成本与可追溯）
-- -------------------------------------------------------------
CREATE TABLE ai_call_log (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id        BIGINT               DEFAULT NULL COMMENT '调用用户',
  scene          VARCHAR(24) NOT NULL COMMENT '业务场景',
  provider       VARCHAR(32) NOT NULL COMMENT '模型提供方',
  model          VARCHAR(48) NOT NULL COMMENT '模型名称',
  prompt_version VARCHAR(24)          DEFAULT NULL COMMENT '提示词版本',
  prompt_tokens  INT         NOT NULL DEFAULT 0 COMMENT '输入 token',
  output_tokens  INT         NOT NULL DEFAULT 0 COMMENT '输出 token',
  cost_cents     INT         NOT NULL DEFAULT 0 COMMENT '估算成本（分）',
  latency_ms     INT         NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
  retry_count    INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
  success        TINYINT     NOT NULL DEFAULT 1 COMMENT '是否成功',
  degraded       TINYINT     NOT NULL DEFAULT 0 COMMENT '是否降级',
  error_msg      VARCHAR(500)         DEFAULT NULL COMMENT '错误信息',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_scene (scene, created_at)
) ENGINE = InnoDB COMMENT 'AI 调用日志表';

-- -------------------------------------------------------------
-- 16. AI 生成报价单（结构化输出的落库结果，需人工确认）
-- -------------------------------------------------------------
CREATE TABLE ai_quote (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  guest_id      BIGINT                DEFAULT NULL COMMENT '关联客人（可空）',
  title         VARCHAR(64)  NOT NULL COMMENT '报价单名称',
  type_id       BIGINT                DEFAULT NULL COMMENT '房型',
  room_count    INT          NOT NULL DEFAULT 1 COMMENT '房间数',
  nights        INT          NOT NULL DEFAULT 1 COMMENT '间夜数',
  quote_json    TEXT         NOT NULL COMMENT '结构化报价（JSON Schema 校验通过）',
  risk_notice   VARCHAR(500)          DEFAULT NULL COMMENT '提示信息（价格时效等）',
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 待确认/CONFIRMED 已确认/REJECTED 已驳回',
  confirmed_by  VARCHAR(50)           DEFAULT NULL COMMENT '确认人',
  confirmed_at  DATETIME              DEFAULT NULL COMMENT '确认时间',
  model         VARCHAR(48)           DEFAULT NULL COMMENT '生成模型',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_quote_guest (guest_id)
) ENGINE = InnoDB COMMENT 'AI 报价单表';

-- -------------------------------------------------------------
-- 17. 操作日志
-- -------------------------------------------------------------
CREATE TABLE operation_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT                DEFAULT NULL COMMENT '操作人',
  username   VARCHAR(50)           DEFAULT NULL COMMENT '操作账号',
  module     VARCHAR(32)  NOT NULL COMMENT '模块',
  action     VARCHAR(32)  NOT NULL COMMENT '动作',
  detail     VARCHAR(500)          DEFAULT NULL COMMENT '详情',
  ip         VARCHAR(64)           DEFAULT NULL COMMENT '来源 IP',
  success    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oplog_user (user_id, created_at)
) ENGINE = InnoDB COMMENT '操作日志表';

-- =============================================================
-- 初始数据
-- =============================================================

INSERT INTO sys_user (id, username, password_hash, salt, real_name, role, phone, status) VALUES
(1, 'admin',          '__INIT__', '__INIT__', '系统管理员', 'ADMIN',        '13800000001', 1),
(2, 'manager01',      '__INIT__', '__INIT__', '周慧',       'ADMIN',        '13800000002', 1),
(3, 'frontdesk01',    '__INIT__', '__INIT__', '李娜',       'FRONTDESK',    '13800000003', 1),
(4, 'frontdesk02',    '__INIT__', '__INIT__', '陈鹏',       'FRONTDESK',    '13800000004', 1),
(5, 'housekeeper01',  '__INIT__', '__INIT__', '王秀英',     'HOUSEKEEPING', '13800000005', 1),
(6, 'guest01',        '__INIT__', '__INIT__', '张伟',       'GUEST',        '13900000001', 1),
(7, 'guest02',        '__INIT__', '__INIT__', '刘敏',       'GUEST',        '13900000002', 1),
(8, 'guest03',        '__INIT__', '__INIT__', '赵磊',       'GUEST',        '13900000003', 1),
(9, 'guest04',        '__INIT__', '__INIT__', '孙丽',       'GUEST',        '13900000004', 1);

INSERT INTO staff (id, user_id, staff_no, gender, position, work_shift, status) VALUES
(1, 3, 'S001', '女', '前台接待', '白班', 'ON_DUTY'),
(2, 4, 'S002', '男', '前台接待', '夜班', 'ON_DUTY'),
(3, 5, 'S003', '女', '客房服务', '白班', 'ON_DUTY');

INSERT INTO room_type (id, type_name, bed_type, area_sqm, capacity, price_cents, member_price_cents, facilities, description, status) VALUES
(1, '标准大床房', '大床', 25, 2, 29800, 26800, '无线网络,空调,24小时热水,液晶电视', '适合单人或情侣入住，房间朝南采光好', 'ON_SALE'),
(2, '标准双床房', '双床', 28, 2, 32800, 29800, '无线网络,空调,24小时热水,液晶电视', '两张1.2米床，适合同事或朋友出差同住', 'ON_SALE'),
(3, '高级商务房', '大床', 35, 2, 45800, 41800, '无线网络,空调,浴缸,写字台,迷你吧', '配备写字台与人体工学椅，适合商务出差', 'ON_SALE'),
(4, '亲子家庭房', '大床+单床', 42, 4, 52800, 48800, '无线网络,空调,儿童拖鞋,浴缸,冰箱', '一大一小两张床，可加婴儿床', 'ON_SALE'),
(5, '行政套房',   '大床', 60, 2, 88800, 82800, '无线网络,空调,会客区,浴缸,免费迷你吧,行政酒廊', '独立会客区与卧室，含双早与行政酒廊权益', 'ON_SALE');

INSERT INTO room (id, room_no, floor_no, type_id, orientation, status, clean_status, remark) VALUES
(1,  '8801', 8, 1, '朝南', 'FREE',       'CLEAN', NULL),
(2,  '8802', 8, 1, '朝北', 'FREE',       'CLEAN', NULL),
(3,  '8803', 8, 2, '朝南', 'FREE',       'CLEAN', NULL),
(4,  '8805', 8, 2, '朝东', 'MAINTENANCE','DIRTY', '空调异响待维修'),
(5,  '8806', 8, 3, '朝南', 'FREE',       'CLEAN', NULL),
(6,  '8807', 8, 3, '朝西', 'FREE',       'CLEAN', NULL),
(7,  '8808', 8, 4, '朝南', 'FREE',       'CLEAN', NULL),
(8,  '9801', 9, 3, '朝南', 'FREE',       'CLEAN', NULL),
(9,  '9802', 9, 4, '朝东', 'FREE',       'CLEAN', NULL),
(10, '9803', 9, 5, '朝南', 'FREE',       'CLEAN', NULL),
(11, '9805', 9, 5, '朝南', 'FREE',       'CLEAN', NULL),
(12, '9806', 9, 1, '朝北', 'FREE',       'CLEAN', NULL);

INSERT INTO guest (id, user_id, guest_no, real_name, gender, phone, id_card, birthday, address, member_level, balance_cents, points, total_stay_count, total_consume_cents, remark, status) VALUES
(1, 6, 'G2025001', '张伟', '男', '13900000001', '500102199005120011', '1990-05-12', '重庆市渝中区', 'GOLD',   120000, 860, 12, 486000, '偏好高楼层无烟房', 'NORMAL'),
(2, 7, 'G2025002', '刘敏', '女', '13900000002', '500102199308230022', '1993-08-23', '重庆市沙坪坝区', 'SILVER',  50000, 320,  6, 218000, '对羽毛过敏，需无羽绒枕', 'NORMAL'),
(3, 8, 'G2025003', '赵磊', '男', '13900000003', '500102198711050033', '1987-11-05', '四川省成都市',   'NORMAL',  20000, 120,  2,  96000, NULL, 'NORMAL'),
(4, 9, 'G2025004', '孙丽', '女', '13900000004', '500102199902170044', '1999-02-17', '重庆市江北区',  'NORMAL',      0,  40,  1,  52800, '需要婴儿床', 'NORMAL');

INSERT INTO reservation (id, reservation_no, guest_id, room_id, type_id, check_in_date, check_out_date, nights, room_count, guest_count, unit_price_cents, total_cents, deposit_cents, channel, status, special_request, created_by, check_in_at, check_out_at) VALUES
(1, 'RS202610010001', 1, 1, 1, '2026-10-01', '2026-10-03', 2, 1, 1, 26800, 53600, 50000, 'FRONT_DESK', 'CHECKED_OUT', '高楼层无烟房', '李娜', '2026-10-01 14:20:00', '2026-10-03 11:05:00'),
(2, 'RS202610050001', 2, 5, 3, '2026-10-05', '2026-10-08', 3, 1, 2, 41800, 125400, 50000, 'APP',        'CHECKED_OUT', '无羽绒枕',     '陈鹏', '2026-10-05 15:10:00', '2026-10-08 10:30:00'),
(3, 'RS202610080001', 3, 3, 2, '2026-10-08', '2026-10-10', 2, 1, 2, 32800, 65600, 30000, 'PHONE',      'CHECKED_OUT', NULL,           '李娜', '2026-10-08 16:00:00', '2026-10-10 09:50:00'),
(4, 'RS202610120001', 4, 7, 4, '2026-10-12', '2026-10-14', 2, 1, 3, 48800, 97600, 50000, 'APP',        'BOOKED',      '需要婴儿床',   '陈鹏', NULL, NULL),
(5, 'RS202610140001', 1, 10, 5, '2026-10-14', '2026-10-16', 2, 1, 2, 82800, 165600, 100000, 'FRONT_DESK','BOOKED',      '行政酒廊早餐', '李娜', NULL, NULL);

INSERT INTO consume_item (id, item_name, category, price_cents, unit, description, status) VALUES
(1, '中式早餐',   '餐饮',   3800, '份', '粥、包子、小菜、豆浆', 'ON_SALE'),
(2, '西式早餐',   '餐饮',   4800, '份', '面包、煎蛋、培根、咖啡', 'ON_SALE'),
(3, '客房送餐',   '餐饮',   6800, '份', '正餐套餐，含主食与汤', 'ON_SALE'),
(4, '衬衫洗涤',   '洗衣',   1500, '件', '当日18点前送洗，次日上午取', 'ON_SALE'),
(5, '西装干洗',   '洗衣',   3500, '件', '干洗加熨烫', 'ON_SALE'),
(6, '迷你吧饮料', '迷你吧', 1200, '瓶', '可乐、雪碧、矿泉水', 'ON_SALE'),
(7, '叫醒服务',   '叫醒服务', 0,  '次', '免费提供', 'ON_SALE'),
(8, '加床',       '加床',   10000, '张', '含床品与洗漱用品', 'ON_SALE'),
(9, '延迟退房',   '其它',   5000, '小时', '14 点后按小时计费', 'ON_SALE');

INSERT INTO rate_plan (id, type_id, plan_name, start_date, end_date, price_cents, status) VALUES
(1, 1, '周末价',   '2026-10-01', '2026-12-31', 32800, 'ACTIVE'),
(2, 3, '周末价',   '2026-10-01', '2026-12-31', 49800, 'ACTIVE'),
(3, 5, '国庆涨价', '2026-10-01', '2026-10-07', 98800, 'ACTIVE'),
(4, 2, '淡季优惠', '2026-11-15', '2026-12-20', 29800, 'DISABLED');

INSERT INTO knowledge_doc (title, category, content, source) VALUES
('订房与入住流程', '预订服务',
 '预订渠道包括前台预订、电话预订与自助预订三种。电话预订需留下姓名与手机号，前台在系统中生成预订单号（RS 加日期加流水号）。\n入住时间为当日 14:00 之后，离店时间为次日 12:00 之前。提前入住需视房态安排，如房间已清洁完毕可免费提前入住，否则需按每小时 50 元收取延迟入住费用。\n办理入住需出示本人有效身份证件，前台核对证件与预订信息无误后录入系统，并收取押金。标准房与高级房押金 300 元，家庭房 500 元，套房 1000 元。\n预订保留至当日 18:00，超过 18:00 未到店且未提前联系的，系统将标记为未到店并释放房间。\n客人在入住期间可以换房，需先确认目标房间为空闲状态，由前台办理换房并同步更新账单房费。',
 '城科酒店前台操作手册'),
('退房与账单结算规则', '结账服务',
 '退房时间为次日 12:00 之前。12:00 至 14:00 退房加收半天房费，14:00 之后退房加收全天房费。\n退房时前台需检查房间消费（迷你吧、送餐、洗衣等）是否录入齐全，核对无误后生成账单并结算。\n押金在结算时优先抵扣房费与消费，剩余部分退还客人；押金不足时客人需补交差额。\n支持现金、刷卡与线上支付三种结算方式。已开具发票的账单如需退改，须由值班经理审批。\n客人退房后房间状态自动变为待打扫，由客房服务在 40 分钟内完成清洁并反馈房态。',
 '城科酒店前台操作手册'),
('房型与房价说明', '房价政策',
 '酒店共有五种房型：标准大床房、标准双床房、高级商务房、亲子家庭房与行政套房。\n标准大床房门市价 298 元每晚，标准双床房 328 元，高级商务房 458 元，亲子家庭房 528 元，行政套房 888 元。\n会员分为普通会员、白银会员与黄金会员。白银会员房价享受 95 折，黄金会员享受 9 折；会员价不可与其它优惠同时使用。\n周末（周五、周六）与法定节假日执行周末价或节假日价，价格以系统内的房价策略为准。\n连续入住 7 晚以上可申请长住优惠，需由值班经理审批后以协议价录入系统。\n房价不含早餐，早餐可在入住时加购或到餐厅单点。',
 '城科酒店房价政策'),
('客房服务与设施说明', '客房服务',
 '酒店提供每日一次客房清洁服务，时间通常为上午 10:00 至下午 15:00。如需请勿打扰，可将门牌挂出或在系统内备注。\n毛巾与床品一客一换，长住客人每两天更换一次，可应客人要求每日更换。\n房间内迷你吧饮料为收费项目，退房时一并结算；行政套房迷你吧首轮免费。\n洗衣服务当日 18:00 前送洗，次日上午 10:00 前送回；西装干洗需 24 小时。\n叫醒服务免费，可提前一天在前台或房间电话预约。\n房间设施损坏请及时告知前台，非人为损坏免费维修；人为损坏按物品价目表赔偿。',
 '城科酒店客房服务规范'),
('安全须知与应急预案', '安全管理',
 '入住期间请勿在房间内使用大功率电器，禁止在房间内吸烟，违者按酒店规定收取清洁费 200 元。\n请妥善保管贵重物品，酒店前台提供免费保险箱寄存服务，房间内保险箱请自行设置密码。\n如遇火警，请保持冷静，用湿毛巾捂住口鼻，沿安全通道有序撤离，切勿乘坐电梯。酒店每层楼均设有消防栓与疏散指示图。\n如遇停水停电，酒店将启动应急照明与备用水源，前台会逐房通知并做好解释工作。\n客人突发疾病时，前台应立即联系 120 并通知值班经理，同时提供最近的医院信息。',
 '城科酒店安全管理规定'),
('会员权益与积分规则', '会员服务',
 '客人累计入住满 3 次且消费满 1000 元可升级为白银会员，满 8 次且消费满 3000 元可升级为黄金会员。\n积分规则：每消费 1 元累计 1 分，积分可在下次入住时抵扣房费，100 分抵扣 10 元，单次抵扣不超过房费的 20%。\n黄金会员享有免费延迟退房至 14:00、免费早餐两份、优先预订与免费升级房型（视房态）等权益。\n会员生日当周入住可获赠生日蛋糕一份，需提前一天告知前台。\n会员预订需使用本人账号或提供会员手机号，积分与消费记录不可转让。',
 '城科酒店会员手册'),
('投诉与纠纷处理办法', '投诉处理',
 '客人对房间卫生、噪音或服务不满意时，前台应第一时间致歉并核实情况，能当场解决的立即解决。\n房间卫生问题优先安排重新清洁或换房；噪音问题先协调隔壁客人，无法协调时安排换房。\n客人要求免除房费或赔偿时，前台不得直接承诺，须上报值班经理审批，并在系统中记录处理过程。\n所有投诉需在当班内录入系统并跟踪闭环，24 小时内回访客人确认满意度。\n因酒店原因造成客人损失的，按实际损失协商赔偿，并保留书面记录。',
 '城科酒店投诉处理流程'),
('AI 客服使用说明', '系统说明',
 'AI 前台助手可以回答客人关于房型房价、订房入住、退房结算、客房服务、会员权益与投诉渠道的问题，回答内容来自酒店知识库并标注引用来源。\n当知识库中没有相关内容时，AI 会明确说明没有找到依据，并建议联系前台（分机 0）或值班经理，不会编造价格与政策。\nAI 生成的报价单属于建议性质，必须经前台或值班经理确认后才会写入系统；报价有效期以生成当日的房价策略为准。\nAI 不会询问也不会保存银行卡号、支付密码等敏感信息。请勿在对话中输入此类内容。',
 '城科酒店智能系统说明');

-- 说明：knowledge_chunk 由后端启动或调用 /api/ai/kb/reindex 时自动切分并生成本地向量，
--      此处不写死，避免向量与实现不一致。
