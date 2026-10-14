<template>
  <div>
    <div class="page-title">房态与出租率统计</div>

    <div class="card">
      <div class="toolbar">
        <el-button type="primary" :loading="loading" @click="load">刷新统计</el-button>
        <span class="muted" style="margin-left:auto">出租率 = 已入住房间数 / 可售房间数（维修房不计入分母）</span>
      </div>

      <div v-loading="loading">
        <div class="stat-grid" style="margin-bottom:16px">
          <div class="stat">
            <div class="label">客房总数</div>
            <div class="value">{{ overview.roomTotal ?? 0 }}</div>
          </div>
          <div class="stat">
            <div class="label">可售房间（扣除维修）</div>
            <div class="value">{{ sellable }}</div>
          </div>
          <div class="stat">
            <div class="label">今日出租率</div>
            <div class="value">{{ occupancyRate }}%</div>
          </div>
          <div class="stat">
            <div class="label">空闲房</div>
            <div class="value" style="color:#1f8a5c">{{ overview.roomFree ?? 0 }}</div>
          </div>
          <div class="stat">
            <div class="label">在住 / 已预订</div>
            <div class="value">
              {{ overview.roomOccupied ?? 0 }} / {{ countOf('BOOKED') }}
            </div>
          </div>
          <div class="stat">
            <div class="label">待打扫 / 维修</div>
            <div class="value" style="font-size:16px">
              {{ overview.roomCleaning ?? 0 }} / {{ overview.roomMaintenance ?? 0 }}
            </div>
          </div>
        </div>

        <div class="row">
          <div class="card grow">
            <div class="card-title">房态分布</div>
            <el-table :data="statusRows" size="small">
              <el-table-column label="房态" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :type="roomStatusTag(row.status)">{{ roomStatusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="cnt" label="房间数" width="100" />
              <el-table-column label="占比" min-width="240">
                <template #default="{ row }">
                  <el-progress :percentage="percentOf(row.cnt)" :stroke-width="14"
                               :color="progressColor(row.status)" />
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!statusRows.length" class="empty">暂无房态数据</div>
          </div>

          <div class="card grow">
            <div class="card-title">房型出租情况</div>
            <el-table :data="typeStats" size="small">
              <el-table-column prop="type_name" label="房型" min-width="130" />
              <el-table-column prop="room_count" label="房间数" width="90" />
              <el-table-column prop="occupied_count" label="在住" width="80" />
              <el-table-column label="出租率" width="180">
                <template #default="{ row }">
                  <el-progress :percentage="typePercent(row)" :stroke-width="14" />
                </template>
              </el-table-column>
              <el-table-column prop="reservation_count" label="累计预订" width="100" />
            </el-table>
            <div v-if="!typeStats.length" class="empty">暂无房型数据</div>
          </div>
        </div>

        <div class="card">
          <div class="card-title">近 7 天入住 / 退房量</div>
          <el-table :data="stayTrend" size="small">
            <el-table-column prop="day" label="日期" width="140" />
            <el-table-column prop="check_in_count" label="入住量" width="120" />
            <el-table-column prop="check_out_count" label="退房量" width="120" />
          </el-table>
          <div v-if="!stayTrend.length" class="empty">暂无入住退房数据</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { publicApi, roomStatusTag, roomStatusText, statsApi } from '@/api'

const loading = ref(false)
const raw = ref({})
const rooms = ref([])

const overview = computed(() => raw.value.overview || raw.value.summary || {})
const statusRows = computed(() => raw.value.roomStatus || raw.value.room_status || [])
const typeStats = computed(() => raw.value.roomTypeStats || raw.value.room_type_stats || [])
const stayTrend = computed(() => raw.value.stayTrend || raw.value.stay_trend || [])

const sellable = computed(() => {
  const total = Number(overview.value.roomTotal || 0)
  const maintenance = Number(overview.value.roomMaintenance || 0)
  return Math.max(0, total - maintenance)
})

const occupancyRate = computed(() => {
  if (raw.value.occupancyRate !== undefined && raw.value.occupancyRate !== null) {
    return raw.value.occupancyRate
  }
  if (!sellable.value) return 0
  return Math.round((Number(overview.value.roomOccupied || 0) * 1000) / sellable.value) / 10
})

function countOf(status) {
  return rooms.value.filter((r) => r.status === status).length
}

const totalRooms = computed(() => statusRows.value.reduce((s, r) => s + Number(r.cnt || 0), 0) || 1)

function percentOf(cnt) {
  return Math.round((Number(cnt || 0) * 1000) / totalRooms.value) / 10
}

function progressColor(status) {
  return {
    FREE: '#1f8a5c',
    OCCUPIED: '#1c5d99',
    CLEANING: '#d99a2b',
    MAINTENANCE: '#8b949e',
    BOOKED: '#8b5cf6'
  }[status] || '#1c5d99'
}

function typePercent(row) {
  const total = Number(row.room_count || 0)
  if (!total) return 0
  return Math.round((Number(row.occupied_count || 0) * 1000) / total) / 10
}

async function load() {
  loading.value = true
  try {
    const res = await statsApi.room()
    raw.value = res.data || {}
  } catch (e) {
    raw.value = {}
  } finally {
    loading.value = false
  }
  // 房态与概览用公开接口兜底，保证统计页在统计接口缺字段时也能展示
  try {
    const [o, r] = await Promise.all([publicApi.overview(), publicApi.rooms()])
    if (!raw.value.overview && !raw.value.summary) {
      raw.value = { ...raw.value, overview: o.data || {} }
    }
    rooms.value = r.data || []
    if (!statusRows.value.length) {
      const map = new Map()
      for (const room of rooms.value) {
        map.set(room.status, (map.get(room.status) || 0) + 1)
      }
      raw.value = {
        ...raw.value,
        roomStatus: [...map.entries()].map(([status, cnt]) => ({ status, cnt })).sort((a, b) => b.cnt - a.cnt)
      }
    }
    if (!typeStats.value.length) {
      raw.value = { ...raw.value, roomTypeStats: buildTypeStats() }
    }
  } catch (e) {
    // 忽略：公开接口仅用于兜底展示
  } finally {
    loading.value = false
  }
}

/** 统计接口未返回房型出租情况时，用公开房态数据前端汇总 */
function buildTypeStats() {
  const map = new Map()
  for (const r of rooms.value) {
    const key = r.typeId
    if (!map.has(key)) {
      map.set(key, { type_name: r.typeName, room_count: 0, occupied_count: 0, reservation_count: '-' })
    }
    const item = map.get(key)
    item.room_count++
    if (r.status === 'OCCUPIED') item.occupied_count++
  }
  return [...map.values()]
}

onMounted(load)
</script>
