<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">房型与房价</div>

    <div class="card">
      <div class="card-title">房价试算（公开报价，不需登录）</div>
      <div class="toolbar">
        <el-select v-model="query.typeId" placeholder="请选择房型" clearable style="width:200px">
          <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
        <el-date-picker v-model="query.checkIn" type="date" value-format="YYYY-MM-DD"
                        placeholder="入住日期" :disabled-date="disablePast" style="width:160px" />
        <el-date-picker v-model="query.checkOut" type="date" value-format="YYYY-MM-DD"
                        placeholder="离店日期" :disabled-date="disablePast" style="width:160px" />
        <el-input-number v-model="query.roomCount" :min="1" :max="10" style="width:130px" />
        <span class="muted">间</span>
        <el-button type="primary" :loading="quoting" @click="doQuote">立即试算</el-button>
      </div>
      <div class="muted" style="line-height:1.9">
        规则：入住 14:00 之后、离店 12:00 之前；单笔预订最长 30 晚；黄金会员 9 折、白银会员 9.5 折；
        若所选日期存在生效的房价策略，则按策略价执行且不再叠加会员折扣。
      </div>

      <div v-if="quote" class="quote-sheet" style="margin-top:14px">
        <h3>{{ quote.typeName }} · 房价试算结果</h3>
        <div class="muted" style="margin-bottom:10px">
          {{ query.checkIn }} 至 {{ query.checkOut }}，共 {{ quote.nights }} 晚 × {{ quote.roomCount }} 间
        </div>
        <div class="quote-row">
          <span>门市价</span>
          <span>￥{{ yuan(quote.originUnitPriceCents) }} / 晚</span>
        </div>
        <div class="quote-row">
          <span>实际执行单价</span>
          <span class="amount-big">￥{{ yuan(quote.unitPriceCents) }} / 晚</span>
        </div>
        <div class="quote-row">
          <span>价格来源</span>
          <span>{{ quote.priceSource }}<span class="muted" style="margin-left:6px">（{{ discountText(quote.discountRate) }}）</span></span>
        </div>
        <div class="quote-row">
          <span>房费小计</span>
          <span>{{ yuan(quote.unitPriceCents) }} × {{ quote.nights }} 晚 × {{ quote.roomCount }} 间</span>
        </div>
        <div class="quote-row">
          <span>需预付押金</span>
          <span>￥{{ yuan(quote.depositCents) }}</span>
        </div>
        <div class="quote-row total">
          <span>房费合计</span>
          <span>￥{{ yuan(quote.totalCents) }}</span>
        </div>
        <div class="muted" style="margin-top:10px;line-height:1.8">
          押金在离店结算时按实际消费多退少补；预订单保留至入住当日 18:00，超时未到店将自动释放房间。
          如需预订请联系前台，或登录后到「客人中心」查看自己的预订与账单。
        </div>
        <div style="margin-top:10px">
          <el-button type="primary" size="small" @click="$router.push('/ai')">让 AI 生成正式报价单</el-button>
          <el-button size="small" @click="resetQuote">清空试算</el-button>
        </div>
      </div>
      <div v-else-if="quoteError" class="empty">{{ quoteError }}</div>
    </div>

    <div class="card">
      <div class="card-title">全部房型</div>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索房型 / 床型" style="width:220px" clearable />
        <el-checkbox v-model="onlyOnSale">只看在售</el-checkbox>
      </div>
      <el-table :data="filteredTypes" v-loading="loading" size="small">
        <el-table-column prop="typeName" label="房型" min-width="140" />
        <el-table-column prop="bedType" label="床型" width="90" />
        <el-table-column label="面积 / 可住" width="120">
          <template #default="{ row }">{{ row.areaSqm }} ㎡ / {{ row.capacity }} 人</template>
        </el-table-column>
        <el-table-column label="门市价" width="120">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.priceCents) }}</span></template>
        </el-table-column>
        <el-table-column label="会员价" width="120">
          <template #default="{ row }">￥{{ yuan(row.memberPriceCents) }}</template>
        </el-table-column>
        <el-table-column label="当前空闲" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="freeOfType(row.id) > 0 ? 'success' : 'info'">
              {{ freeOfType(row.id) }} 间
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="facilities" label="配套设施" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ON_SALE' ? 'success' : 'info'">
              {{ row.status === 'ON_SALE' ? '在售' : '停售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="quickQuote(row)">试算房价</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!filteredTypes.length && !loading" class="empty">没有符合条件的房型</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import { addDays, discountText, publicApi, today, yuan } from '@/api'

const types = ref([])
const rooms = ref([])
const loading = ref(false)
const quoting = ref(false)
const quote = ref(null)
const quoteError = ref('')
const keyword = ref('')
const onlyOnSale = ref(false)

const query = reactive({
  typeId: null,
  checkIn: today(),
  checkOut: addDays(today(), 1),
  roomCount: 1
})

const filteredTypes = computed(() => {
  const kw = keyword.value.trim()
  return types.value.filter((t) => {
    if (onlyOnSale.value && t.status !== 'ON_SALE') return false
    if (!kw) return true
    return (t.typeName || '').includes(kw) || (t.bedType || '').includes(kw)
  })
})

/** 不允许选择过去的日期 */
function disablePast(date) {
  return date.getTime() < new Date(today()).getTime()
}

function freeOfType(typeId) {
  return rooms.value.filter((r) => r.typeId === typeId && r.status === 'FREE').length
}

async function doQuote() {
  if (!query.typeId) {
    ElMessage.warning('请先选择房型')
    return
  }
  if (!query.checkIn || !query.checkOut) {
    ElMessage.warning('请选择入住与离店日期')
    return
  }
  if (query.checkOut <= query.checkIn) {
    ElMessage.warning('离店日期必须晚于入住日期')
    return
  }
  quoting.value = true
  quoteError.value = ''
  try {
    const res = await publicApi.quote({
      typeId: query.typeId,
      checkIn: query.checkIn,
      checkOut: query.checkOut,
      roomCount: query.roomCount
    })
    quote.value = res.data || null
  } catch (e) {
    quote.value = null
    quoteError.value = '试算失败：' + ((e && e.message) || '请检查房型与日期后重试')
  } finally {
    quoting.value = false
  }
}

/** 从表格行直接带入房型并试算 */
function quickQuote(row) {
  query.typeId = row.id
  doQuote()
}

function resetQuote() {
  quote.value = null
  quoteError.value = ''
}

onMounted(async () => {
  loading.value = true
  try {
    const [t, r] = await Promise.all([publicApi.roomTypes(), publicApi.rooms()])
    types.value = t.data || []
    rooms.value = r.data || []
    if (!query.typeId && types.value.length) {
      query.typeId = types.value[0].id
    }
  } catch (e) {
    // 错误已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
})
</script>
