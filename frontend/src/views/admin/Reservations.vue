<template>
  <div>
    <div class="page-title">预订管理</div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="预订单号 / 客人姓名 / 电话 / 房间号"
                  style="width:260px" clearable @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px" @change="search">
          <el-option label="已预订" value="BOOKED" />
          <el-option label="已入住" value="CHECKED_IN" />
          <el-option label="已退房" value="CHECKED_OUT" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="未到店" value="NO_SHOW" />
        </el-select>
        <el-date-picker v-model="query.date" type="date" value-format="YYYY-MM-DD"
                        placeholder="按在住日期筛选" clearable style="width:180px" @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button type="success" @click="openCreate">新建预订</el-button>
        <el-button style="margin-left:auto" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="reservationNo" label="预订单号" width="170" />
        <el-table-column label="客人" width="140">
          <template #default="{ row }">
            {{ row.guestName }}<br />
            <span class="muted">{{ row.guestPhone }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="typeName" label="房型" width="120" />
        <el-table-column label="房间" width="90">
          <template #default="{ row }">{{ row.roomNo || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="入住 → 离店" width="200">
          <template #default="{ row }">{{ row.checkInDate }} → {{ row.checkOutDate }}</template>
        </el-table-column>
        <el-table-column label="间夜 / 人数" width="120">
          <template #default="{ row }">{{ row.roomCount }} 间 × {{ row.nights }} 晚 / {{ row.guestCount }} 人</template>
        </el-table-column>
        <el-table-column label="单价 / 总额" width="150">
          <template #default="{ row }">
            ￥{{ yuan(row.unitPriceCents) }}<br />
            <span class="price">￥{{ yuan(row.totalCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="押金" width="110">
          <template #default="{ row }">￥{{ yuan(row.depositCents) }}</template>
        </el-table-column>
        <el-table-column label="账单应收" width="120">
          <template #default="{ row }">￥{{ yuan(row.billTotalCents) }}</template>
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
        <el-table-column prop="specialRequest" label="特殊要求" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'BOOKED'" size="small" type="primary" @click="openCheckin(row)">
              办理入住
            </el-button>
            <el-button v-if="row.status === 'CHECKED_IN'" size="small" type="success" @click="checkout(row)">
              办理退房
            </el-button>
            <el-button v-if="row.status === 'BOOKED'" size="small" type="danger" @click="cancel(row)">取消</el-button>
            <el-button v-if="row.status === 'BOOKED'" size="small" @click="noShow(row)">未到店</el-button>
            <el-button size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:12px" layout="total, prev, pager, next, sizes"
                     :total="total" :page-size="size" :current-page="page"
                     :page-sizes="[10, 20, 50]" @current-change="onPage" @size-change="onSizeChange" />
      <div v-if="!records.length && !loading" class="empty">没有符合条件的预订单</div>
    </div>

    <!-- 新建预订 -->
    <el-dialog v-model="createVisible" title="新建预订" width="720px" top="6vh">
      <el-form label-width="100px">
        <el-form-item label="选择客人">
          <div style="display:flex;gap:8px;width:100%">
            <el-select v-model="form.guestId" filterable remote reserve-keyword placeholder="输入姓名/手机号/客人编号搜索"
                       :remote-method="searchGuests" :loading="guestLoading" style="flex:1">
              <el-option v-for="g in guestOptions" :key="g.id"
                         :label="`${g.realName}（${g.guestNo}，${g.phone}，${guestLevelText(g.memberLevel)}）`" :value="g.id" />
            </el-select>
            <el-button @click="openQuickGuest">登记新客人</el-button>
          </div>
          <div v-if="selectedGuest" class="muted" style="margin-top:6px;line-height:1.8">
            客人编号 {{ selectedGuest.guestNo }} ｜ 等级 {{ guestLevelText(selectedGuest.memberLevel) }}
            ｜ 预存余额 ￥{{ yuan(selectedGuest.balanceCents) }}
            ｜ 累计入住 {{ selectedGuest.totalStayCount ?? 0 }} 次
            <el-tag v-if="selectedGuest.status === 'BLACKLIST'" size="small" type="danger" style="margin-left:6px">
              黑名单，无法预订
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="房型">
          <el-select v-model="form.typeId" placeholder="请选择房型" style="width:100%" @change="onTypeChange">
            <el-option v-for="t in types" :key="t.id"
                       :label="`${t.typeName}（￥${yuan(t.priceCents)}/晚，可住 ${t.capacity} 人，${t.status === 'ON_SALE' ? '在售' : '停售'}）`"
                       :value="t.id" :disabled="t.status !== 'ON_SALE'" />
          </el-select>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="入住日期">
              <el-date-picker v-model="form.checkInDate" type="date" value-format="YYYY-MM-DD"
                              :disabled-date="disablePast" style="width:100%" @change="onDateChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离店日期">
              <el-date-picker v-model="form.checkOutDate" type="date" value-format="YYYY-MM-DD"
                              :disabled-date="disablePast" style="width:100%" @change="onDateChange" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="房间数">
              <el-input-number v-model="form.roomCount" :min="1" :max="10" style="width:100%"
                               @change="onDateChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入住人数">
              <el-input-number v-model="form.guestCount" :min="1" :max="20" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预订渠道">
              <el-select v-model="form.channel" style="width:100%">
                <el-option label="前台" value="FRONT_DESK" />
                <el-option label="自助/APP" value="APP" />
                <el-option label="电话" value="PHONE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="指定房间">
          <el-select v-model="form.roomId" placeholder="可不指定，入住时再分配" clearable style="width:100%"
                     :loading="roomLoading">
            <el-option v-for="r in availableRooms" :key="r.id"
                       :label="`${r.roomNo}（${r.floorNo} 楼）`" :value="r.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="特殊要求">
          <el-input v-model="form.specialRequest" type="textarea" :rows="2"
                    placeholder="如：无烟房、高楼层、需要加床" />
        </el-form-item>

        <el-form-item label="房价试算">
          <el-button size="small" type="primary" :loading="quoting" @click="doQuote">试算价格</el-button>
          <span class="muted" style="margin-left:8px">试算结果会随预订一起提交，价格由后端最终确定</span>
        </el-form-item>
      </el-form>

      <div v-if="quote" class="quote-sheet" style="margin:0 0 12px 100px">
        <div class="quote-row">
          <span>{{ quote.typeName }}（{{ quote.nights }} 晚 × {{ quote.roomCount }} 间）</span>
          <span class="amount-big">￥{{ yuan(quote.totalCents) }}</span>
        </div>
        <div class="quote-row">
          <span>单价 ￥{{ yuan(quote.unitPriceCents) }} / 晚（门市价 ￥{{ yuan(quote.originUnitPriceCents) }}）</span>
          <span>押金 ￥{{ yuan(quote.depositCents) }}</span>
        </div>
        <div class="quote-row">
          <span>价格来源</span>
          <span>{{ quote.priceSource }}<span class="muted" style="margin-left:6px">（{{ discountText(quote.discountRate) }}）</span></span>
        </div>
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建预订</el-button>
      </template>
    </el-dialog>

    <!-- 快速登记客人 -->
    <el-dialog v-model="guestVisible" title="登记新客人" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="guestForm.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="guestForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="guestForm.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="guestForm.idCard" maxlength="18" />
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="guestForm.address" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="guestForm.remark" placeholder="如：过敏史、偏好高楼层" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="guestVisible = false">取消</el-button>
        <el-button type="primary" :loading="guestSaving" @click="saveQuickGuest">登记并选中</el-button>
      </template>
    </el-dialog>

    <!-- 办理入住 -->
    <el-dialog v-model="checkinVisible" title="办理入住" width="620px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="预订单号">{{ current.reservationNo }}</el-descriptions-item>
        <el-descriptions-item label="客人">{{ current.guestName }}</el-descriptions-item>
        <el-descriptions-item label="房型">{{ current.typeName }}</el-descriptions-item>
        <el-descriptions-item label="间夜">{{ current.roomCount }} 间 × {{ current.nights }} 晚</el-descriptions-item>
        <el-descriptions-item label="入住日期">{{ current.checkInDate }}</el-descriptions-item>
        <el-descriptions-item label="离店日期">{{ current.checkOutDate }}</el-descriptions-item>
        <el-descriptions-item label="房费总额">￥{{ yuan(current.totalCents) }}</el-descriptions-item>
        <el-descriptions-item label="应收押金">
          <span class="price">￥{{ yuan(current.depositCents) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
                title="押金优先从客人预存余额扣除；余额不足时由前台线下收取并记录押金流水。" />
      <el-form label-width="100px">
        <el-form-item label="分配房间">
          <el-select v-model="checkinForm.roomId" placeholder="请选择房间" style="width:100%" :loading="roomLoading">
            <el-option v-for="r in availableRooms" :key="r.id"
                       :label="`${r.roomNo}（${r.typeName}，${r.floorNo} 楼，￥${yuan(r.priceCents)}/晚）`" :value="r.id" />
          </el-select>
          <div class="muted" style="margin-top:6px">共 {{ availableRooms.length }} 间可分配</div>
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

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="预订单详情" width="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="预订单号">{{ detail.reservationNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="reservationStatusTag(detail.status)">
            {{ reservationStatusText(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客人">{{ detail.guestName }}（{{ detail.guestPhone }}）</el-descriptions-item>
        <el-descriptions-item label="房型 / 房间">{{ detail.typeName }} / {{ detail.roomNo || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="入住日期">{{ detail.checkInDate }}</el-descriptions-item>
        <el-descriptions-item label="离店日期">{{ detail.checkOutDate }}</el-descriptions-item>
        <el-descriptions-item label="间夜 / 人数">
          {{ detail.roomCount }} 间 × {{ detail.nights }} 晚 / {{ detail.guestCount }} 人
        </el-descriptions-item>
        <el-descriptions-item label="单价">￥{{ yuan(detail.unitPriceCents) }}</el-descriptions-item>
        <el-descriptions-item label="房费总额">￥{{ yuan(detail.totalCents) }}</el-descriptions-item>
        <el-descriptions-item label="押金">￥{{ yuan(detail.depositCents) }}</el-descriptions-item>
        <el-descriptions-item label="渠道">{{ channelText(detail.channel) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入住时间">{{ detail.checkInAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退房时间">{{ detail.checkOutAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="特殊要求" :span="2">{{ detail.specialRequest || '无' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因" :span="2">{{ detail.cancelReason || '无' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addDays,
  channelText,
  discountText,
  frontApi,
  guestLevelText,
  reservationApi,
  reservationStatusTag,
  reservationStatusText,
  roomApi,
  today,
  yuan
} from '@/api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const submitting = ref(false)
const quoting = ref(false)
const roomLoading = ref(false)
const guestLoading = ref(false)

const query = reactive({ keyword: '', status: '', date: null })

const types = ref([])
const guestOptions = ref([])
const availableRooms = ref([])
const quote = ref(null)

const createVisible = ref(false)
const guestVisible = ref(false)
const checkinVisible = ref(false)
const detailVisible = ref(false)
const guestSaving = ref(false)

const current = ref({})
const detail = ref({})

const form = reactive({
  guestId: null,
  typeId: null,
  roomId: null,
  checkInDate: today(),
  checkOutDate: addDays(today(), 1),
  roomCount: 1,
  guestCount: 1,
  channel: 'FRONT_DESK',
  specialRequest: ''
})

const checkinForm = reactive({ roomId: null, idCard: '' })
const guestForm = reactive({
  realName: '', phone: '', gender: '男', idCard: '', address: '', remark: ''
})

const selectedGuest = computed(() => guestOptions.value.find((g) => g.id === form.guestId) || null)

function disablePast(date) {
  return date.getTime() < new Date(today()).getTime()
}

async function load() {
  loading.value = true
  try {
    const res = await reservationApi.list({
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      date: query.date || undefined,
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

async function loadTypes() {
  try {
    const res = await roomApi.types()
    types.value = res.data || []
  } catch (e) {
    types.value = []
  }
}

function openCreate() {
  Object.assign(form, {
    guestId: null,
    typeId: types.value.find((t) => t.status === 'ON_SALE')?.id || null,
    roomId: null,
    checkInDate: today(),
    checkOutDate: addDays(today(), 1),
    roomCount: 1,
    guestCount: 1,
    channel: 'FRONT_DESK',
    specialRequest: ''
  })
  quote.value = null
  availableRooms.value = []
  guestOptions.value = []
  createVisible.value = true
}

/** 客人检索：远程搜索，按姓名/手机号/客人编号 */
async function searchGuests(keyword) {
  if (!keyword) return
  guestLoading.value = true
  try {
    const res = await frontApi.guests({ keyword })
    const data = res.data
    guestOptions.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    guestOptions.value = []
  } finally {
    guestLoading.value = false
  }
}

function openQuickGuest() {
  Object.assign(guestForm, { realName: '', phone: '', gender: '男', idCard: '', address: '', remark: '' })
  guestVisible.value = true
}

async function saveQuickGuest() {
  if (!guestForm.realName || !guestForm.phone) {
    ElMessage.warning('请填写姓名与手机号')
    return
  }
  guestSaving.value = true
  try {
    const res = await frontApi.createGuest({ ...guestForm })
    const created = res.data || {}
    ElMessage.success('客人登记成功')
    guestVisible.value = false
    // 登记成功后把新客人放入下拉列表并自动选中
    const newGuest = created.id ? created : { ...guestForm, id: created.guestId, guestNo: created.guestNo }
    if (newGuest.id) {
      guestOptions.value = [newGuest, ...guestOptions.value]
      form.guestId = newGuest.id
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    guestSaving.value = false
  }
}

function onTypeChange() {
  form.roomId = null
  quote.value = null
  loadAvailableRooms()
}

function onDateChange() {
  form.roomId = null
  quote.value = null
  loadAvailableRooms()
}

async function loadAvailableRooms() {
  if (!form.typeId || !form.checkInDate || !form.checkOutDate) return
  if (form.checkOutDate <= form.checkInDate) return
  roomLoading.value = true
  try {
    const res = await frontApi.availableRooms({
      typeId: form.typeId,
      checkIn: form.checkInDate,
      checkOut: form.checkOutDate
    })
    availableRooms.value = res.data || []
  } catch (e) {
    availableRooms.value = []
  } finally {
    roomLoading.value = false
  }
}

async function doQuote() {
  if (!form.typeId) {
    ElMessage.warning('请先选择房型')
    return
  }
  if (!form.checkInDate || !form.checkOutDate) {
    ElMessage.warning('请选择入住与离店日期')
    return
  }
  if (form.checkOutDate <= form.checkInDate) {
    ElMessage.warning('离店日期必须晚于入住日期')
    return
  }
  quoting.value = true
  try {
    const res = await frontApi.quote({
      typeId: form.typeId,
      checkIn: form.checkInDate,
      checkOut: form.checkOutDate,
      roomCount: form.roomCount,
      guestId: form.guestId || undefined
    })
    quote.value = res.data || null
  } catch (e) {
    quote.value = null
  } finally {
    quoting.value = false
  }
}

async function submitCreate() {
  if (!form.guestId) {
    ElMessage.warning('请选择客人（可先登记新客人）')
    return
  }
  if (!form.typeId) {
    ElMessage.warning('请选择房型')
    return
  }
  if (!form.checkInDate || !form.checkOutDate || form.checkOutDate <= form.checkInDate) {
    ElMessage.warning('请选择正确的入住与离店日期')
    return
  }
  submitting.value = true
  try {
    const res = await frontApi.createReservation({
      guestId: form.guestId,
      typeId: form.typeId,
      roomId: form.roomId || undefined,
      checkInDate: form.checkInDate,
      checkOutDate: form.checkOutDate,
      roomCount: form.roomCount,
      guestCount: form.guestCount,
      channel: form.channel,
      specialRequest: form.specialRequest || undefined
    })
    const data = res.data || {}
    ElMessage.success(
      `预订成功：${data.reservationNo}，房费 ￥${yuan(data.totalCents)}，押金 ￥${yuan(data.depositCents)}`
    )
    createVisible.value = false
    search()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function openCheckin(row) {
  current.value = row
  checkinForm.roomId = row.roomId || null
  checkinForm.idCard = ''
  checkinVisible.value = true
  loadAvailableForCheckin(row)
}

async function loadAvailableForCheckin(row) {
  roomLoading.value = true
  try {
    const res = await frontApi.availableRooms({
      typeId: row.typeId,
      checkIn: row.checkInDate,
      checkOut: row.checkOutDate
    })
    availableRooms.value = res.data || []
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
    ElMessage.success(`入住办理完成：房间 ${data.roomNo || ''}，押金 ￥${yuan(data.depositCents)}`)
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
      `确认为 ${row.guestName}（房间 ${row.roomNo || '-'}）办理退房？房费将按实际住宿晚数重算，房间置为待打扫。`,
      '办理退房',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await frontApi.checkout(row.id)
    const data = res.data || {}
    ElMessage.success(`退房完成：实际 ${data.stayNights} 晚，账单 ${data.billNo} 待结算 ￥${yuan(data.payableCents)}`)
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function cancel(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `确认取消预订单 ${row.reservationNo}？请填写取消原因：`,
      '取消预订',
      {
        type: 'warning',
        inputPlaceholder: '如：客人电话取消 / 行程变更',
        inputValidator: (v) => (v && v.trim() ? true : '请填写取消原因')
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
      `确认将预订单 ${row.reservationNo} 标记为「未到店」？房间将被释放。`,
      '标记未到店',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await reservationApi.noShow(row.id)
    ElMessage.success('已标记未到店')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function showDetail(row) {
  try {
    const res = await reservationApi.detail(row.id)
    detail.value = res.data || row
  } catch (e) {
    detail.value = row
  }
  detailVisible.value = true
}

onMounted(() => {
  load()
  loadTypes()
})
</script>
