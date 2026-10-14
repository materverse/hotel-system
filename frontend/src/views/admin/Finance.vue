<template>
  <div>
    <div class="page-title">财务报表</div>

    <div class="card">
      <div class="toolbar">
        <span class="muted">统计区间</span>
        <el-radio-group v-model="days" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="15">近 15 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button type="primary" style="margin-left:auto" :loading="loading" @click="load">刷新</el-button>
      </div>

      <div v-loading="loading">
        <div class="stat-grid" style="margin-bottom:16px">
          <div class="stat">
            <div class="label">实收营收合计</div>
            <div class="value">￥{{ yuan(summary.revenueCents ?? summary.revenue_cents) }}</div>
          </div>
          <div class="stat">
            <div class="label">房费收入</div>
            <div class="value">￥{{ yuan(summary.roomChargeCents ?? summary.room_charge_cents) }}</div>
          </div>
          <div class="stat">
            <div class="label">消费项目收入</div>
            <div class="value">￥{{ yuan(summary.consumeChargeCents ?? summary.consume_charge_cents) }}</div>
          </div>
          <div class="stat">
            <div class="label">优惠减免合计</div>
            <div class="value" style="color:#b45309">
              ￥{{ yuan(summary.discountCents ?? summary.discount_cents) }}
            </div>
          </div>
          <div class="stat">
            <div class="label">退还押金合计</div>
            <div class="value">￥{{ yuan(summary.refundCents ?? summary.refund_cents) }}</div>
          </div>
          <div class="stat">
            <div class="label">结算笔数</div>
            <div class="value">{{ summary.billCount ?? summary.bill_count ?? 0 }}</div>
          </div>
          <div class="stat">
            <div class="label">平均房价（ADR）</div>
            <div class="value">￥{{ yuan(adrCents) }}</div>
          </div>
          <div class="stat">
            <div class="label">平均每间夜收入（RevPAR）</div>
            <div class="value">￥{{ yuan(revparCents) }}</div>
          </div>
        </div>

        <div class="card" style="border:none;padding:0">
          <div class="card-title">营收走势</div>
          <el-table :data="finance.trend || []" size="small">
            <el-table-column prop="day" label="日期" width="140" />
            <el-table-column prop="bill_count" label="结算笔数" width="110" />
            <el-table-column label="房费收入" width="140">
              <template #default="{ row }">￥{{ yuan(row.room_cents) }}</template>
            </el-table-column>
            <el-table-column label="消费收入" width="140">
              <template #default="{ row }">￥{{ yuan(row.consume_cents) }}</template>
            </el-table-column>
            <el-table-column label="实收合计" width="150">
              <template #default="{ row }"><span class="price">￥{{ yuan(row.revenue_cents) }}</span></template>
            </el-table-column>
          </el-table>
          <div v-if="!(finance.trend || []).length" class="empty">该区间内暂无结算数据</div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="card grow">
        <div class="card-title">房型收入</div>
        <el-table :data="finance.roomTypeStats || []" size="small">
          <el-table-column prop="type_name" label="房型" min-width="130" />
          <el-table-column prop="room_count" label="房间数" width="90" />
          <el-table-column prop="occupied_count" label="在住" width="80" />
          <el-table-column prop="reservation_count" label="累计预订" width="100" />
          <el-table-column label="房费收入" width="140">
            <template #default="{ row }"><span class="price">￥{{ yuan(row.room_revenue_cents) }}</span></template>
          </el-table-column>
        </el-table>
        <div v-if="!(finance.roomTypeStats || []).length" class="empty">暂无房型数据</div>
      </div>

      <div class="card grow">
        <div class="card-title">消费项目排行</div>
        <el-table :data="finance.consumeRanking || []" size="small">
          <el-table-column prop="item_name" label="消费项目" min-width="150" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column label="收入" width="140">
            <template #default="{ row }"><span class="price">￥{{ yuan(row.amount_cents) }}</span></template>
          </el-table-column>
        </el-table>
        <div v-if="!(finance.consumeRanking || []).length" class="empty">暂无消费记录</div>
      </div>
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
          <el-table-column label="累计消费" width="140">
            <template #default="{ row }">￥{{ yuan(row.total_consume_cents) }}</template>
          </el-table-column>
          <el-table-column label="预存余额" width="130">
            <template #default="{ row }">￥{{ yuan(row.balance_cents) }}</template>
          </el-table-column>
          <el-table-column prop="points" label="积分" width="90" />
        </el-table>
        <div v-if="!(finance.guestRanking || []).length" class="empty">暂无客人数据</div>
      </div>

      <div class="card grow">
        <div class="card-title">预订渠道分布</div>
        <el-table :data="finance.channelStats || []" size="small">
          <el-table-column label="渠道" width="140">
            <template #default="{ row }">{{ channelText(row.channel) }}</template>
          </el-table-column>
          <el-table-column prop="cnt" label="预订数" width="100" />
          <el-table-column label="预订金额" width="150">
            <template #default="{ row }">￥{{ yuan(row.amount_cents) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="!(finance.channelStats || []).length" class="empty">暂无预订数据</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">最近结算账单</div>
      <el-table :data="finance.recentBills || []" size="small">
        <el-table-column prop="billNo" label="账单号" width="175" />
        <el-table-column prop="guestName" label="客人" width="100" />
        <el-table-column prop="reservationNo" label="预订单号" width="175" />
        <el-table-column prop="roomNo" label="房间" width="90" />
        <el-table-column label="房费" width="115">
          <template #default="{ row }">￥{{ yuan(row.roomChargeCents) }}</template>
        </el-table-column>
        <el-table-column label="消费" width="115">
          <template #default="{ row }">￥{{ yuan(row.consumeChargeCents) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="110">
          <template #default="{ row }">−￥{{ yuan(row.discountCents) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="120">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.paidCents) }}</span></template>
        </el-table-column>
        <el-table-column label="结算方式" width="110">
          <template #default="{ row }">{{ payTypeText(row.payType) }}</template>
        </el-table-column>
        <el-table-column prop="settledBy" label="结算人" width="110" />
        <el-table-column prop="settledAt" label="结算时间" width="170" />
      </el-table>
      <div v-if="!(finance.recentBills || []).length" class="empty">暂无结算记录</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { channelText, guestLevelTag, guestLevelText, payTypeText, statsApi, yuan } from '@/api'

const days = ref(7)
const loading = ref(false)
const finance = ref({})

const summary = computed(() => finance.value.summary || {})

/** 平均房价：房费收入 / 已结算的间夜数（无间夜数据时退化为房费/笔数） */
const adrCents = computed(() => {
  const s = summary.value
  const room = s.roomChargeCents ?? s.room_charge_cents ?? 0
  const nights = s.nights ?? s.totalNights ?? s.total_nights
  if (nights) return Math.round(room / nights)
  return 0
})

/** RevPAR：房费收入 /（可售房数 × 天数） */
const revparCents = computed(() => {
  const s = summary.value
  const room = s.roomChargeCents ?? s.room_charge_cents ?? 0
  const sellable = s.sellableRoomCount ?? s.sellable_room_count
  if (sellable && days.value) return Math.round(room / (sellable * days.value))
  return 0
})

async function load() {
  loading.value = true
  try {
    const res = await statsApi.finance({ days: days.value })
    finance.value = res.data || {}
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
