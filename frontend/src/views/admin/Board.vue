<template>
  <div>
    <div class="page-title">
      前台看板
      <span class="muted" style="font-weight:400;font-size:13px">
        {{ board.date || todayText }} ｜ 今日出租率 {{ board.occupancyRate ?? 0 }}%
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-button type="primary" :loading="loading" @click="load">刷新看板</el-button>
        <el-button @click="$router.push('/admin/reservations')">新建预订</el-button>
        <el-button @click="$router.push('/admin/rooms')">房态图</el-button>
        <el-button @click="$router.push('/admin/bills')">待结算账单</el-button>
        <span class="muted" style="margin-left:auto">
          入住时间 14:00 之后 ｜ 离店时间 12:00 之前 ｜ 预订保留至当日 18:00
        </span>
      </div>

      <div class="stat-grid">
        <div class="stat">
          <div class="label">今日应到店</div>
          <div class="value">{{ arrivals.length }}</div>
        </div>
        <div class="stat">
          <div class="label">当前在住</div>
          <div class="value">{{ inHouse.length }}</div>
        </div>
        <div class="stat">
          <div class="label">今日应离店</div>
          <div class="value">{{ departures.length }}</div>
        </div>
        <div class="stat">
          <div class="label">今日出租率</div>
          <div class="value">{{ board.occupancyRate ?? 0 }}%</div>
        </div>
      </div>
    </div>

    <div class="row" v-loading="loading">
      <!-- 应到店 -->
      <div class="card board-col">
        <div class="card-title">
          今日应到店（{{ arrivals.length }}）
          <el-tag size="small" type="warning" style="margin-left:6px">待办理入住</el-tag>
        </div>
        <div v-for="r in arrivals" :key="r.id" class="board-item">
          <div class="top">
            <span class="no">{{ r.reservationNo }}</span>
            <el-tag size="small" :type="reservationStatusTag(r.status)">
              {{ reservationStatusText(r.status) }}
            </el-tag>
          </div>
          <div class="muted" style="line-height:1.9">
            客人：{{ r.guestName }}（{{ r.guestPhone || '未留电话' }}）<br />
            房型：{{ r.typeName }}<span v-if="r.roomNo"> ｜ 预定房间 {{ r.roomNo }}</span><br />
            {{ r.checkInDate }} → {{ r.checkOutDate }}（{{ r.roomCount }} 间 × {{ r.nights }} 晚 / {{ r.guestCount }} 人）<br />
            单价 ￥{{ yuan(r.unitPriceCents) }} ｜ 房费 ￥{{ yuan(r.totalCents) }} ｜ 押金 ￥{{ yuan(r.depositCents) }}<br />
            渠道 {{ channelText(r.channel) }}
            <template v-if="r.specialRequest"><br />特殊要求：{{ r.specialRequest }}</template>
          </div>
          <div style="margin-top:8px">
            <el-button size="small" type="primary" @click="openCheckin(r)">办理入住</el-button>
            <el-button size="small" type="danger" @click="cancelReservation(r)">取消</el-button>
            <el-button size="small" @click="noShow(r)">标记未到店</el-button>
          </div>
        </div>
        <div v-if="!arrivals.length" class="empty">今日没有待入住的预订</div>
      </div>

      <!-- 在住 -->
      <div class="card board-col">
        <div class="card-title">
          当前在住（{{ inHouse.length }}）
          <el-tag size="small" type="success" style="margin-left:6px">可登记消费</el-tag>
        </div>
        <div v-for="r in inHouse" :key="r.id" class="board-item">
          <div class="top">
            <span class="no">{{ r.reservationNo }}</span>
            <el-tag size="small" :type="reservationStatusTag(r.status)">
              {{ reservationStatusText(r.status) }}
            </el-tag>
            <el-tag v-if="isDepartureToday(r)" size="small" type="danger">今日应离店</el-tag>
          </div>
          <div class="muted" style="line-height:1.9">
            客人：{{ r.guestName }} ｜ 房间 {{ r.roomNo || '未分配' }}<br />
            房型：{{ r.typeName }}<br />
            {{ r.checkInDate }} → {{ r.checkOutDate }}（{{ r.roomCount }} 间 × {{ r.nights }} 晚）<br />
            房费 ￥{{ yuan(r.totalCents) }} ｜ 账单应收 ￥{{ yuan(r.billTotalCents) }}<br />
            入住时间：{{ r.checkInAt || '-' }}
          </div>
          <div style="margin-top:8px">
            <el-button size="small" type="success" @click="checkout(r)">办理退房</el-button>
            <el-button size="small" @click="openBill(r)">账单/登记消费</el-button>
          </div>
        </div>
        <div v-if="!inHouse.length" class="empty">当前没有在住客人</div>
      </div>

      <!-- 应离店 -->
      <div class="card board-col">
        <div class="card-title">
          今日应离店（{{ departures.length }}）
          <el-tag size="small" type="danger" style="margin-left:6px">需结算</el-tag>
        </div>
        <div v-for="r in departures" :key="r.id" class="board-item">
          <div class="top">
            <span class="no">{{ r.reservationNo }}</span>
            <el-tag size="small" type="danger">应离店</el-tag>
          </div>
          <div class="muted" style="line-height:1.9">
            客人：{{ r.guestName }} ｜ 房间 {{ r.roomNo || '未分配' }}<br />
            房型：{{ r.typeName }} ｜ {{ r.roomCount }} 间 × {{ r.nights }} 晚<br />
            房费 ￥{{ yuan(r.totalCents) }} ｜ 账单应收 ￥{{ yuan(r.billTotalCents) }}<br />
            离店日期：{{ r.checkOutDate }}
          </div>
          <div style="margin-top:8px">
            <el-button size="small" type="success" @click="checkout(r)">办理退房</el-button>
            <el-button size="small" @click="openBill(r)">去结算</el-button>
          </div>
        </div>
        <div v-if="!departures.length" class="empty">今日没有应离店的订单</div>
      </div>
    </div>

    <!-- 办理入住：分配房间 -->
    <el-dialog v-model="checkinVisible" title="办理入住" width="620px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="预订单号">{{ current.reservationNo }}</el-descriptions-item>
        <el-descriptions-item label="客人">{{ current.guestName }}</el-descriptions-item>
        <el-descriptions-item label="房型">{{ current.typeName }}</el-descriptions-item>
        <el-descriptions-item label="间夜">
          {{ current.roomCount }} 间 × {{ current.nights }} 晚
        </el-descriptions-item>
        <el-descriptions-item label="入住日期">{{ current.checkInDate }}</el-descriptions-item>
        <el-descriptions-item label="离店日期">{{ current.checkOutDate }}</el-descriptions-item>
        <el-descriptions-item label="房费总额">￥{{ yuan(current.totalCents) }}</el-descriptions-item>
        <el-descriptions-item label="应收押金">
          <span class="price">￥{{ yuan(current.depositCents) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
                title="押金优先从客人预存余额扣除，余额不足时由前台线下收取（系统会记录押金流水）。" />
      <el-form label-width="100px">
        <el-form-item label="分配房间">
          <el-select v-model="checkinForm.roomId" placeholder="请选择要分配的房间" style="width:100%"
                     :loading="roomLoading">
            <el-option v-for="r in availableRooms" :key="r.id"
                       :label="`${r.roomNo}（${r.typeName}，${r.floorNo} 楼，￥${yuan(r.priceCents)}/晚）`" :value="r.id" />
          </el-select>
          <div class="muted" style="margin-top:6px">
            共 {{ availableRooms.length }} 间可分配；列表已按日期区间过滤掉被占用与维修的房间。
          </div>
        </el-form-item>
        <el-form-item label="核验证件号">
          <el-input v-model="checkinForm.idCard" maxlength="18" placeholder="录入客人证件号（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCheckin">确认办理入住</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  channelText,
  frontApi,
  reservationApi,
  reservationStatusTag,
  reservationStatusText,
  today,
  yuan
} from '@/api'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const roomLoading = ref(false)
const board = ref({})
const arrivals = ref([])
const inHouse = ref([])
const departures = ref([])
const todayText = today()

const checkinVisible = ref(false)
const current = ref({})
const availableRooms = ref([])
const checkinForm = reactive({ roomId: null, idCard: '' })

/** 看板接口可能返回数组或 {records:[]} 两种结构，这里统一成数组 */
function toArray(v) {
  if (Array.isArray(v)) return v
  if (v && Array.isArray(v.records)) return v.records
  return []
}

function isDepartureToday(r) {
  return r.checkOutDate === (board.value.date || todayText)
}

async function load() {
  loading.value = true
  try {
    const res = await frontApi.board()
    const data = res.data || {}
    board.value = data
    arrivals.value = toArray(data.arrivals)
    inHouse.value = toArray(data.inHouse)
    departures.value = toArray(data.departures)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function openCheckin(row) {
  current.value = row
  checkinForm.roomId = row.roomId || null
  checkinForm.idCard = ''
  checkinVisible.value = true
  loadAvailableRooms(row)
}

async function loadAvailableRooms(row) {
  roomLoading.value = true
  try {
    const res = await frontApi.availableRooms({
      typeId: row.typeId,
      checkIn: row.checkInDate,
      checkOut: row.checkOutDate
    })
    availableRooms.value = res.data || []
    // 默认选中预订单上已指定的房间
    if (!checkinForm.roomId && availableRooms.value.length) {
      checkinForm.roomId = row.roomId || availableRooms.value[0].id
    }
  } catch (e) {
    availableRooms.value = []
  } finally {
    roomLoading.value = false
  }
}

async function submitCheckin() {
  if (!checkinForm.roomId) {
    ElMessage.warning('请选择要分配的房间')
    return
  }
  submitting.value = true
  try {
    const res = await frontApi.checkin(current.value.id, {
      roomId: checkinForm.roomId,
      idCard: checkinForm.idCard || undefined
    })
    const data = res.data || {}
    ElMessage.success(
      `入住办理完成：房间 ${data.roomNo || ''}，押金 ￥${yuan(data.depositCents)}` +
        (data.paidByBalance === false ? '（预存余额不足，线下收取）' : '（已从预存余额扣除）')
    )
    checkinVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function checkout(row) {
  try {
    await ElMessageBox.confirm(
      `确认为 ${row.guestName}（房间 ${row.roomNo || '-'}）办理退房？\n` +
        '系统将按实际住宿晚数重算房费，并把房间置为待打扫，随后请到账单页完成结算。',
      '办理退房',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await frontApi.checkout(row.id)
    const data = res.data || {}
    ElMessage.success(
      `退房完成：实际 ${data.stayNights} 晚，房费 ￥${yuan(data.roomChargeCents)}，` +
        `账单 ${data.billNo} 待结算 ￥${yuan(data.payableCents)}`
    )
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

function openBill(row) {
  router.push({ path: '/admin/bills', query: { reservationId: row.id } })
}

async function cancelReservation(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `确认取消预订单 ${row.reservationNo}（${row.guestName}）？请填写取消原因：`,
      '取消预订',
      {
        type: 'warning',
        inputPlaceholder: '如：客人电话取消 / 行程变更',
        inputValidator: (v) => (v && v.trim() ? true : '请填写取消原因'),
        confirmButtonText: '确认取消',
        cancelButtonText: '返回'
      }
    )
    reason = res.value
  } catch (e) {
    return
  }
  try {
    await frontApi.cancelReservation(row.id, { reason })
    ElMessage.success('预订已取消，房间已释放')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function noShow(row) {
  try {
    await ElMessageBox.confirm(
      `确认将预订单 ${row.reservationNo}（${row.guestName}）标记为「未到店」？房间将被释放。`,
      '标记未到店',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    // 未到店标记走预订管理接口
    await reservationApi.noShow(row.id)
    ElMessage.success('已标记未到店，房间已释放')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(load)
</script>
