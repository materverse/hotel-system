<template>
  <div>
    <div class="page-title">数据概览</div>

    <div v-loading="loading">
      <div class="stat-grid" style="margin-bottom:16px">
        <div class="stat">
          <div class="label">客房总数 / 可售</div>
          <div class="value">{{ d.roomTotal ?? 0 }} / {{ sellable }}</div>
        </div>
        <div class="stat">
          <div class="label">今日出租率</div>
          <div class="value">{{ occupancy }}%</div>
        </div>
        <div class="stat">
          <div class="label">今日到店 / 应离店</div>
          <div class="value">{{ d.guestToday ?? 0 }} / {{ d.checkoutToday ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">在住 / 待入住预订</div>
          <div class="value">{{ d.checkedIn ?? 0 }} / {{ d.bookingActive ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">今日营收</div>
          <div class="value">￥{{ yuan(d.revenueTodayCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">累计营收</div>
          <div class="value">￥{{ yuan(d.revenueTotalCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">待结算金额</div>
          <div class="value" style="color:#b45309">￥{{ yuan(d.unsettledCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">客人预存押金总额</div>
          <div class="value">￥{{ yuan(d.depositBalanceCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">累计消费项收入</div>
          <div class="value">￥{{ yuan(d.consumeTotalCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">客人总数</div>
          <div class="value">{{ d.guestTotal ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">AI 累计调用 / 降级</div>
          <div class="value">{{ d.aiCallTotal ?? 0 }} / {{ d.aiDegradedTotal ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">知识库文档 / 片段</div>
          <div class="value">{{ status.knowledgeDocs ?? 0 }} / {{ status.knowledgeChunks ?? 0 }}</div>
        </div>
      </div>

      <div class="stat-grid" style="margin-bottom:16px">
        <div class="stat">
          <div class="label">空闲房</div>
          <div class="value" style="color:#1f8a5c">{{ d.roomFree ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">已入住</div>
          <div class="value">{{ d.roomOccupied ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">待打扫</div>
          <div class="value" style="color:#b45309">{{ d.roomCleaning ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">维修中</div>
          <div class="value" style="color:#7a8a97">{{ d.roomMaintenance ?? 0 }}</div>
        </div>
      </div>

      <div class="row">
        <div class="card grow">
          <div class="card-title">近 7 天营收走势</div>
          <el-table :data="finance.trend || []" size="small">
            <el-table-column prop="day" label="日期" width="130" />
            <el-table-column prop="bill_count" label="结算笔数" width="100" />
            <el-table-column label="房费收入" width="130">
              <template #default="{ row }">￥{{ yuan(row.room_cents) }}</template>
            </el-table-column>
            <el-table-column label="消费收入" width="130">
              <template #default="{ row }">￥{{ yuan(row.consume_cents) }}</template>
            </el-table-column>
            <el-table-column label="实收合计" width="130">
              <template #default="{ row }">
                <span class="price">￥{{ yuan(row.revenue_cents) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!(finance.trend || []).length" class="empty">暂无结算数据</div>
        </div>

        <div class="card grow">
          <div class="card-title">AI 调用统计（按场景）</div>
          <el-table :data="aiStat.scenes || []" size="small">
            <el-table-column prop="scene" label="场景" width="120" />
            <el-table-column prop="call_count" label="调用次数" width="100" />
            <el-table-column prop="success_count" label="成功" width="80" />
            <el-table-column prop="degraded_count" label="降级" width="80" />
            <el-table-column prop="avg_latency" label="平均耗时(ms)" width="120" />
            <el-table-column label="成本" width="110">
              <template #default="{ row }">￥{{ yuan(row.cost_cents) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!(aiStat.scenes || []).length" class="empty">暂无 AI 调用记录</div>
          <div class="muted" style="margin-top:10px;line-height:1.9">
            模型状态：{{ status.enabled ? '已启用' : '未启用' }}
            ｜ 密钥：{{ status.keyConfigured ? '已配置' : '未配置' }}<br />
            模型 {{ status.model || '-' }} ｜ 提示词 {{ status.promptVersion || '-' }}
            ｜ 今日调用 {{ status.todayCalls ?? 0 }} 次，成本 ￥{{ yuan(status.todayCostCents) }}
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">房型业绩</div>
        <el-table :data="finance.roomTypeStats || []" size="small">
          <el-table-column prop="type_name" label="房型" min-width="140" />
          <el-table-column label="门市价" width="120">
            <template #default="{ row }">￥{{ yuan(row.price_cents) }}</template>
          </el-table-column>
          <el-table-column prop="room_count" label="房间数" width="90" />
          <el-table-column prop="occupied_count" label="在住" width="80" />
          <el-table-column prop="reservation_count" label="累计预订" width="100" />
          <el-table-column label="房费收入" width="140">
            <template #default="{ row }">
              <span class="price">￥{{ yuan(row.room_revenue_cents) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!(finance.roomTypeStats || []).length" class="empty">暂无房型数据</div>
      </div>

      <div class="row">
        <div class="card grow">
          <div class="card-title">客人消费排行</div>
          <el-table :data="finance.guestRanking || []" size="small">
            <el-table-column prop="guest_name" label="客人" width="100" />
            <el-table-column prop="guest_no" label="客人编号" width="130" />
            <el-table-column label="等级" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="guestLevelTag(row.member_level)">
                  {{ guestLevelText(row.member_level) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="total_stay_count" label="入住次数" width="100" />
            <el-table-column label="累计消费" width="130">
              <template #default="{ row }">￥{{ yuan(row.total_consume_cents) }}</template>
            </el-table-column>
            <el-table-column label="预存余额" width="130">
              <template #default="{ row }">￥{{ yuan(row.balance_cents) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!(finance.guestRanking || []).length" class="empty">暂无客人数据</div>
        </div>

        <div class="card grow">
          <div class="card-title">最近结算账单</div>
          <el-table :data="finance.recentBills || []" size="small">
            <el-table-column prop="billNo" label="账单号" width="160" />
            <el-table-column prop="guestName" label="客人" width="90" />
            <el-table-column prop="roomNo" label="房间" width="80" />
            <el-table-column label="实收" width="120">
              <template #default="{ row }"><span class="price">￥{{ yuan(row.paidCents) }}</span></template>
            </el-table-column>
            <el-table-column prop="settledAt" label="结算时间" width="170" />
          </el-table>
          <div v-if="!(finance.recentBills || []).length" class="empty">暂无结算记录</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { aiApi, guestLevelTag, guestLevelText, statsApi, yuan } from '@/api'

const d = ref({})
const finance = ref({})
const aiStat = ref({})
const status = ref({})
const loading = ref(false)

/** 可售房间数：维修房不计入分母，与后端出租率口径一致 */
const sellable = computed(() => {
  const total = Number(d.value.roomTotal || 0)
  const maintenance = Number(d.value.roomMaintenance || 0)
  return Math.max(0, total - maintenance)
})

/** 出租率优先取后端计算值，缺失时前端按同一口径兜底 */
const occupancy = computed(() => {
  if (d.value.occupancyRate !== undefined && d.value.occupancyRate !== null) {
    return d.value.occupancyRate
  }
  if (!sellable.value) return 0
  return Math.round(((Number(d.value.roomOccupied || 0) * 1000) / sellable.value)) / 10
})

async function load() {
  loading.value = true
  try {
    const [dash, fin, ai] = await Promise.all([
      statsApi.dashboard(),
      statsApi.finance({ days: 7 }),
      statsApi.ai({ limit: 20 })
    ])
    d.value = dash.data || {}
    finance.value = fin.data || {}
    aiStat.value = ai.data || {}
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
  // AI 状态单独取，失败不影响主看板
  try {
    const res = await aiApi.status()
    status.value = res.data || {}
  } catch (e) {
    status.value = {}
  }
}

onMounted(load)
</script>
