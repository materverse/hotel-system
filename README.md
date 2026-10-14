# 基于 Vue 的城科酒店管理系统

> 《应用软件开发专题实践》课程设计 —— 单人单题
> 姓名：卢家全　学号：2023114028　班级：23 计算机 6 班
> 学院：重庆城市科技学院 人工智能与大数据学院

一个前后端分离的酒店管理系统，覆盖「客人登记 → 房型房价试算 → 订房 → 办理入住 → 登记客房消费 →
办理退房 → 账单结算」完整业务闭环，并在业务流程里嵌入 4 项 AI 能力
（大模型接入、结构化输出、知识库问答 RAG、工具调用智能体），
附 26 条 AI 专项测试集、54 项验收断言与 10 条单元测试。

---

## 一、技术栈

| 层次 | 选型 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3.5 + Vite 6 + Vue Router 4 + Pinia 2 + Element Plus 2.9 | 组件化 + 路由 + 状态管理（登录态） |
| 后端 | Java 17 + Spring Boot 3.4.6 + Spring MVC + JdbcTemplate | RESTful 接口，统一响应体与全局异常处理 |
| 数据库 | MySQL 8.0（utf8mb4 / InnoDB） | 18 张表，含索引、唯一约束与种子数据 |
| AI | 兼容 OpenAI 协议的 Chat Completions 接口（默认 DeepSeek） | 模型 / 密钥 / 地址全部走配置与环境变量 |
| 检索 | 自研本地 TF-IDF 稀疏向量检索（中文单字 + 双字特征） | 无外部向量模型依赖，断网也能演示 RAG |
| 构建 | Maven（后端）、npm + Vite（前端） | 前端产物可合并进后端 jar 单进程部署 |

---

## 二、目录结构

```
hotel-system/
├── backend/                                  # Spring Boot 后端
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/cqucc/hotel/
│       │   ├── HotelSystemApplication.java   # 启动类
│       │   ├── common/                       # 统一响应、全局异常、口令与脱敏工具
│       │   ├── config/                       # Web/跨域/HTTP 客户端/数据初始化
│       │   ├── security/                     # 令牌仓库、鉴权与角色拦截器
│       │   ├── entity/                       # 18 个实体（与表一一对应，由脚本生成）
│       │   ├── dao/                          # CrudDao 通用访问 + 业务/知识库/AI 专用 SQL
│       │   ├── service/                      # 认证、客人、客房、预订、账单、统计、知识库、AI 助手
│       │   ├── controller/                   # 12 个 REST 控制器
│       │   └── ai/
│       │       ├── client/                   # 模型调用层（超时/重试/限流/降级/日志）
│       │       ├── prompt/                   # 提示词模板、JSON Schema、报价单校验
│       │       ├── rag/                      # 切分器 + 本地向量化器
│       │       ├── agent/                    # 工具定义与 8 个业务工具、调用轨迹
│       │       ├── guard/                    # 注入检测、脱敏、输出审查、调用日志
│       │       └── eval/                     # AI 评测服务
│       ├── main/resources/
│       │   ├── application.properties        # 端口、数据库、AI、RAG 配置
│       │   ├── ai/testcases.json             # 26 条 AI 测试集
│       │   ├── db/hotel_system.sql           # 数据库脚本副本（与 sql/ 一致）
│       │   └── static/                       # 前端构建产物（合并部署）
│       └── test/java/com/cqucc/hotel/HotelSystemUnitTests.java   # 10 条单元测试
├── frontend/                                 # Vue 3 前端
│   ├── package.json / vite.config.js / index.html
│   ├── src/
│   │   ├── api/index.js                      # 全部接口封装 + 金额格式化
│   │   ├── router/index.js                   # 路由与登录/角色守卫（多角色 meta.role）
│   │   ├── stores/index.js                   # 登录态
│   │   ├── components/                       # 顶部导航、AI 对话组件
│   │   └── views/                            # 公共 5 页 + 客人端 4 页 + 管理端 14 页
│   └── verify/screenshot.mjs                 # 无头浏览器逐页渲染自检 + 截图
├── sql/hotel_system.sql                      # 数据库脚本（建库、18 张表、种子数据）
├── scripts/
│   ├── acceptance.ps1                        # 一键验收脚本（54 项断言）
│   ├── gen_entities.py                       # 按表结构生成实体类
│   └── build_report.py                       # 由代码与实测数据自动生成课程设计报告
└── docs/                                     # 设计报告、测试报告、冒烟记录、AI 说明、演示脚本、截图
```

---

## 三、快速开始

### 1. 环境要求

- JDK 17（已验证 `17.0.11`）
- MySQL 8.0（默认 `localhost:3306`）
- Node.js 18+（构建前端时需要，已验证 `v24.15.0`）
- Maven 3.8+（或直接使用 IntelliJ IDEA 内置 Maven）

### 2. 初始化数据库

```powershell
# 方式一：命令行导入（Windows）
cmd /c "mysql -uroot -p123456 --default-character-set=utf8mb4 < sql\hotel_system.sql"

# 方式二：用 SQLyog 打开 sql\hotel_system.sql，全选执行
```

脚本会创建数据库 `hotel_system`、18 张表与演示数据（9 个账号、3 名员工、4 位客人、5 种房型、
12 间客房、5 笔历史预订、9 个消费项目、4 条房价策略、8 篇知识库文档）。

> 注意：脚本里的口令字段写为 `__INIT__` 占位符，后端首次启动时会自动生成盐与散列并回写，
> 因此**必须先启动一次后端**才能用账号登录。

### 3. 启动后端

```powershell
cd backend

# 数据库口令与模型密钥一律通过环境变量注入，仓库中不保存任何口令
$env:HOTEL_DB_USER = "root"
$env:HOTEL_DB_PASSWORD = "你的数据库口令"

.\mvnw.cmd -DskipTests spring-boot:run

# 或使用 IDEA 内置 Maven / 直接运行构建产物
& "C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.4\plugins\maven\lib\maven3\bin\mvn.cmd" -DskipTests spring-boot:run
java -jar target\hotel-system.jar
```

启动成功后：

- 健康检查：<http://localhost:8080/api/health>
- 启动日志会打印「演示数据自检」「知识库重建完成」等信息

### 4. 启动前端（开发模式）

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>，Vite 已把 `/api` 代理到 8080。

### 5. 合并部署（一个 jar 提供页面 + 接口）

```powershell
cd frontend
npm run build          # 产物直接输出到 backend/src/main/resources/static
cd ..\backend
.\mvnw.cmd -DskipTests package
java -jar target\hotel-system.jar
# 访问 http://localhost:8080 即可使用完整系统（刷新子路由不 404）
```

---

## 四、演示账号

| 角色 | 账号 | 初始口令 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin`、`manager01` | `123456` | 员工账号、统计报表、知识库、AI 评测、操作日志 |
| 前台 | `frontdesk01`、`frontdesk02` | `123456` | 客人登记、预订、入住、消费登记、退房、结算 |
| 客房服务 | `housekeeper01` | `123456` | 房态查看、标记维修、反馈打扫完成 |
| 客人 | `guest01` ~ `guest04` | `123456` | 自助注册、订房、预存押金、查看预订/账单/流水、AI 问答 |

> `guest01`（张伟）是黄金会员、预存余额充足，适合演示会员折扣与押金抵扣；
> `guest02`（刘敏）备注「对羽毛过敏，需无羽绒枕」，适合演示 AI 入住报告结合客人备注给建议；
> `guest04`（孙丽）备注「需要婴儿床」，适合演示带儿童入住的接待建议。

---

## 五、功能清单

### 前台（未登录可浏览）
1. **首页**：实时房态概览、房型与房价卡片、按楼层的房态一览、能力介绍
2. **房型与房价**：5 种房型对比，按入住离店日期试算房价（策略价 / 会员价 / 门市价）
3. **登录 / 客人自助注册**：注册即自动建立客人档案与客人编号
4. **AI 前台助手**：知识库问答、业务数据助手、客人入住报告、结构化报价单生成与人工确认

### 客人自助（需登录，role=GUEST）
5. 客人中心（会员等级与折扣、预存余额、在线预存、最近流水与预订）
6. 我的预订（列表与状态、取消未入住预订）
7. 我的账单（房费、消费明细、押金抵扣、应收与已收）
8. 预存与消费流水（分类筛选 + 分页）

### 管理端
9. **前台看板**：今日应到店 / 在住 / 应离店三列，含办理入住与退房入口，实时出租率
10. **预订管理**：检索分页、新建预订（选客人 → 选房型 → 日期 → 试算 → 指定房间）、
    办理入住（分配房间对话框）、办理退房、取消、标记未到店
11. **客房管理**：按楼层房态图（五种房态颜色区分）、单房改状态、批量新增房间、房型管理
12. **客人管理**：检索分页、新增/编辑、拉黑与解除、查看客人档案与历史入住
13. **账单结算**：待结算列表、明细查看、登记消费、优惠减免（需填原因）、
    结算（支付方式 + 押金是否退回余额）
14. **消费项目**：餐饮 / 洗衣 / 迷你吧 / 叫醒服务 / 加床的价格与上下架维护
15. **房价策略**：按房型与日期区间定价（周末价、节假日价），启用与停用
16. **数据概览**：客房与出租率、今日营收与累计营收、待结算金额、营收走势、房型业绩、AI 调用统计
17. **房态统计**：房态分布、各房型出租情况、近 7 天入住退房量
18. **财务报表**：营收走势、房型收入、客人消费排行、消费项目排行、渠道分布、最近结算账单
19. **AI 知识库**：文档增删改、索引重建、检索测试（含无答案判定）
20. **AI 评测**：26 条测试集一键运行、逐条结果、AI 调用日志与成本
21. **员工与权限**：员工账号新增、启停、口令重置、系统与 AI 状态

---

## 六、AI 能力实现说明

### 1）大模型 API 接入（统一调用层）
`ai/client/LlmClient` 封装模型厂商差异，业务代码不接触密钥：

- 地址 / 模型 / 密钥 / 超时 / 重试 / 限流 / 单价全部来自 `application.properties` 的 `ai.*`，
  密钥经环境变量 `HOTEL_AI_API_KEY` 注入；
- 支持超时（默认 5s 连接 + 60s 读取）、指数退避重试（默认 2 次）、每分钟限流（默认 20 次）；
- 任一环节失败不抛给前端，而是返回**明确降级文案**（未配置密钥 / 被限流 / 上游异常三种）；
- 每次调用写 `ai_call_log`：场景、模型、提示词版本、token、估算成本、耗时、重试次数、是否降级、错误信息。

### 2）提示词工程与结构化输出（住宿报价单）
- 提示词集中在 `ai/prompt/PromptTemplates`，带版本号（当前 `v1.0`），日志与消息表都会记录版本；
- 报价单要求模型输出严格 JSON，并在请求里带 `response_format: json_object`；
- 服务端用 `QuoteValidator` 按 `JsonSchemas.QUOTE_SCHEMA` 二次校验，除字段完整性外还校验：
  每晚明细条数必须等于晚数且日期连续、房费合计等于各晚单价之和、应付合计等于房费加服务费减优惠；
- 校验失败先做「修复重试」，仍失败则**不入库**，返回人工报价提示；
- 校验通过的报价单以 `DRAFT` 状态入库，必须经**人工确认**（前台或值班经理）才作为沟通依据。

### 3）知识库问答（RAG）
链路：`knowledge_doc` → `TextChunker` 按句切分（默认 220 字/片段，40 字重叠）→
`TextVectorizer` 生成 TF-IDF 稀疏向量（中文单字 + 双字特征，L2 归一化）→ 存 `knowledge_chunk` →
查询时余弦相似度召回 Top-K（默认 4）→ 低于阈值（默认 0.08）直接判定**无答案**。

- 命中时把片段与来源塞进提示词，要求模型「只依据资料回答」，前端展示**引用来源 + 相似度 + 片段原文**；
- 无命中时走无答案提示词，明确说明「知识库中没有找到相关依据」并引导人工咨询，不编造房价与政策；
- 管理端提供「检索测试」接口 `/api/kb/search`，可单独验证切分与召回效果（不消耗模型额度）。

### 4）智能体与工具调用
`ai/agent/HotelTools` 注册 8 个只读业务工具，模型可多轮调用（默认最多 4 步）：

| 工具 | 作用 |
| --- | --- |
| `query_guest` | 按姓名 / 手机号 / 客人编号查档案、会员等级、累计入住与消费、历史入住（姓名手机号脱敏） |
| `query_room_availability` | 指定房型与日期区间的可售房数、价格与房号示例 |
| `room_status` | 实时房态统计、出租率、各房型出租与房费收入 |
| `reservation_query` | 按状态或客人姓名查预订单与金额状态 |
| `revenue_stats` | 累计营收、今日营收、未结算金额、押金余额、消费收入与近 N 天走势 |
| `consume_items` | 可登记的消费项目与价格（餐饮 / 洗衣 / 迷你吧等） |
| `rate_plans` | 生效中的房价策略（周末价、节假日价） |
| `hotel_info` | 房型数量、可住人数、设施等基础信息 |

每步调用都会记录到 `TraceStep`（工具名、参数、返回、耗时、是否成功），前端以「工具调用轨迹」展示，
形成**可追踪的多步任务**。

### 5）AI 评测与防护
- `resources/ai/testcases.json` 内置 **26 条**测试集，覆盖：知识库召回（7 条）、无答案处理（2 条）、
  提示词注入（4 条）、敏感信息脱敏（4 条）、结构化输出校验（3 条）、工具调用与数据查询（6 条）；
- 管理端「AI 评测」页面或 `GET /api/ai/eval/run` 一键运行，返回逐条的输入、期望、实际结果、是否通过与问题分析；
- 输入侧：`AiGuard.checkInput` 做注入特征匹配（中英文、越狱、索要系统提示词）并统一脱敏
  手机号 / 身份证 / 银行卡 / 邮箱；
- 输出侧：`checkOutput` 二次脱敏，命中高风险关键词时追加风险提示；
- 越权控制：工具只读、写操作全部走人工确认，客人账号不能执行员工写操作。

---

## 七、主要接口一览

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login`、`/register`、`/logout`；`GET /api/auth/me` | 四角色登录、客人注册、当前用户 |
| 公共 | `GET /api/public/room-types`、`/rooms`、`/overview`、`/quote` | 房型、房态概览、房价试算 |
| 客人自助 | `GET /api/me/profile`、`/reservations`、`/bills`、`/transactions`；`POST /api/me/deposit` | 客人中心、我的预订/账单/流水、预存 |
| 前台业务 | `GET /api/front/board`、`/guests`、`/quote`、`/rooms/available` | 看板、客人检索、试算、可分配房间 |
| 前台业务 | `POST /api/front/reservations`、`/{id}/checkin`、`/{id}/checkout`、`/{id}/cancel` | 订房、入住、退房、取消 |
| 前台业务 | `POST /api/front/bills/{id}/consume`、`/discount`、`/settle` | 消费登记、优惠、结算 |
| 客房管理 | `GET/POST /api/room/list`、`/save`、`/batch`、`/types`、`/rate-plans` | 客房、房型、房价策略 |
| 客房管理 | `PUT /api/room/{id}/status`；`POST /api/room/clean/{id}` | 房态修改、打扫反馈 |
| 预订/客人 | `GET /api/reservation/list`、`/{id}`；`GET /api/guest/list`、`/{id}` | 预订与客人档案 |
| 账单 | `GET /api/bill/list`、`/{id}`、`/by-reservation/{id}`、`/consume-items` | 账单与消费项目 |
| 统计 | `GET /api/stats/dashboard`、`/finance`、`/room`、`/logs`、`/ai` | 经营与房态统计、日志 |
| 知识库 | `GET/POST/PUT/DELETE /api/kb/docs`；`POST /api/kb/reindex`；`GET /api/kb/search` | 文档管理与检索测试 |
| AI | `POST /api/ai/ask?scene=QA|AGENT|REPORT`；`POST /api/ai/quote/generate`、`/quote/{id}/confirm` | 四类 AI 能力 |
| AI 评测 | `GET /api/ai/eval/run`、`/eval/cases`、`/status` | 测试集评测与运行状态 |
| 员工/权限 | `GET /api/staff/list`；`POST /api/staff/save`；`GET /api/users/list`；`PUT /api/users/{id}/status` | 员工与账号管理 |

统一响应结构：

```json
{ "code": 200, "message": "操作成功", "data": {}, "timestamp": 1791543662042 }
```

错误码约定：`400` 参数/业务校验失败、`401` 未登录或令牌过期、`403` 无权限、`409` 业务冲突、
`429` 触发限流、`500` 服务内部错误。

---

## 八、安全与隐私处理

1. **密钥不入库不入仓**：模型密钥只通过环境变量 `HOTEL_AI_API_KEY` 注入，
   数据库口令只通过 `HOTEL_DB_PASSWORD` 注入，`application.properties` 只引用变量；
   仓库中不含任何真实密钥（`.gitignore` 排除 `*.env`、`*.key`、`*.pem`）。
2. **口令安全**：加盐 SHA-256 + 1024 次迭代保存，登录失败同账号 5 次锁定 15 分钟；
   数据库脚本中口令以 `__INIT__` 占位，需启动后端生成散列。
3. **最小权限**：`/api/staff`、`/api/stats/**`、`/api/kb/**`、`/api/users/**` 仅管理员；
   `/api/front/**`、`/api/room/**`、`/api/reservation/**`、`/api/guest/**`、`/api/bill/**` 仅酒店员工；
   客人账号不能执行写操作，只能通过 `/api/me/**` 访问自己的数据。
4. **数据脱敏**：接口返回与上传模型前统一脱敏身份证号（保留前 6 后 4）、手机号与邮箱；
   智能体工具返回的客人姓名同样脱敏。
5. **账务可追溯**：押金收取、消费登记、结算都写 `guest_transaction`（流水号、变动前后余额、
   业务类型、关联业务 ID、操作人）；押金扣减使用 `SELECT ... FOR UPDATE` 行锁防止并发错账。
6. **AI 风险控制**：模型输出仅作建议；报价单需人工确认；知识库无依据时明确说明而不编造；
   管理端可查看每次 AI 调用的场景、模型、token、耗时与是否降级。
7. **上传数据说明**：演示数据均为虚构；上传到第三方模型的内容仅为知识库片段与脱敏后的
   客人档案 / 房态 / 营收摘要。

---

## 九、测试

- **一键验收脚本**：`scripts/acceptance.ps1`（54 项断言：四角色权限、房态与房价、核心业务闭环、
  异常分支、四项 AI 能力、统计报表）
  ```powershell
  pwsh -File .\scripts\acceptance.ps1     # 验收结果：通过 54 项，失败 0 项
  ```
- **单元测试（自动化）**：`backend/src/test/java/com/cqucc/hotel/HotelSystemUnitTests.java`（10 条：
  AI 降级、密钥不外泄、口令散列、脱敏与注入、切分与检索、报价单校验、房价与押金规则、等级与支付方式）
  ```powershell
  cd backend
  mvn test        # Tests run: 10, Failures: 0, Errors: 0
  ```
- **功能与接口测试**：`docs/测试用例与测试报告.md`（40+ 条用例 + 缺陷闭环记录）
- **AI 专项测试集**：`backend/src/main/resources/ai/testcases.json`（26 条），接口 `/api/ai/eval/run`
- **接口冒烟记录**：`docs/接口冒烟测试记录.md`（实际请求与返回摘要）
- **页面渲染自检**：`frontend/verify/screenshot.mjs`（22 个路由逐页截图，产物在 `docs/screenshots/`）
  ```powershell
  cd frontend
  node verify/screenshot.mjs      # 22 页全部 http=200、无白屏、零脚本错误
  ```

---

## 十、常见问题

**Q1：启动后登录提示「账号或密码错误」怎么办？**
数据库脚本中的口令是占位符，必须**先成功启动一次后端**（会自动写入口令散列）。
若手动改过数据库，可重新导入脚本后再启动一次。

**Q2：AI 回答变成「AI 服务未启用」？**
说明没有注入密钥，系统按设计走降级提示，其它功能不受影响。设置环境变量后重启后端：

```powershell
$env:HOTEL_AI_API_KEY = "你的密钥"
$env:HOTEL_AI_BASE_URL = "https://api.deepseek.com"   # 也可换成其它兼容 OpenAI 协议的服务
$env:HOTEL_AI_MODEL = "deepseek-chat"
```

**Q3：报价单总是不通过校验？**
这是结构化输出的正常防护行为：系统会先自动修复重试一次，仍失败就不入库并提示人工报价。
可以换更强的模型，或在提示词中补充更明确的房型与日期信息后重试。

**Q4：端口冲突？**
后端改 `backend/src/main/resources/application.properties` 的 `server.port`；
前端改 `frontend/vite.config.js` 的 `server.port` 与 `proxy.target`。

**Q5：知识库检索命不中？**
在管理端「AI 知识库 → 检索测试」里输入问题查看相似度；命中偏低可调低 `rag.score-threshold`，
片段过长可调小 `rag.chunk-size`，改完点击「重建向量索引」。

**Q6：客人看不到自己的账单/预订？**
客人请使用 `/guest` 下的自助页面（对应 `/api/me/**` 接口）；
员工接口 `/api/guest/**`、`/api/reservation/**` 对客人返回 403，这是设计上的权限隔离。

---

## 十一、参考文献

[1] 黑马程序员. Vue.js 前端开发实战（第 2 版）[M]. 北京: 人民邮电出版社, 2023.
[2] 黑马程序员. 微信小程序开发实战（第 2 版）[M]. 北京: 人民邮电出版社, 2023.
[3] 汪云飞. 深入实践 Spring Boot[M]. 北京: 机械工业出版社, 2022.
[4] 唐汉明, 翟振兴, 关宝军. 深入浅出 MySQL（第 3 版）[M]. 北京: 人民邮电出版社, 2021.
[5] 李刚. 疯狂 Java 讲义（第 5 版）[M]. 北京: 电子工业出版社, 2022.
[6] 王珊, 萨师煊. 数据库系统概论（第 6 版）[M]. 北京: 高等教育出版社, 2023.
[7] 张奇, 桂韬, 黄萱菁. 大规模语言模型: 从理论到实践[M]. 北京: 电子工业出版社, 2024.
[8] Lewis P, Perez E, Piktus A, et al. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks[C].
    NeurIPS, 2020: 9459-9474.
