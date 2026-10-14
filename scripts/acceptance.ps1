# 酒店系统验收脚本：在同一个后端进程生命周期内跑完核心闭环与四项 AI 能力，输出可核对证据
# 用法：pwsh -File scripts\acceptance.ps1
param(
    [string]$Base = "http://localhost:8080/api"
)

$ErrorActionPreference = "Continue"
$script:pass = 0
$script:fail = 0

function Check($name, $condition, $detail) {
    if ($condition) {
        $script:pass++
        Write-Host ("  [通过] " + $name + " —— " + $detail) -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host ("  [失败] " + $name + " —— " + $detail) -ForegroundColor Red
    }
}

function Api($method, $url, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $args = @{ Uri = "$Base$url"; Method = $method; Headers = $headers; TimeoutSec = 240 }
    if ($body) {
        $headers["Content-Type"] = "application/json"
        $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 8 -Compress }
        $args["Body"] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return ((Invoke-WebRequest @args).Content | ConvertFrom-Json)
}

$today = (Get-Date).ToString("yyyy-MM-dd")
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$dayAfter = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")

Write-Host "`n=== 0. 服务健康检查 ===" -ForegroundColor Cyan
$health = Api "GET" "/health" $null $null
Check "健康检查" ($health.code -eq 200) ("status=" + $health.data.status + " service=" + $health.data.service)

Write-Host "`n=== 1. 认证与权限（四角色） ===" -ForegroundColor Cyan
$admin = Api "POST" "/auth/login" '{"username":"admin","password":"123456"}' $null
Check "管理员登录" ($admin.code -eq 200 -and $admin.data.role -eq "ADMIN") ("role=" + $admin.data.role)
$fd = Api "POST" "/auth/login" '{"username":"frontdesk01","password":"123456"}' $null
Check "前台登录" ($fd.code -eq 200 -and $fd.data.role -eq "FRONTDESK") ("staffId=" + $fd.data.staffId)
$hk = Api "POST" "/auth/login" '{"username":"housekeeper01","password":"123456"}' $null
Check "客房服务登录" ($hk.code -eq 200 -and $hk.data.role -eq "HOUSEKEEPING") ("role=" + $hk.data.role)
$guestLogin = Api "POST" "/auth/login" '{"username":"guest01","password":"123456"}' $null
Check "客人登录" ($guestLogin.code -eq 200 -and $guestLogin.data.guestId -ge 1) ("guestId=" + $guestLogin.data.guestId)
$at = $admin.data.token
$ft = $fd.data.token
$ht = $hk.data.token
$gt = $guestLogin.data.token

$bad = Api "POST" "/auth/login" '{"username":"admin","password":"wrong"}' $null
Check "错误口令被拒绝" ($bad.code -eq 401) $bad.message
Check "无令牌访问被拒绝" ((Api "GET" "/front/board" $null $null).code -eq 401) "返回 401"
Check "客人越权访问统计被拒绝" ((Api "GET" "/stats/dashboard" $null $gt).code -eq 403) "返回 403"
Check "客人越权访问客人管理被拒绝" ((Api "GET" "/guest/list" $null $gt).code -eq 403) "返回 403"

Write-Host "`n=== 2. 房型、房价与房态 ===" -ForegroundColor Cyan
$types = Api "GET" "/public/room-types" $null $null
Check "在售房型查询" ($types.data.Count -ge 5) ("共 " + $types.data.Count + " 种")
$overview = Api "GET" "/public/overview" $null $null
Check "房态公开概览" ($overview.data.roomTotal -ge 10) `
    ("总房数=" + $overview.data.roomTotal + " 空闲=" + $overview.data.roomFree + " 在住=" + $overview.data.roomOccupied + " 维修=" + $overview.data.roomMaintenance)
$quotePlan = Api "GET" "/public/quote?typeId=1&checkIn=2026-12-05&checkOut=2026-12-07&roomCount=1" $null $null
Check "房价策略生效（周末价）" ($quotePlan.data.priceSource -like "*策略*") `
    ("单价=" + $quotePlan.data.unitPriceCents + "分 来源=" + $quotePlan.data.priceSource + " 总额=" + $quotePlan.data.totalCents + "分")
$guestRow = (Api "GET" "/front/guests?keyword=%E5%BC%A0%E4%BC%9F" $null $ft).data.records[0]
$quoteMember = Api "GET" "/public/quote?typeId=2&checkIn=$tomorrow&checkOut=$dayAfter&roomCount=1" $null $null
Check "普通日期按门市价" ($quoteMember.data.priceSource -ne $null) ("来源=" + $quoteMember.data.priceSource + " 单价=" + $quoteMember.data.unitPriceCents + "分")

Write-Host "`n=== 3. 核心业务闭环：订房 → 入住 → 登记消费 → 退房 → 结算 ===" -ForegroundColor Cyan
Check "客人检索（证件脱敏）" ($guestRow.id -ge 1 -and $guestRow.maskedIdCard -ne $null) `
    ("客人=" + $guestRow.realName + " 等级=" + $guestRow.memberLevel + " 证件=" + $guestRow.maskedIdCard)
$available = Api "GET" "/front/rooms/available?typeId=1&checkIn=$today&checkOut=$dayAfter" $null $ft
Check "可分配房间查询" ($available.data.Count -ge 1) ("可分配 " + $available.data.Count + " 间，示例 " + $available.data[0].roomNo)
$roomId = $available.data[0].id
$quoteToday = Api "GET" "/front/quote?typeId=1&checkIn=$today&checkOut=$dayAfter&roomCount=1&guestId=$($guestRow.id)" $null $ft
Check "会员价试算" ($quoteToday.code -eq 200) ("单价=" + $quoteToday.data.unitPriceCents + "分 来源=" + $quoteToday.data.priceSource + " 总额=" + $quoteToday.data.totalCents + "分 押金=" + $quoteToday.data.depositCents + "分")

$book = Api "POST" "/front/reservations" ("{`"guestId`":$($guestRow.id),`"typeId`":1,`"roomId`":$roomId,`"checkInDate`":`"$today`",`"checkOutDate`":`"$dayAfter`",`"roomCount`":1,`"guestCount`":1,`"channel`":`"FRONT_DESK`",`"specialRequest`":`"高楼层无烟房`"}") $ft
Check "创建预订" ($book.code -eq 200 -and $book.data.reservationNo) `
    ("单号=" + $book.data.reservationNo + " 总额=" + $book.data.totalCents + "分 押金=" + $book.data.depositCents + "分 价格来源=" + $book.data.priceSource)
$resId = $book.data.reservationId

$bookedRoom = (Api "GET" "/room/$roomId" $null $ft).data
Check "预订后房态变为已预订" ($bookedRoom.status -eq "BOOKED") ("房态=" + $bookedRoom.status)

$conflict = Api "POST" "/front/reservations" ("{`"guestId`":$($guestRow.id),`"typeId`":1,`"roomId`":$roomId,`"checkInDate`":`"$today`",`"checkOutDate`":`"$dayAfter`"}") $ft
Check "同房同期重复预订被拦截" ($conflict.code -eq 400) $conflict.message

$checkIn = Api "POST" "/front/reservations/$resId/checkin" ("{`"roomId`":$roomId,`"idCard`":`"500102199005120011`"}") $ft
Check "办理入住（分配房间+收押金）" ($checkIn.code -eq 200) `
    ("房间=" + $checkIn.data.roomNo + " 押金=" + $checkIn.data.depositCents + "分 预存抵扣=" + $checkIn.data.paidByBalance + " 客人余额=" + $checkIn.data.guestBalanceAfter + "分")
$occupiedRoom = (Api "GET" "/room/$roomId" $null $ft).data
Check "入住后房态变为在住" ($occupiedRoom.status -eq "OCCUPIED") ("房态=" + $occupiedRoom.status)
$again = Api "POST" "/front/reservations/$resId/checkin" '{}' $ft
Check "重复入住被拦截" ($again.code -eq 400) $again.message

$bill = (Api "GET" "/front/bills/by-reservation/$resId" $null $ft).data
Check "入住自动生成账单" ($bill.id -ge 1 -and $bill.status -eq "UNSETTLED") `
    ("账单号=" + $bill.billNo + " 房费=" + $bill.roomChargeCents + "分 押金抵扣=" + $bill.depositCents + "分 应收=" + $bill.totalCents + "分")

$items = Api "GET" "/bill/consume-items?category=%E9%A4%90%E9%A5%AE&status=ON_SALE" $null $ft
Check "消费项目查询（按分类）" ($items.data.Count -ge 2) ("餐饮类 " + $items.data.Count + " 项，示例：" + $items.data[0].itemName + "/" + $items.data[0].priceCents + "分")
$consumeItemId = $items.data[0].id
$consumeBody = '{"consumeItemId":' + $consumeItemId + ',"quantity":2,"remark":"上门送餐"}'
$consume = Api "POST" "/front/bills/$($bill.id)/consume" $consumeBody $ft
Check "登记客房消费" ($consume.code -eq 200) `
    ($consume.data.itemName + " × " + $consume.data.quantity + " = " + $consume.data.amountCents + "分，应收 " + $consume.data.payableCents + "分")
$consumeByRes = Api "POST" "/bill/by-reservation/$resId/consume" ('{"consumeItemId":' + $consumeItemId + ',"quantity":1,"remark":"按预订ID登记"}') $ft
Check "按预订ID登记消费（兼容入口）" ($consumeByRes.code -eq 200) ("金额=" + $consumeByRes.data.amountCents + "分，应收=" + $consumeByRes.data.payableCents + "分")
$discount = Api "POST" "/front/bills/$($bill.id)/discount" '{"amountCents":2000,"reason":"会员生日优惠"}' $ft
Check "账单优惠（需填原因）" ($discount.code -eq 200) ("优惠=" + $discount.data.discountCents + "分，应收=" + $discount.data.payableCents + "分")
$badDiscount = Api "POST" "/front/bills/$($bill.id)/discount" '{"amountCents":1000}' $ft
Check "优惠未填原因被拦截" ($badDiscount.code -eq 400) $badDiscount.message

$checkOut = Api "POST" "/front/reservations/$resId/checkout" '{}' $ft
Check "办理退房（按实际晚数重算房费）" ($checkOut.code -eq 200) `
    ("实际 " + $checkOut.data.stayNights + " 晚，房费=" + $checkOut.data.roomChargeCents + "分，消费=" + $checkOut.data.consumeChargeCents + "分，应付=" + $checkOut.data.payableCents + "分")
$cleaningRoom = (Api "GET" "/room/$roomId" $null $ft).data
Check "退房后房态变为待打扫" ($cleaningRoom.status -eq "CLEANING") ("房态=" + $cleaningRoom.status + " 清洁=" + $cleaningRoom.cleanStatus)
$clean = Api "POST" "/room/clean/$roomId" '{}' $ht
Check "客房服务反馈打扫完成" ($clean.code -eq 200) $clean.message
$freeRoom = (Api "GET" "/room/$roomId" $null $ft).data
Check "打扫完成后房间转为空闲" ($freeRoom.status -eq "FREE") ("房态=" + $freeRoom.status)

$settle = Api "POST" "/front/bills/$($bill.id)/settle" '{"payType":"CARD","refundToBalance":true}' $ft
Check "账单结算（押金抵扣与退还）" ($settle.code -eq 200) `
    ("应收=" + $settle.data.paidCents + "分 退还=" + $settle.data.refundCents + "分 方式=" + $settle.data.payType + " 客人余额=" + $settle.data.guestBalanceAfter + "分")
$settleAgain = Api "POST" "/front/bills/$($bill.id)/settle" '{}' $ft
Check "重复结算被拦截" ($settleAgain.code -eq 400) $settleAgain.message

Write-Host "`n=== 4. 异常分支 ===" -ForegroundColor Cyan
$badDates = Api "GET" "/public/quote?typeId=1&checkIn=2026-12-10&checkOut=2026-12-10" $null $null
Check "离店日期不晚于入住被拦截" ($badDates.code -eq 400) $badDates.message
$past = Api "GET" "/public/quote?typeId=1&checkIn=2020-01-01&checkOut=2020-01-03" $null $null
Check "过去日期被拦截" ($past.code -eq 400) $past.message
$blacklist = Api "PUT" "/guest/$($guestRow.id)/status" '{"status":"BLACKLIST"}' $ft
Check "客人拉黑成功" ($blacklist.code -eq 200) $blacklist.message
$blocked = Api "POST" "/front/reservations" ("{`"guestId`":$($guestRow.id),`"typeId`":1,`"checkInDate`":`"$today`",`"checkOutDate`":`"$dayAfter`"}") $ft
Check "黑名单客人预订被拦截" ($blocked.code -eq 400) $blocked.message
Api "PUT" "/guest/$($guestRow.id)/status" '{"status":"NORMAL"}' $ft | Out-Null
$maintenance = Api "PUT" "/room/4/status" '{"status":"MAINTENANCE"}' $ft
Check "维修未填原因被拦截" ($maintenance.code -eq 400) $maintenance.message
$maintenanceOk = Api "PUT" "/room/4/status" '{"status":"MAINTENANCE","remark":"空调异响待维修"}' $ft
Check "维修房标记成功" ($maintenanceOk.code -eq 200) $maintenanceOk.message

Write-Host "`n=== 5. AI 能力（四项） ===" -ForegroundColor Cyan
$qa = Api "POST" "/ai/ask?scene=QA" '{"question":"入住和退房时间分别是几点？押金怎么收？"}' $ft
Check "知识库问答（带引用来源）" ($qa.code -eq 200 -and $qa.data.citations.Count -ge 1) `
    ("degraded=" + $qa.data.degraded + " 引用=" + $qa.data.citations.Count + " 首条=" + $qa.data.citations[0].title + "(" + $qa.data.citations[0].score + ") 耗时=" + $qa.data.latencyMs + "ms")
$noAnswer = Api "POST" "/ai/ask?scene=QA" '{"question":"帮我写一段爬取酒店点评的 Python 代码"}' $ft
Check "无答案处理（不编造）" ($noAnswer.code -eq 200 -and $noAnswer.data.citations.Count -eq 0) `
    ("引用=" + $noAnswer.data.citations.Count + " 回答开头=" + $noAnswer.data.answer.Substring(0, [Math]::Min(30, $noAnswer.data.answer.Length)))
$agent = Api "POST" "/ai/ask?scene=AGENT" '{"question":"现在有几间房在住？标准大床房今晚还有房吗？"}' $ft
Check "工具调用（多步可追踪）" ($agent.code -eq 200 -and $agent.data.trace.Count -ge 1) `
    ("步数=" + $agent.data.trace.Count + " 工具=" + (($agent.data.trace | ForEach-Object { $_.tool }) -join ","))
$report = Api "POST" "/ai/ask?scene=REPORT" '{"question":"生成这位客人的入住报告"}' $gt
Check "客人入住报告" ($report.code -eq 200 -and $report.data.answer.Length -gt 100) `
    ("字数=" + $report.data.answer.Length + " degraded=" + $report.data.degraded)

$quote = Api "POST" "/ai/quote/generate" ("{`"typeId`":1,`"roomCount`":1,`"checkInDate`":`"$today`",`"checkOutDate`":`"$dayAfter`",`"requirement`":`"希望高楼层无烟房`"}") $ft
Check "结构化住宿报价单通过校验" ($quote.code -eq 200 -and $quote.data.valid) `
    ("quoteId=" + $quote.data.quoteId + " 房型=" + $quote.data.quote.typeName + " 晚数=" + $quote.data.quote.nights + " 应付=" + $quote.data.quote.totalAmount + "元 明细=" + $quote.data.quote.nightlyBreakdown.Count + " 条")
if ($quote.data.valid) {
    $confirm = Api "POST" "/ai/quote/$($quote.data.quoteId)/confirm" '{"approved":true}' $ft
    Check "人工确认后报价单生效" ($confirm.code -eq 200) $confirm.message
    $quoteList = Api "GET" "/ai/quote/list" $null $ft
    Check "报价单列表可查询" ($quoteList.data.Count -ge 1) ("共 " + $quoteList.data.Count + " 条，最新状态=" + $quoteList.data[0].status + " 确认人=" + $quoteList.data[0].confirmedBy)
}

$eval = Api "GET" "/ai/eval/run" $null $at
Check "AI 测试集全部通过" ($eval.data.total -ge 20 -and $eval.data.failed -eq 0) `
    ("总数=" + $eval.data.total + " 通过=" + $eval.data.passed + " 通过率=" + $eval.data.passRate + "%")
$status = Api "GET" "/ai/status" $null $at
Check "AI 状态（不回显密钥）" ($status.code -eq 200 -and $status.data.keyConfigured) `
    ("模型=" + $status.data.model + " 提示词=" + $status.data.promptVersion + " 工具=" + $status.data.toolCount + " 片段=" + $status.data.knowledgeChunks + " 限流=" + $status.data.rateLimitPerMinute + "/min")

Write-Host "`n=== 6. 统计报表与知识库 ===" -ForegroundColor Cyan
$dash = Api "GET" "/stats/dashboard" $null $at
Check "经营概览（含出租率）" ($dash.code -eq 200 -and $dash.data.roomTotal -gt 0) `
    ("客房=" + $dash.data.roomTotal + " 在住=" + $dash.data.roomOccupied + " 出租率=" + $dash.data.occupancyRate + "% 累计营收=" + $dash.data.revenueTotalCents + "分 未结算=" + $dash.data.unsettledCents + "分")
$fin = Api "GET" "/stats/finance?days=7" $null $at
Check "财务报表" ($fin.code -eq 200) `
    ("房型统计=" + $fin.data.roomTypeStats.Count + " 条 客人排行=" + $fin.data.guestRanking.Count + " 条 消费排行=" + $fin.data.consumeRanking.Count + " 条 渠道=" + $fin.data.channelStats.Count + " 条")
$roomStats = Api "GET" "/stats/room" $null $at
Check "房态与出租率统计" ($roomStats.code -eq 200) ("出租率=" + $roomStats.data.occupancyRate + "% 房态分布=" + $roomStats.data.roomStatus.Count + " 类")
$kb = Api "GET" "/kb/search?question=%E9%80%80%E6%88%BF%E6%97%B6%E9%97%B4%E6%98%AF%E5%87%A0%E7%82%B9" $null $at
Check "知识库检索测试" ($kb.code -eq 200 -and $kb.data.hitCount -ge 1) ("命中=" + $kb.data.hitCount + " 阈值=" + $kb.data.threshold + " 首条=" + $kb.data.hits[0].docTitle)
$logs = Api "GET" "/stats/logs?limit=10" $null $at
Check "操作日志可追溯" ($logs.code -eq 200 -and $logs.data.Count -ge 1) ("最近日志=" + $logs.data.Count + " 条，最新：" + $logs.data[0].module + "/" + $logs.data[0].action)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host ("验收结果：通过 " + $script:pass + " 项，失败 " + $script:fail + " 项") -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
if ($script:fail -gt 0) { exit 1 }
