<template>
  <div>
    <div class="page-title">账单结算</div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:150px" @change="search">
          <el-option label="待结算" value="UNSETTLED" />
          <el-option label="已结算" value="SETTLED" />
          <el-option label="已作废" value="VOID" />
        </el-select>
        <el-input v-model="query.guestId" placeholder="按客人 ID 筛选" style="width:170px" clearable
                  @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button style="margin-left:auto" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
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
        <el-table-column label="押金抵扣" width="110">
          <template #default="{ row }">−￥{{ yuan(row.depositCents) }}</template>
        </el-table-column>
        <el-table-column label="应收" width="120">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.totalCents) }}</span></template>
        </el-table-column>
        <el-table-column label="已收" width="110">
          <template #default="{ row }">￥{{ yuan(row.paidCents) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="billStatusTag(row.status)">{{ billStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="settledAt" label="结算时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'UNSETTLED'" size="small" type="success" @click="openSettle(row)">
              结算
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:12px" layout="total, prev, pager, next, sizes"
                     :total="total" :page-size="size" :current-page="page"
                     :page-sizes="[10, 20, 50]" @current-change="onPage" @size-change="onSizeChange" />
      <div v-if="!records.length && !loading" class="empty">没有符合条件的账单</div>
    </div>

    <!-- 账单详情 -->
    <el-dialog v-model="detailVisible" :title="`账单详情：${detail.billNo || ''}`" width="900px" top="6vh">
      <div v-loading="detailLoading">
        <el-descriptions :column="3" border size="small" style="margin-bottom:14px">
          <el-descriptions-item label="账单号">{{ detail.billNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="billStatusTag(detail.status)">{{ billStatusText(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客人">{{ detail.guestName }}</el-descriptions-item>
          <el-descriptions-item label="预订单号">{{ detail.reservationNo }}</el-descriptions-item>
          <el-descriptions-item label="房间">{{ detail.roomNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ payTypeText(detail.payType) }}</el-descriptions-item>
          <el-descriptions-item label="房费小计">￥{{ yuan(detail.roomChargeCents) }}</el-descriptions-item>
          <el-descriptions-item label="消费小计">￥{{ yuan(detail.consumeChargeCents) }}</el-descriptions-item>
          <el-descriptions-item label="优惠减免">−￥{{ yuan(detail.discountCents) }}</el-descriptions-item>
          <el-descriptions-item label="押金抵扣">−￥{{ yuan(detail.depositCents) }}</el-descriptions-item>
          <el-descriptions-item label="应收合计">
            <span class="price">￥{{ yuan(detail.totalCents) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="已收金额">￥{{ yuan(detail.paidCents) }}</el-descriptions-item>
          <el-descriptions-item label="退还押金">￥{{ yuan(detail.refundCents) }}</el-descriptions-item>
          <el-descriptions-item label="结算人">{{ detail.settledBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算时间">{{ detail.settledAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-title">账单明细</div>
        <el-table :data="detail.items || []" size="small">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small"
                      :type="row.itemType === 'ROOM' ? 'primary' : row.itemType === 'CONSUME' ? 'warning' : 'info'">
                {{ row.itemType === 'ROOM' ? '房费' : row.itemType === 'CONSUME' ? '消费' : '其它' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="itemName" label="项目" min-width="220" />
          <el-table-column label="单价" width="120">
            <template #default="{ row }">￥{{ yuan(row.unitPriceCents) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="金额" width="130">
            <template #default="{ row }">
              <span :class="row.amountCents < 0 ? 'income' : 'price'">￥{{ yuan(row.amountCents) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" />
          <el-table-column prop="createdAt" label="登记时间" width="170" />
        </el-table>
        <div v-if="!(detail.items || []).length" class="empty">暂无账单明细</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail.status === 'UNSETTLED'" type="warning" @click="openConsume(detail)">
          登记消费
        </el-button>
        <el-button v-if="detail.status === 'UNSETTLED'" type="info" @click="openDiscount(detail)">优惠减免</el-button>
        <el-button v-if="detail.status === 'UNSETTLED'" type="success" @click="openSettle(detail)">结算账单</el-button>
      </template>
    </el-dialog>

    <!-- 登记消费 -->
    <el-dialog v-model="consumeVisible" title="登记消费" width="540px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="账单号">{{ settleTarget.billNo }}</el-descriptions-item>
        <el-descriptions-item label="客人">{{ settleTarget.guestName }}</el-descriptions-item>
        <el-descriptions-item label="当前消费小计">￥{{ yuan(settleTarget.consumeChargeCents) }}</el-descriptions-item>
        <el-descriptions-item label="当前应收">￥{{ yuan(settleTarget.totalCents) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="消费项目">
          <el-select v-model="consumeForm.consumeItemId" placeholder="请选择消费项目" filterable style="width:100%">
            <el-option v-for="it in consumeItems" :key="it.id"
                       :label="`${it.itemName}（${it.category}，￥${yuan(it.priceCents)}/${it.unit}）`" :value="it.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="consumeForm.quantity" :min="1" :max="99" />
          <span class="muted" style="margin-left:8px">
            小计 ￥{{ yuan((selectedConsumeItem?.priceCents || 0) * consumeForm.quantity) }}
          </span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="consumeForm.remark" placeholder="如：送至 8801 房 / 客人现场点单" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false"
                title="仅已入住的订单可以登记消费；房费与消费之和构成应收，结算时按押金多退少补。" />
      <template #footer>
        <el-button @click="consumeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConsume">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 优惠减免 -->
    <el-dialog v-model="discountVisible" title="优惠减免" width="500px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="账单号">{{ settleTarget.billNo }}</el-descriptions-item>
        <el-descriptions-item label="客人">{{ settleTarget.guestName }}</el-descriptions-item>
        <el-descriptions-item label="房费 + 消费">
          ￥{{ yuan((settleTarget.roomChargeCents || 0) + (settleTarget.consumeChargeCents || 0)) }}
        </el-descriptions-item>
        <el-descriptions-item label="已优惠">￥{{ yuan(settleTarget.discountCents) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="优惠金额">
          <el-input-number v-model="discountYuan" :min="0.01" :max="100000" :precision="2" :step="10" />
          <span class="muted" style="margin-left:8px">元</span>
        </el-form-item>
        <el-form-item label="优惠原因">
          <el-input v-model="discountReason" type="textarea" :rows="2"
                    placeholder="必填，用于审计追溯，如：会员生日折扣 / 房间空调故障补偿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="discountVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDiscount">确认减免</el-button>
      </template>
    </el-dialog>

    <!-- 结算 -->
    <el-dialog v-model="settleVisible" title="账单结算" width="560px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="账单号">{{ settleTarget.billNo }}</el-descriptions-item>
        <el-descriptions-item label="客人">{{ settleTarget.guestName }}</el-descriptions-item>
        <el-descriptions-item label="房费">￥{{ yuan(settleTarget.roomChargeCents) }}</el-descriptions-item>
        <el-descriptions-item label="消费">￥{{ yuan(settleTarget.consumeChargeCents) }}</el-descriptions-item>
        <el-descriptions-item label="优惠减免">−￥{{ yuan(settleTarget.discountCents) }}</el-descriptions-item>
        <el-descriptions-item label="押金抵扣">−￥{{ yuan(settleTarget.depositCents) }}</el-descriptions-item>
      </el-descriptions>
      <div class="quote-sheet" style="margin-bottom:12px">
        <div class="quote-row total">
          <span>{{ netAmount >= 0 ? '客人应补交' : '应退还客人' }}</span>
          <span>￥{{ yuan(Math.abs(netAmount)) }}</span>
        </div>
      </div>
      <el-form label-width="120px">
        <el-form-item label="结算方式">
          <el-radio-group v-model="settleForm.payType">
            <el-radio-button value="CASH">现金</el-radio-button>
            <el-radio-button value="CARD">刷卡</el-radio-button>
            <el-radio-button value="ONLINE">线上支付</el-radio-button>
            <el-radio-button value="BALANCE">预存余额</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="押金退回余额">
          <el-switch v-model="settleForm.refundToBalance" />
          <div class="muted" style="margin-top:4px">
            开启后，押金抵扣的剩余部分退回客人预存余额；关闭则记为现金退还。
          </div>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false"
                title="结算后账单不可再修改；应收 = 房费 + 消费 − 优惠 − 押金抵扣。" />
      <template #footer>
        <el-button @click="settleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSettle">确认结算</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  billApi,
  billStatusTag,
  billStatusText,
  frontApi,
  payTypeText,
  toCents,
  yuan
} from '@/api'

const route = useRoute()

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const submitting = ref(false)
const detailLoading = ref(false)

const query = reactive({ status: 'UNSETTLED', guestId: '' })

const detailVisible = ref(false)
const consumeVisible = ref(false)
const discountVisible = ref(false)
const settleVisible = ref(false)

const detail = ref({})
const settleTarget = ref({})
const consumeItems = ref([])

const consumeForm = reactive({ consumeItemId: null, quantity: 1, remark: '' })
const discountYuan = ref(50)
const discountReason = ref('')
const settleForm = reactive({ payType: 'CASH', refundToBalance: true })

const selectedConsumeItem = computed(() =>
  consumeItems.value.find((i) => i.id === consumeForm.consumeItemId) || null
)

/** 押金抵扣后的净额：正数为客人还需补交，负数为应退客人 */
const netAmount = computed(() => {
  const t = settleTarget.value || {}
  return (t.roomChargeCents || 0) + (t.consumeChargeCents || 0) - (t.discountCents || 0) - (t.depositCents || 0)
})

async function load() {
  loading.value = true
  try {
    const res = await billApi.list({
      status: query.status || undefined,
      guestId: query.guestId || undefined,
      page: page.value,
      size: size.value
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

function search() {
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

async function loadConsumeItems() {
  try {
    const res = await billApi.consumeItems({ status: 'ON_SALE' })
    const data = res.data
    consumeItems.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    consumeItems.value = []
  }
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  try {
    const res = await billApi.detail(row.id)
    detail.value = res.data || row
  } catch (e) {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

function openConsume(row) {
  settleTarget.value = row
  consumeForm.consumeItemId = null
  consumeForm.quantity = 1
  consumeForm.remark = ''
  consumeVisible.value = true
  loadConsumeItems()
}

async function submitConsume() {
  if (!consumeForm.consumeItemId) {
    ElMessage.warning('请选择消费项目')
    return
  }
  submitting.value = true
  try {
    await frontApi.addConsume(settleTarget.value.id, {
      consumeItemId: consumeForm.consumeItemId,
      quantity: consumeForm.quantity,
      remark: consumeForm.remark || undefined
    })
    ElMessage.success('消费已登记')
    consumeVisible.value = false
    detailVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function openDiscount(row) {
  settleTarget.value = row
  discountYuan.value = 50
  discountReason.value = ''
  discountVisible.value = true
}

async function submitDiscount() {
  if (!discountReason.value || !discountReason.value.trim()) {
    ElMessage.warning('必须填写优惠原因，便于审计追溯')
    return
  }
  submitting.value = true
  try {
    const res = await frontApi.addDiscount(settleTarget.value.id, {
      amountCents: toCents(discountYuan.value),
      reason: discountReason.value.trim()
    })
    ElMessage.success(`优惠已登记，累计优惠 ￥${yuan(res.data?.discountCents)}，应收 ￥${yuan(res.data?.payableCents)}`)
    discountVisible.value = false
    detailVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function openSettle(row) {
  settleTarget.value = row
  settleForm.payType = 'CASH'
  settleForm.refundToBalance = true
  settleVisible.value = true
}

async function submitSettle() {
  submitting.value = true
  try {
    const res = await frontApi.settle(settleTarget.value.id, {
      payType: settleForm.payType,
      refundToBalance: settleForm.refundToBalance
    })
    const data = res.data || {}
    ElMessage.success(
      `结算完成：应收 ￥${yuan(data.paidCents)}，退还押金 ￥${yuan(data.refundCents)}` +
        (data.refundToBalance ? '（已退回客人预存余额）' : '')
    )
    settleVisible.value = false
    detailVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 从前台看板跳转过来时带 reservationId，自动打开对应账单
  const reservationId = route.query.reservationId
  if (reservationId) {
    billApi
      .byReservation(reservationId)
      .then((res) => {
        const bill = res.data
        if (bill) {
          detail.value = bill
          detailVisible.value = true
        }
      })
      .catch(() => {
        // 账单可能尚未生成，忽略
      })
  }
  load()
  loadConsumeItems()
})
</script>
