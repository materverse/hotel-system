<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">我的账单</div>

    <div class="card">
      <div class="toolbar">
        <span class="muted">展开账单可查看房费、消费明细与押金抵扣情况。</span>
        <el-button type="primary" style="margin-left:auto" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding:8px 16px">
              <div class="card-title" style="margin-bottom:8px">消费明细</div>
              <el-table :data="rowItems(row)" size="small">
                <el-table-column label="类型" width="100">
                  <template #default="{ row: it }">
                    <el-tag size="small"
                            :type="it.itemType === 'ROOM' ? 'primary' : it.itemType === 'CONSUME' ? 'warning' : 'info'">
                      {{ it.itemType === 'ROOM' ? '房费' : it.itemType === 'CONSUME' ? '消费' : '其它' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="itemName" label="项目" min-width="200" />
                <el-table-column label="单价" width="110">
                  <template #default="{ row: it }">￥{{ yuan(it.unitPriceCents) }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="80" />
                <el-table-column label="金额" width="120">
                  <template #default="{ row: it }">
                    <span :class="it.amountCents < 0 ? 'income' : 'price'">￥{{ yuan(it.amountCents) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="160" />
              </el-table>
              <div v-if="!rowItems(row).length" class="empty">暂无消费明细（账单明细仅在结算前后台可见）</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="billNo" label="账单号" width="175" />
        <el-table-column prop="reservationNo" label="预订单号" width="175" />
        <el-table-column prop="roomNo" label="房间" width="90" />
        <el-table-column label="房费" width="110">
          <template #default="{ row }">￥{{ yuan(row.roomChargeCents) }}</template>
        </el-table-column>
        <el-table-column label="消费" width="110">
          <template #default="{ row }">￥{{ yuan(row.consumeChargeCents) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="110">
          <template #default="{ row }">−￥{{ yuan(row.discountCents) }}</template>
        </el-table-column>
        <el-table-column label="押金抵扣" width="110">
          <template #default="{ row }">−￥{{ yuan(row.depositCents) }}</template>
        </el-table-column>
        <el-table-column label="应收" width="120">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.totalCents) }}</span></template>
        </el-table-column>
        <el-table-column label="已收" width="110">
          <template #default="{ row }">￥{{ yuan(row.paidCents) }}</template>
        </el-table-column>
        <el-table-column label="退还押金" width="110">
          <template #default="{ row }">￥{{ yuan(row.refundCents) }}</template>
        </el-table-column>
        <el-table-column label="结算方式" width="110">
          <template #default="{ row }">{{ payTypeText(row.payType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="billStatusTag(row.status)">{{ billStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="settledAt" label="结算时间" width="170" />
      </el-table>

      <div v-if="!records.length && !loading" class="empty">暂无账单记录</div>

      <div v-if="summary.count" class="stat-grid" style="margin-top:14px">
        <div class="stat">
          <div class="label">账单数</div>
          <div class="value">{{ summary.count }}</div>
        </div>
        <div class="stat">
          <div class="label">累计应收</div>
          <div class="value">￥{{ yuan(summary.total) }}</div>
        </div>
        <div class="stat">
          <div class="label">累计已收</div>
          <div class="value">￥{{ yuan(summary.paid) }}</div>
        </div>
        <div class="stat">
          <div class="label">累计优惠</div>
          <div class="value">￥{{ yuan(summary.discount) }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">结算说明</div>
      <div class="muted" style="line-height:2">
        · 应收合计 = 房费 + 消费 − 优惠减免 − 押金抵扣。<br />
        · 押金抵扣后如有剩余，结算时可退回你的预存余额或现金退还；不足时需补交差额。<br />
        · 退房时房费按实际住宿晚数重算，14:00 之后退房会加收延迟退房费。<br />
        · 如对账单有疑问，请联系前台核对，所有明细与流水均可追溯。
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import SiteHeader from '@/components/SiteHeader.vue'
import { billStatusTag, billStatusText, guestApi, payTypeText, yuan } from '@/api'

const records = ref([])
const loading = ref(false)

const summary = computed(() => {
  const list = records.value || []
  return {
    count: list.length,
    total: list.reduce((s, r) => s + (r.totalCents || 0), 0),
    paid: list.reduce((s, r) => s + (r.paidCents || 0), 0),
    discount: list.reduce((s, r) => s + (r.discountCents || 0), 0)
  }
})

/** 明细可能以 items 数组或 JSON 字符串两种形式返回，这里统一成数组 */
function rowItems(row) {
  if (Array.isArray(row.items)) return row.items
  if (typeof row.items === 'string' && row.items) {
    try {
      const parsed = JSON.parse(row.items)
      return Array.isArray(parsed) ? parsed : []
    } catch (e) {
      return []
    }
  }
  return []
}

async function load() {
  loading.value = true
  try {
    const res = await guestApi.bills()
    const data = res.data
    records.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
