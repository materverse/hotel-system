<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">预存与消费流水</div>

    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="txType" @change="onFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="DEPOSIT">预存</el-radio-button>
          <el-radio-button value="CONSUME">消费</el-radio-button>
          <el-radio-button value="REFUND">退款</el-radio-button>
          <el-radio-button value="ADJUST">调整</el-radio-button>
        </el-radio-group>
        <el-button type="primary" style="margin-left:auto" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="txNo" label="流水号" width="175" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small"
                    :type="row.txType === 'DEPOSIT' ? 'success' : row.txType === 'REFUND' ? 'primary' : row.txType === 'CONSUME' ? 'warning' : 'info'">
              {{ txTypeText(row.txType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130">
          <template #default="{ row }">
            <span :class="row.amountCents >= 0 ? 'income' : 'price'">
              {{ row.amountCents > 0 ? '+' : '' }}{{ yuan(row.amountCents) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动后余额" width="130">
          <template #default="{ row }">￥{{ yuan(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            {{ { RESERVATION: '预订押金', SETTLE: '退房结算', OTHER: '其它' }[row.bizType] || row.bizType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="remark" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>

      <el-pagination style="margin-top:12px" layout="total, prev, pager, next, sizes"
                     :total="total" :page-size="size" :current-page="page"
                     :page-sizes="[10, 20, 50]" @current-change="onPage" @size-change="onSizeChange" />
      <div v-if="!records.length && !loading" class="empty">暂无流水记录</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import SiteHeader from '@/components/SiteHeader.vue'
import { guestApi, txTypeText, yuan } from '@/api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const txType = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await guestApi.transactions({
      page: page.value,
      size: size.value,
      txType: txType.value || undefined
    })
    const data = res.data || {}
    records.value = data.records || (Array.isArray(data) ? data : [])
    total.value = data.total ?? records.value.length
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

function onPage(p) {
  page.value = p
  load()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  load()
}

onMounted(load)
</script>
