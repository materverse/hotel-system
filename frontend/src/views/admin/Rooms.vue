<template>
  <div>
    <div class="page-title">客房管理</div>

    <div class="card">
      <el-tabs v-model="tab">
        <!-- ---------------- 房态图 ---------------- -->
        <el-tab-pane label="房态图" name="board">
          <div class="toolbar">
            <el-select v-model="filter.typeId" placeholder="全部房型" clearable style="width:160px" @change="loadRooms">
              <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
            </el-select>
            <el-select v-model="filter.floorNo" placeholder="全部楼层" clearable style="width:130px" @change="loadRooms">
              <el-option v-for="f in floorOptions" :key="f" :label="f + ' 楼'" :value="f" />
            </el-select>
            <el-input v-model="filter.keyword" placeholder="房间号 / 房型" style="width:180px" clearable
                      @keyup.enter="loadRooms" @clear="loadRooms" />
            <el-select v-model="filter.status" placeholder="全部房态" clearable style="width:140px" @change="loadRooms">
              <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-button type="primary" @click="loadRooms">查询</el-button>
            <el-button type="success" @click="openBatchAdd">批量新增房间</el-button>
          </div>

          <div class="toolbar">
            <span class="muted">图例：</span>
            <el-tag v-for="s in statusOptions" :key="s.value" size="small" :type="s.tag">{{ s.label }}</el-tag>
            <span class="muted" style="margin-left:auto">
              共 {{ rooms.length }} 间房：空闲 {{ countOf('FREE') }} ｜ 在住 {{ countOf('OCCUPIED') }} ｜
              待打扫 {{ countOf('CLEANING') }} ｜ 维修 {{ countOf('MAINTENANCE') }} ｜ 已预订 {{ countOf('BOOKED') }}
            </span>
          </div>

          <div class="room-board" v-loading="roomLoading">
            <div v-for="group in floors" :key="group.floor" class="floor-row">
              <div class="floor-name">{{ group.floor }} 楼（{{ group.rooms.length }} 间）</div>
              <div class="room-grid">
                <div v-for="r in group.rooms" :key="r.id" class="room-cell" :class="r.status" @click="openStatusDialog(r)">
                  <div class="no">{{ r.roomNo }}</div>
                  <div class="meta">
                    {{ r.typeName }}<br />
                    {{ roomStatusText(r.status) }}
                    <template v-if="r.cleanStatus === 'DIRTY'"><br />待清洁</template>
                    <template v-if="r.guestName"><br />{{ r.guestName }}</template>
                    <template v-if="r.currentCheckOutDate"><br />{{ r.currentCheckOutDate }} 离店</template>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!floors.length && !roomLoading" class="empty">暂无客房数据，可用「批量新增房间」快速建楼</div>
          </div>
        </el-tab-pane>

        <!-- ---------------- 房型管理 ---------------- -->
        <el-tab-pane label="房型管理" name="types">
          <div class="toolbar">
            <el-button type="success" @click="openTypeForm()">新增房型</el-button>
            <el-button style="margin-left:auto" @click="loadTypes">刷新</el-button>
          </div>
          <el-table :data="types" v-loading="typeLoading" size="small">
            <el-table-column prop="typeName" label="房型名称" min-width="140" />
            <el-table-column prop="bedType" label="床型" width="90" />
            <el-table-column prop="areaSqm" label="面积(㎡)" width="100" />
            <el-table-column prop="capacity" label="可住人数" width="100" />
            <el-table-column label="门市价" width="120">
              <template #default="{ row }"><span class="price">￥{{ yuan(row.priceCents) }}</span></template>
            </el-table-column>
            <el-table-column label="会员价" width="120">
              <template #default="{ row }">￥{{ yuan(row.memberPriceCents) }}</template>
            </el-table-column>
            <el-table-column label="房间数" width="90">
              <template #default="{ row }">{{ roomCountOfType(row.id) }}</template>
            </el-table-column>
            <el-table-column prop="facilities" label="配套设施" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ON_SALE' ? 'success' : 'info'">
                  {{ row.status === 'ON_SALE' ? '在售' : '停售' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openTypeForm(row)">编辑</el-button>
                <el-button size="small" :type="row.status === 'ON_SALE' ? 'warning' : 'success'"
                           @click="toggleTypeStatus(row)">
                  {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ---------------- 房价策略 ---------------- -->
        <el-tab-pane label="房价策略" name="rates">
          <div class="toolbar">
            <el-select v-model="rateFilter.typeId" placeholder="全部房型" clearable style="width:180px" @change="loadRates">
              <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
            </el-select>
            <el-select v-model="rateFilter.status" placeholder="全部状态" clearable style="width:140px" @change="loadRates">
              <el-option label="生效中" value="ACTIVE" />
              <el-option label="已停用" value="DISABLED" />
            </el-select>
            <el-button type="success" @click="openRateForm()">新增策略</el-button>
            <el-button style="margin-left:auto" @click="loadRates">刷新</el-button>
          </div>
          <el-table :data="rates" v-loading="rateLoading" size="small">
            <el-table-column prop="typeName" label="房型" width="150" />
            <el-table-column prop="planName" label="策略名称" min-width="160" />
            <el-table-column prop="startDate" label="生效开始" width="130" />
            <el-table-column prop="endDate" label="生效结束" width="130" />
            <el-table-column label="策略价" width="130">
              <template #default="{ row }"><span class="price">￥{{ yuan(row.priceCents) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? '生效中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openRateForm(row)">编辑</el-button>
                <el-button size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
                           @click="toggleRateStatus(row)">
                  {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="muted" style="margin-top:10px;line-height:1.9">
            说明：某日期存在生效的房价策略时，房价试算按策略价执行且不再叠加会员折扣；无策略时按会员等级折扣使用会员价/门市价。
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 单个改房态 -->
    <el-dialog v-model="statusVisible" :title="`修改房态：${current.roomNo || ''}`" width="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="房间号">{{ current.roomNo }}</el-descriptions-item>
        <el-descriptions-item label="楼层">{{ current.floorNo }} 楼</el-descriptions-item>
        <el-descriptions-item label="房型">{{ current.typeName }}</el-descriptions-item>
        <el-descriptions-item label="门市价">￥{{ yuan(current.priceCents) }}</el-descriptions-item>
        <el-descriptions-item label="当前房态">
          <el-tag size="small" :type="roomStatusTag(current.status)">{{ roomStatusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="清洁状态">
          {{ current.cleanStatus === 'DIRTY' ? '待清洁' : '已清洁' }}
        </el-descriptions-item>
        <el-descriptions-item label="在住客人" :span="2">{{ current.guestName || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="修改为">
          <el-radio-group v-model="statusForm.status">
            <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="statusForm.remark" placeholder="如：空调故障待维修 / 马桶漏水" />
        </el-form-item>
      </el-form>
      <el-alert v-if="current.status === 'OCCUPIED'" type="warning" :closable="false"
                title="该房间有客人在住，改为空闲前请确认客人已办理退房。" />
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button v-if="current.status === 'CLEANING' || current.cleanStatus === 'DIRTY'" type="success"
                   :loading="submitting" @click="doClean">打扫完成（置为空闲）</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStatus">保存房态</el-button>
      </template>
    </el-dialog>

    <!-- 批量新增房间 -->
    <el-dialog v-model="batchVisible" title="批量新增房间" width="600px">
      <el-form label-width="110px">
        <el-form-item label="房型">
          <el-select v-model="batchForm.typeId" placeholder="请选择房型" style="width:100%">
            <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-input-number v-model="batchForm.floorNo" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="房间号前缀">
          <el-input v-model="batchForm.prefix" placeholder="如 88（自动拼接序号）" style="width:200px" />
        </el-form-item>
        <el-form-item label="起始序号">
          <el-input-number v-model="batchForm.start" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="新增数量">
          <el-input-number v-model="batchForm.count" :min="1" :max="50" />
          <span class="muted" style="margin-left:8px">最多一次 50 间</span>
        </el-form-item>
        <el-form-item label="朝向">
          <el-select v-model="batchForm.orientation" clearable style="width:160px">
            <el-option label="朝南" value="朝南" />
            <el-option label="朝北" value="朝北" />
            <el-option label="朝东" value="朝东" />
            <el-option label="朝西" value="朝西" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始房态">
          <el-radio-group v-model="batchForm.status">
            <el-radio-button value="FREE">空闲</el-radio-button>
            <el-radio-button value="MAINTENANCE">维修</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false"
                :title="`将创建：${previewNos.join('、') || '（请填写前缀与数量）'}`" />
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBatch">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- 房型表单 -->
    <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑房型' : '新增房型'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="房型名称">
          <el-input v-model="typeForm.typeName" placeholder="如：标准大床房 / 家庭房 / 行政套房" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="床型">
              <el-select v-model="typeForm.bedType" style="width:100%">
                <el-option label="大床" value="大床" />
                <el-option label="双床" value="双床" />
                <el-option label="套房" value="套房" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(㎡)">
              <el-input-number v-model="typeForm.areaSqm" :min="10" :max="300" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="可住人数">
              <el-input-number v-model="typeForm.capacity" :min="1" :max="10" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门市价(元)">
              <el-input-number v-model="typePriceYuan" :min="0" :max="100000" :step="50" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="会员价(元)">
          <el-input-number v-model="typeMemberPriceYuan" :min="0" :max="100000" :step="50" />
          <span class="muted" style="margin-left:8px">留 0 表示按门市价与等级折扣计算</span>
        </el-form-item>
        <el-form-item label="配套设施">
          <el-input v-model="typeForm.facilities" placeholder="逗号分隔，如：免费WiFi,空调,液晶电视,独立卫浴" />
        </el-form-item>
        <el-form-item label="房型说明">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio-button value="ON_SALE">在售</el-radio-button>
            <el-radio-button value="OFF_SALE">停售</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 房价策略表单 -->
    <el-dialog v-model="rateVisible" :title="rateForm.id ? '编辑房价策略' : '新增房价策略'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="房型">
          <el-select v-model="rateForm.typeId" placeholder="请选择房型" style="width:100%">
            <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略名称">
          <el-input v-model="rateForm.planName" placeholder="如：周末价 / 国庆涨价 / 淡季促销" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生效开始">
              <el-date-picker v-model="rateForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效结束">
              <el-date-picker v-model="rateForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="策略价(元/晚)">
          <el-input-number v-model="ratePriceYuan" :min="0" :max="100000" :step="50" style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="rateForm.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  roomApi,
  roomStatusTag,
  roomStatusText,
  today,
  toCents,
  yuan
} from '@/api'

const tab = ref('board')
const rooms = ref([])
const types = ref([])
const rates = ref([])
const roomLoading = ref(false)
const typeLoading = ref(false)
const rateLoading = ref(false)
const submitting = ref(false)

const filter = reactive({ typeId: null, floorNo: null, keyword: '', status: '' })
const rateFilter = reactive({ typeId: null, status: '' })

const statusOptions = [
  { value: 'FREE', label: '空闲', tag: 'success' },
  { value: 'OCCUPIED', label: '已入住', tag: 'primary' },
  { value: 'CLEANING', label: '待打扫', tag: 'warning' },
  { value: 'MAINTENANCE', label: '维修', tag: 'info' },
  { value: 'BOOKED', label: '已预订', tag: 'danger' }
]

const statusVisible = ref(false)
const batchVisible = ref(false)
const typeVisible = ref(false)
const rateVisible = ref(false)
const current = ref({})

const statusForm = reactive({ status: 'FREE', remark: '' })

const batchForm = reactive({
  typeId: null,
  floorNo: 1,
  prefix: '',
  start: 1,
  count: 4,
  orientation: '',
  status: 'FREE'
})

const typeForm = reactive({
  id: null, typeName: '', bedType: '大床', areaSqm: 25, capacity: 2,
  priceCents: 0, memberPriceCents: 0, facilities: '', description: '', status: 'ON_SALE'
})
const typePriceYuan = ref(0)
const typeMemberPriceYuan = ref(0)

const rateForm = reactive({
  id: null, typeId: null, planName: '', startDate: today(),
  endDate: today(), priceCents: 0, status: 'ACTIVE'
})
const ratePriceYuan = ref(0)

/** 楼层下拉选项：由现有房间的楼层去重得出 */
const floorOptions = computed(() => [...new Set(rooms.value.map((r) => r.floorNo))].sort((a, b) => a - b))

/** 按楼层分组的房态图 */
const floors = computed(() => {
  const map = new Map()
  for (const r of rooms.value) {
    const floor = r.floorNo ?? 0
    if (!map.has(floor)) map.set(floor, [])
    map.get(floor).push(r)
  }
  return [...map.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([floor, list]) => ({ floor, rooms: list }))
})

/** 批量新增房间号的预览 */
const previewNos = computed(() => {
  const list = []
  const prefix = batchForm.prefix || ''
  for (let i = 0; i < Math.min(batchForm.count, 50); i++) {
    list.push(prefix + String(batchForm.start + i).padStart(2, '0'))
  }
  return list
})

function countOf(status) {
  return rooms.value.filter((r) => r.status === status).length
}

function roomCountOfType(typeId) {
  return rooms.value.filter((r) => r.typeId === typeId).length
}

async function loadRooms() {
  roomLoading.value = true
  try {
    const res = await roomApi.list({
      status: filter.status || undefined,
      typeId: filter.typeId || undefined,
      floorNo: filter.floorNo || undefined,
      keyword: filter.keyword || undefined
    })
    const data = res.data
    rooms.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    roomLoading.value = false
  }
}

async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await roomApi.types()
    const data = res.data
    types.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    typeLoading.value = false
  }
}

async function loadRates() {
  rateLoading.value = true
  try {
    const res = await roomApi.ratePlans({
      typeId: rateFilter.typeId || undefined,
      status: rateFilter.status || undefined
    })
    const data = res.data
    rates.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    rateLoading.value = false
  }
}

function openStatusDialog(row) {
  current.value = row
  statusForm.status = row.status
  statusForm.remark = row.remark || ''
  statusVisible.value = true
}

async function submitStatus() {
  submitting.value = true
  try {
    await roomApi.changeStatus(current.value.id, { status: statusForm.status, remark: statusForm.remark })
    ElMessage.success(`房间 ${current.value.roomNo} 房态已更新为「${roomStatusText(statusForm.status)}」`)
    statusVisible.value = false
    loadRooms()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doClean() {
  submitting.value = true
  try {
    await roomApi.clean(current.value.id)
    ElMessage.success(`房间 ${current.value.roomNo} 打扫完成，已置为空闲`)
    statusVisible.value = false
    loadRooms()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function openBatchAdd() {
  Object.assign(batchForm, {
    typeId: types.value[0]?.id || null,
    floorNo: 1,
    prefix: '',
    start: 1,
    count: 4,
    orientation: '',
    status: 'FREE'
  })
  batchVisible.value = true
}

async function submitBatch() {
  if (!batchForm.typeId) {
    ElMessage.warning('请选择房型')
    return
  }
  if (!batchForm.prefix) {
    ElMessage.warning('请填写房间号前缀')
    return
  }
  submitting.value = true
  let success = 0
  const failures = []
  // 逐个创建：某间房号重复时不影响其余房间，最后汇总提示
  for (const roomNo of previewNos.value) {
    try {
      await roomApi.save({
        roomNo,
        floorNo: batchForm.floorNo,
        typeId: batchForm.typeId,
        orientation: batchForm.orientation || undefined,
        status: batchForm.status
      })
      success++
    } catch (e) {
      failures.push(roomNo)
    }
  }
  submitting.value = false
  if (success) {
    ElMessage.success(`成功新增 ${success} 间房`)
  }
  if (failures.length) {
    ElMessage.warning(`以下房间号创建失败（可能已存在）：${failures.join('、')}`)
  }
  batchVisible.value = false
  loadRooms()
}

function openTypeForm(row) {
  if (row) {
    Object.assign(typeForm, {
      id: row.id,
      typeName: row.typeName,
      bedType: row.bedType,
      areaSqm: row.areaSqm,
      capacity: row.capacity,
      priceCents: row.priceCents,
      memberPriceCents: row.memberPriceCents,
      facilities: row.facilities,
      description: row.description,
      status: row.status
    })
    typePriceYuan.value = Number(yuan(row.priceCents))
    typeMemberPriceYuan.value = Number(yuan(row.memberPriceCents))
  } else {
    Object.assign(typeForm, {
      id: null, typeName: '', bedType: '大床', areaSqm: 25, capacity: 2,
      priceCents: 0, memberPriceCents: 0, facilities: '', description: '', status: 'ON_SALE'
    })
    typePriceYuan.value = 0
    typeMemberPriceYuan.value = 0
  }
  typeVisible.value = true
}

async function submitType() {
  if (!typeForm.typeName) {
    ElMessage.warning('请填写房型名称')
    return
  }
  submitting.value = true
  try {
    await roomApi.saveType({
      id: typeForm.id || undefined,
      typeName: typeForm.typeName,
      bedType: typeForm.bedType,
      areaSqm: typeForm.areaSqm,
      capacity: typeForm.capacity,
      priceCents: toCents(typePriceYuan.value),
      memberPriceCents: toCents(typeMemberPriceYuan.value),
      facilities: typeForm.facilities,
      description: typeForm.description,
      status: typeForm.status
    })
    ElMessage.success(typeForm.id ? '房型已更新' : '房型已创建')
    typeVisible.value = false
    loadTypes()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleTypeStatus(row) {
  const next = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  try {
    await ElMessageBox.confirm(
      `确认将房型《${row.typeName}》${next === 'ON_SALE' ? '上架' : '下架'}？` +
        (next === 'OFF_SALE' ? '下架后该房型不再接受新预订。' : ''),
      '房型状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await roomApi.saveType({ id: row.id, typeName: row.typeName, status: next })
    ElMessage.success('房型状态已更新')
    loadTypes()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

function openRateForm(row) {
  if (row) {
    Object.assign(rateForm, {
      id: row.id,
      typeId: row.typeId,
      planName: row.planName,
      startDate: row.startDate,
      endDate: row.endDate,
      priceCents: row.priceCents,
      status: row.status
    })
    ratePriceYuan.value = Number(yuan(row.priceCents))
  } else {
    Object.assign(rateForm, {
      id: null,
      typeId: types.value[0]?.id || null,
      planName: '',
      startDate: today(),
      endDate: today(),
      priceCents: 0,
      status: 'ACTIVE'
    })
    ratePriceYuan.value = 0
  }
  rateVisible.value = true
}

async function submitRate() {
  if (!rateForm.typeId) {
    ElMessage.warning('请选择房型')
    return
  }
  if (!rateForm.planName) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (rateForm.endDate < rateForm.startDate) {
    ElMessage.warning('生效结束日期不能早于开始日期')
    return
  }
  submitting.value = true
  try {
    await roomApi.saveRatePlan({
      id: rateForm.id || undefined,
      typeId: rateForm.typeId,
      planName: rateForm.planName,
      startDate: rateForm.startDate,
      endDate: rateForm.endDate,
      priceCents: toCents(ratePriceYuan.value),
      status: rateForm.status
    })
    ElMessage.success(rateForm.id ? '房价策略已更新' : '房价策略已创建')
    rateVisible.value = false
    loadRates()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleRateStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'ACTIVE' ? '启用' : '停用'}策略《${row.planName}》？`,
      '策略状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await roomApi.saveRatePlan({
      id: row.id,
      typeId: row.typeId,
      planName: row.planName,
      startDate: row.startDate,
      endDate: row.endDate,
      priceCents: row.priceCents,
      status: next
    })
    ElMessage.success('策略状态已更新')
    loadRates()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  loadRooms()
  loadTypes()
  loadRates()
})
</script>
