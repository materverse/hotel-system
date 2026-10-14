<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">我的预订 / 在住</div>

    <el-alert type="info" :closable="false" style="margin-bottom:14px"
              title="取消预订需前台确认：未入住的预订可在此提交取消申请，若提示权限不足请直接联系前台（电话 023-8888 6666）办理。" />

    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="status" @change="onFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="BOOKED">已预订</el-radio-button>
          <el-radio-button value="CHECKED_IN">在住</el-radio-button>
          <el-radio-button value="CHECKED_OUT">已退房</el-radio-button>
          <el-radio-button value="CANCELLED">已取消</el-radio-button>
        </el-radio-group>
        <el-button type="primary" style="margin-left:auto" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="reservationNo" label="预订单号" width="170" />
        <el-table-column prop="typeName" label="房型" width="130" />
        <el-table-column prop="roomNo" label="房间" width="90">
          <template #default="{ row }">{{ row.roomNo || '入住时分配' }}</template>
        </el-table-column>
        <el-table-column label="入住 → 离店" width="200">
          <template #default="{ row }">{{ row.checkInDate }} → {{ row.checkOutDate }}</template>
        </el-table-column>
        <el-table-column label="间夜 / 人数" width="120">
          <template #default="{ row }">
            {{ row.roomCount }} 间 × {{ row.nights }} 晚 / {{ row.guestCount }} 人
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }">￥{{ yuan(row.unitPriceCents) }}</template>
        </el-table-column>
        <el-table-column label="房费总额" width="120">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.totalCents) }}</span></template>
        </el-table-column>
        <el-table-column label="押金" width="110">
          <template #default="{ row }">￥{{ yuan(row.depositCents) }}</template>
        </el-table-column>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">{{ channelText(row.channel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="reservationStatusTag(row.status)">
              {{ reservationStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specialRequest" label="特殊要求" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'BOOKED'" size="small" type="danger" @click="cancel(row)">
              取消预订
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
                     :page-size="size" :current-page="page" @current-change="onPage" />
      <div v-if="!records.length && !loading" class="empty">暂无预订记录</div>
    </div>

    <div class="card">
      <div class="card-title">预订与入住须知</div>
      <div class="muted" style="line-height:2">
        · 入住时间 14:00 之后，离店时间 12:00 之前；提前入住或延迟退房以酒店房态为准。<br />
        · 预订单保留至入住当日 18:00，超时未到店系统会自动释放房间并可能按未到店处理。<br />
        · 未入住的预订可在线取消；已入住（在住）的订单需到前台办理退房结算。<br />
        · 押金在办理入住时收取，退房结算后按实际消费多退少补。
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import {
  channelText,
  frontApi,
  guestApi,
  reservationStatusTag,
  reservationStatusText,
  yuan
} from '@/api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await guestApi.reservations({
      page: page.value,
      size: size.value,
      status: status.value || undefined
    })
    const data = res.data || {}
    // 兼容后端返回分页对象或裸数组两种情况
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

async function cancel(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `确认取消预订单 ${row.reservationNo}（${row.checkInDate} 入住，${row.typeName}）？请填写取消原因：`,
      '取消预订',
      {
        type: 'warning',
        inputPlaceholder: '如：行程变更 / 改期入住',
        inputValidator: (v) => (v && v.trim() ? true : '请填写取消原因'),
        confirmButtonText: '确认取消',
        cancelButtonText: '暂不取消'
      }
    )
    reason = res.value
  } catch (e) {
    return
  }
  try {
    // 取消走后台的预订取消接口（与前台共用同一套校验与释放房间逻辑）。
    // 接口契约中未定义客人自助取消端点，若后端限定为员工角色会返回 403，此时提示客人联系前台。
    await frontApi.cancelReservation(row.id, { reason })
    ElMessage.success('预订已取消，房间已释放')
    load()
  } catch (e) {
    if (e && (e.code === 403 || (e.response && e.response.status === 403))) {
      ElMessage.warning('在线取消需前台确认，请致电前台 023-8888 6666 由工作人员为您取消')
    }
    // 其它错误已由拦截器提示
  }
}

onMounted(load)
</script>
