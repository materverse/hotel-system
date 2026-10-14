<template>
  <SiteHeader />
  <div class="page">
    <div class="hero">
      <div>
        <h1>城科酒店管理系统</h1>
        <p>客房与房态 · 订房入住退房 · 账单结算 · 客人档案 · AI 智能前台</p>
        <div class="row" style="margin-top:16px">
          <el-button type="primary" size="large" @click="$router.push('/rooms')">查看房型与房价</el-button>
          <el-button size="large" @click="$router.push('/ai')">问问 AI 前台助手</el-button>
        </div>
      </div>
      <div class="hero-card">
        <div class="muted" style="color:#cfe0ee">实时房态概览</div>
        <div class="row" style="margin-top:10px;gap:18px">
          <div>
            <div class="muted" style="color:#cfe0ee">可售客房</div>
            <div class="value">{{ overview.roomTotal ?? 0 }} 间</div>
          </div>
          <div>
            <div class="muted" style="color:#cfe0ee">空闲</div>
            <div class="value">{{ overview.roomFree ?? 0 }} 间</div>
          </div>
          <div>
            <div class="muted" style="color:#cfe0ee">出租率</div>
            <div class="value">{{ occupancyText }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom:16px">
      <div class="stat">
        <div class="label">空闲房</div>
        <div class="value" style="color:#1f8a5c">{{ overview.roomFree ?? 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">在住</div>
        <div class="value">{{ overview.roomOccupied ?? 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">待打扫</div>
        <div class="value" style="color:#b45309">{{ overview.roomCleaning ?? 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">维修中</div>
        <div class="value" style="color:#7a8a97">{{ overview.roomMaintenance ?? 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">房型数量</div>
        <div class="value">{{ overview.roomTypeCount ?? types.length }}</div>
      </div>
      <div class="stat">
        <div class="label">知识库文档</div>
        <div class="value">{{ overview.knowledgeDocs ?? 0 }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">房型与房价</div>
      <el-row :gutter="14" v-loading="loading">
        <el-col :xs="24" :sm="12" :md="8" v-for="t in types" :key="t.id" style="margin-bottom:14px">
          <div class="type-card">
            <div class="type-head">
              <span class="type-name">{{ t.typeName }}</span>
              <el-tag size="small" :type="t.status === 'ON_SALE' ? 'success' : 'info'">
                {{ t.status === 'ON_SALE' ? '在售' : '停售' }}
              </el-tag>
            </div>
            <div class="muted" style="line-height:1.8">
              {{ t.bedType }} ｜ {{ t.areaSqm }} ㎡ ｜ 可住 {{ t.capacity }} 人
            </div>
            <div v-if="t.facilities" class="facilities">
              <el-tag v-for="(f, i) in splitFacilities(t.facilities)" :key="i" size="small" type="info" effect="plain"
                      style="margin:3px 4px 0 0">{{ f }}</el-tag>
            </div>
            <div class="muted" style="margin-top:8px;min-height:34px">{{ t.description }}</div>
            <div class="type-price">
              <span class="price">￥{{ yuan(t.priceCents) }}</span>
              <span class="muted">/晚 起</span>
              <span v-if="t.memberPriceCents" class="muted" style="margin-left:8px">
                会员价 ￥{{ yuan(t.memberPriceCents) }}
              </span>
            </div>
            <div class="muted" style="margin-top:6px">
              当前可售 {{ freeOfType(t.id) }} 间
            </div>
            <el-button type="primary" size="small" style="margin-top:10px;width:100%"
                       @click="$router.push('/rooms')">查价预订</el-button>
          </div>
        </el-col>
      </el-row>
      <div v-if="!types.length && !loading" class="empty">
        暂无房型数据，请先执行数据库脚本导入演示数据
      </div>
    </div>

    <div class="row">
      <div class="card grow">
        <div class="card-title">快捷入口</div>
        <div class="quick-grid">
          <div class="quick" @click="$router.push('/rooms')">
            <div class="q-title">房价试算</div>
            <div class="muted">选日期看每晚房价与总价</div>
          </div>
          <div class="quick" @click="$router.push('/ai')">
            <div class="q-title">AI 前台助手</div>
            <div class="muted">知识库问答 · 报价单生成</div>
          </div>
          <div class="quick" @click="$router.push(auth.isLogin ? '/guest' : '/login')">
            <div class="q-title">客人中心</div>
            <div class="muted">预存押金 · 我的预订与账单</div>
          </div>
          <div class="quick" @click="$router.push(auth.isLogin ? '/guest/transactions' : '/login')">
            <div class="q-title">预存流水</div>
            <div class="muted">预存 / 消费 / 退款可追溯</div>
          </div>
          <div class="quick" @click="$router.push(auth.isStaff ? '/admin' : '/login')">
            <div class="q-title">员工工作台</div>
            <div class="muted">前台看板 · 客房管理 · 结算</div>
          </div>
          <div class="quick" @click="$router.push('/register')">
            <div class="q-title">客人注册</div>
            <div class="muted">自助建档并享受会员折扣</div>
          </div>
        </div>
      </div>

      <div class="card grow">
        <div class="card-title">系统能做什么</div>
        <div v-for="f in features" :key="f.title" style="padding:8px 0">
          <div style="font-weight:700;margin-bottom:4px">{{ f.title }}</div>
          <div class="muted" style="line-height:1.7">{{ f.desc }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">房态一览（按楼层）</div>
      <div v-loading="loading">
        <div v-for="group in floors" :key="group.floor" style="margin-bottom:12px">
          <div style="font-weight:700;margin-bottom:6px">{{ group.floor }} 楼</div>
          <div class="room-grid">
            <div v-for="r in group.rooms" :key="r.id" class="room-cell" :class="r.status">
              <div class="no">{{ r.roomNo }}</div>
              <div class="meta">
                {{ r.typeName }}<br />
                {{ roomStatusText(r.status) }}
                <template v-if="r.guestName"><br />{{ r.guestName }}</template>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!floors.length && !loading" class="empty">暂无客房数据</div>
      </div>
      <div class="muted" style="margin-top:10px;line-height:1.9">
        图例：
        <span class="legend" style="background:#e8f6ef;color:#1f6b4a">空闲</span>
        <span class="legend" style="background:#e8f1f9;color:#1c5d99">已入住</span>
        <span class="legend" style="background:#fff6e5;color:#96650a">待打扫</span>
        <span class="legend" style="background:#f2f3f5;color:#61676e">维修</span>
        <span class="legend" style="background:#f3ecfb;color:#6b4ba8">已预订</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import SiteHeader from '@/components/SiteHeader.vue'
import { publicApi, roomStatusText, yuan } from '@/api'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const types = ref([])
const rooms = ref([])
const overview = ref({})
const loading = ref(false)

const features = [
  { title: '客房与房态管理', desc: '房型设置、房价策略、按楼层的房态图，空闲/已入住/待打扫/维修/已预订五种状态实时同步，客房服务打扫完成即自动释放。' },
  { title: '订房—入住—退房闭环', desc: '房价试算自动取生效策略价或会员折扣价，预订单保留至当日 18:00，超时未到店可标记未到店并释放房间。' },
  { title: '账单与押金', desc: '入住收取押金、在住期间登记餐饮洗衣迷你吧消费、退房按实际晚数重算房费、押金多退少补，全部记录可追溯。' },
  { title: '客人档案与会员', desc: '登记客人、消费与入住次数自动累计并升级黄金/白银等级，等级折扣直接体现在房价试算结果中。' },
  { title: 'AI 前台助手', desc: '基于酒店知识库的问答（带引用来源）、可调用业务数据工具的多步问答，以及结构化报价单生成与人工确认。' },
  { title: '统计与成本控制', desc: '营收走势、房型业绩、客人消费排行、渠道分布、房态与出租率统计，以及 AI 调用次数与成本日志。' }
]

/** 出租率：已入住 / 可售（维修房不计入） */
const occupancyText = computed(() => {
  const total = Number(overview.value.roomTotal || 0)
  const occupied = Number(overview.value.roomOccupied || 0)
  const maintenance = Number(overview.value.roomMaintenance || 0)
  const sellable = total - maintenance
  if (sellable <= 0) return '0%'
  return ((occupied * 100) / sellable).toFixed(1) + '%'
})

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

function splitFacilities(text) {
  if (!text) return []
  return String(text).split(/[,，、]/).map((s) => s.trim()).filter(Boolean)
}

/** 某房型当前空闲房间数（公开接口按 status 返回，这里用一次性拉取的全量房态计算） */
function freeOfType(typeId) {
  return rooms.value.filter((r) => r.typeId === typeId && r.status === 'FREE').length
}

onMounted(async () => {
  loading.value = true
  try {
    // 首页为公开页面：只调用免登录接口，避免出现 401 提示
    const [t, r, o] = await Promise.all([
      publicApi.roomTypes(),
      publicApi.rooms(),
      publicApi.overview()
    ])
    types.value = t.data || []
    rooms.value = r.data || []
    overview.value = o.data || {}
  } catch (e) {
    // 错误已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hero {
  display: flex;
  gap: 24px;
  align-items: center;
  background: linear-gradient(120deg, #12263a 0%, #1c5d99 100%);
  color: #fff;
  border-radius: 12px;
  padding: 34px 28px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.hero h1 {
  margin: 0 0 10px;
  font-size: 26px;
}
.hero p {
  margin: 0;
  color: #cfe0ee;
}
.hero-card {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 16px 18px;
  min-width: 300px;
}
.hero-card .value {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.type-card {
  border: 1px solid var(--hotel-border);
  border-radius: 10px;
  padding: 14px;
  background: #fff;
  height: 100%;
  transition: box-shadow 0.15s ease;
}
.type-card:hover {
  box-shadow: 0 4px 14px rgba(28, 93, 153, 0.12);
}
.type-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.type-name {
  font-weight: 700;
  font-size: 15px;
}
.facilities {
  margin-top: 8px;
}
.type-price {
  margin-top: 10px;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.quick {
  border: 1px solid var(--hotel-border);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.quick:hover {
  border-color: var(--hotel-primary);
  background: var(--hotel-primary-light);
}
.q-title {
  font-weight: 700;
  margin-bottom: 4px;
}
.legend {
  display: inline-block;
  border-radius: 4px;
  padding: 1px 8px;
  margin-right: 6px;
  font-size: 12px;
}
</style>
