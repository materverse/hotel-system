<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">AI 前台助手</div>

    <div class="row" style="margin-bottom:16px">
      <div class="stat">
        <div class="label">模型状态</div>
        <div class="value" style="font-size:15px">
          <el-tag size="small" :type="status.enabled ? 'success' : 'info'">
            {{ status.enabled ? '已启用' : '未启用' }}
          </el-tag>
          <el-tag size="small" :type="status.keyConfigured ? 'success' : 'warning'" style="margin-left:6px">
            {{ status.keyConfigured ? '密钥已配置' : '密钥未配置（降级应答）' }}
          </el-tag>
        </div>
      </div>
      <div class="stat">
        <div class="label">模型 / 提示词版本</div>
        <div class="value" style="font-size:14px">{{ status.model || '-' }} / {{ status.promptVersion || '-' }}</div>
      </div>
      <div class="stat">
        <div class="label">知识库文档 / 片段</div>
        <div class="value">{{ status.knowledgeDocs ?? 0 }} / {{ status.knowledgeChunks ?? 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">今日调用 / 成本</div>
        <div class="value" style="font-size:15px">
          {{ status.todayCalls ?? 0 }} 次 / ￥{{ yuan(status.todayCostCents) }}
        </div>
      </div>
      <div class="stat">
        <div class="label">检索参数</div>
        <div class="value" style="font-size:14px">
          topK {{ status.topK ?? '-' }} ｜ 阈值 {{ status.scoreThreshold ?? '-' }}
        </div>
      </div>
    </div>

    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="AI 对话助手" name="chat">
          <AiChat />
        </el-tab-pane>

        <el-tab-pane label="报价单生成" name="quote">
          <div class="muted" style="margin-bottom:12px;line-height:1.8">
            由 AI 依据房型、间数与间夜生成结构化报价单（含每晚房价、总价、押金、优惠、有效期与注意事项）。
            报价单属于「生成草稿 → 人工确认」流程，未经人工确认不会影响任何房态与账单数据。
          </div>
          <div class="toolbar">
            <el-select v-model="qForm.typeId" placeholder="选择房型" style="width:180px">
              <el-option v-for="t in types" :key="t.id" :label="`${t.typeName}（￥${yuan(t.priceCents)}/晚）`" :value="t.id" />
            </el-select>
            <el-input-number v-model="qForm.roomCount" :min="1" :max="10" style="width:130px" />
            <span class="muted">间</span>
            <el-input-number v-model="qForm.nights" :min="1" :max="30" style="width:130px" />
            <span class="muted">晚</span>
            <el-date-picker v-model="qForm.checkInDate" type="date" value-format="YYYY-MM-DD"
                            placeholder="入住日期" :disabled-date="disablePast" style="width:160px" />
            <el-button type="primary" :loading="generating" @click="generate">生成报价单</el-button>
            <el-button @click="loadQuotes">刷新历史</el-button>
          </div>
          <el-input v-model="qForm.requirement" type="textarea" :rows="2"
                    placeholder="特殊要求，例如：需要无烟高楼层、加一张婴儿床、连住 3 晚希望有折扣"
                    style="margin-bottom:12px" />

          <div v-if="current" class="quote-sheet">
            <h3>{{ current.quote?.title || current.title || '房价报价单' }}</h3>
            <div class="muted" style="margin-bottom:10px">
              报价单号 #{{ current.quoteId }}
              <el-tag size="small" :type="quoteStatusTag(current.status)" style="margin-left:8px">
                {{ quoteStatusText(current.status) }}
              </el-tag>
              <el-tag v-if="current.degraded" size="small" type="warning" style="margin-left:6px">降级应答</el-tag>
              <el-tag v-if="current.needHumanConfirm" size="small" type="danger" style="margin-left:6px">需人工确认</el-tag>
            </div>

            <div v-if="current.message" class="muted" style="margin-bottom:10px">{{ current.message }}</div>

            <el-table :data="current.quote?.nights_detail || current.quote?.items || []" size="small"
                      style="margin-bottom:12px">
              <el-table-column prop="date" label="日期" width="130" />
              <el-table-column prop="weekday" label="星期" width="90" />
              <el-table-column label="每晚房价" width="130">
                <template #default="{ row }">￥{{ yuan(row.priceCents ?? row.unitPriceCents) }}</template>
              </el-table-column>
              <el-table-column label="间数" width="80">
                <template #default="{ row }">{{ row.roomCount ?? qForm.roomCount }}</template>
              </el-table-column>
              <el-table-column label="小计" width="130">
                <template #default="{ row }">￥{{ yuan(row.amountCents ?? row.totalCents) }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="160" />
            </el-table>

            <div class="quote-row">
              <span>房型 · 间数 · 间夜</span>
              <span>
                {{ current.quote?.typeName || '-' }} ｜ {{ current.quote?.roomCount ?? qForm.roomCount }} 间 ｜
                {{ current.quote?.nights ?? qForm.nights }} 晚
              </span>
            </div>
            <div class="quote-row">
              <span>门市单价</span>
              <span>￥{{ yuan(current.quote?.originUnitPriceCents ?? current.quote?.originPriceCents) }} / 晚</span>
            </div>
            <div class="quote-row">
              <span>执行单价</span>
              <span class="amount-big">￥{{ yuan(current.quote?.unitPriceCents) }} / 晚</span>
            </div>
            <div class="quote-row">
              <span>优惠说明</span>
              <span>
                {{ current.quote?.discountDesc || discountText(current.quote?.discountRate) }}
                <span v-if="current.quote?.discountCents" class="price" style="margin-left:6px">
                  −￥{{ yuan(current.quote.discountCents) }}
                </span>
              </span>
            </div>
            <div class="quote-row">
              <span>需预付押金</span>
              <span>￥{{ yuan(current.quote?.depositCents) }}</span>
            </div>
            <div class="quote-row">
              <span>有效期</span>
              <span>{{ current.quote?.validUntil || '-' }}</span>
            </div>
            <div class="quote-row total">
              <span>报价总额</span>
              <span>￥{{ yuan(current.quote?.totalCents) }}</span>
            </div>

            <div v-if="current.riskNotice" class="citation" style="margin-top:12px">
              <div style="font-weight:700;margin-bottom:4px">风险提示</div>
              {{ current.riskNotice }}
            </div>
            <div v-if="current.quote?.notice" class="citation" style="margin-top:8px">
              <div style="font-weight:700;margin-bottom:4px">注意事项</div>
              {{ current.quote.notice }}
            </div>

            <div style="margin-top:14px" v-if="current.status === 'PENDING'">
              <el-button type="success" :loading="confirming" @click="confirm(true)">人工确认通过</el-button>
              <el-button type="danger" :loading="confirming" @click="confirm(false)">驳回报价单</el-button>
              <span class="muted" style="margin-left:10px">确认后该报价单才会被视为有效对外报价</span>
            </div>
            <div v-else class="muted" style="margin-top:12px">
              该报价单已由 {{ current.confirmedBy || '员工' }} 处理，状态不再变更。
            </div>
            <div v-if="current.model" class="muted" style="margin-top:8px">生成模型：{{ current.model }}</div>
          </div>
          <div v-else class="empty">选择房型与间夜后点击「生成报价单」</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="card">
      <div class="card-title">历史报价单</div>
      <el-table :data="quotes" v-loading="loadingQuotes" size="small">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="房型 / 间夜" width="180">
          <template #default="{ row }">{{ row.typeName || ('房型#' + row.typeId) }} ｜ {{ row.roomCount }} 间 × {{ row.nights }} 晚</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="quoteStatusTag(row.status)">{{ quoteStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmedBy" label="处理人" width="110" />
        <el-table-column prop="confirmedAt" label="处理时间" width="170" />
        <el-table-column prop="model" label="模型" width="130" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewQuote(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!quotes.length && !loadingQuotes" class="empty">暂无历史报价单</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import AiChat from '@/components/AiChat.vue'
import { addDays, aiApi, discountText, publicApi, today, yuan } from '@/api'

const tab = ref('chat')
const status = ref({})
const types = ref([])
const quotes = ref([])
const loadingQuotes = ref(false)
const generating = ref(false)
const confirming = ref(false)
const current = ref(null)

const qForm = reactive({
  typeId: null,
  roomCount: 1,
  nights: 1,
  checkInDate: today(),
  requirement: ''
})

function disablePast(date) {
  return date.getTime() < new Date(today()).getTime()
}

function quoteStatusText(s) {
  return { PENDING: '待确认', CONFIRMED: '已确认', REJECTED: '已驳回', EXPIRED: '已过期' }[s] || s || '-'
}

function quoteStatusTag(s) {
  return { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger', EXPIRED: 'info' }[s] || 'info'
}

/** 历史列表里的 quoteJson 是字符串，展开成结构化对象便于详情展示 */
function normalizeQuote(row) {
  const copy = { ...row }
  if (typeof copy.quoteJson === 'string' && copy.quoteJson) {
    try {
      copy.quote = JSON.parse(copy.quoteJson)
    } catch (e) {
      copy.quote = null
    }
  } else if (copy.quoteJson && typeof copy.quoteJson === 'object') {
    copy.quote = copy.quoteJson
  }
  return copy
}

async function loadStatus() {
  try {
    const res = await aiApi.status()
    status.value = res.data || {}
  } catch (e) {
    // 忽略：状态查询失败不影响页面其它部分
  }
}

async function loadQuotes() {
  loadingQuotes.value = true
  try {
    const res = await aiApi.quotes()
    quotes.value = (res.data || []).map(normalizeQuote)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loadingQuotes.value = false
  }
}

async function generate() {
  if (!qForm.typeId) {
    ElMessage.warning('请选择房型')
    return
  }
  generating.value = true
  try {
    const res = await aiApi.generateQuote({
      typeId: qForm.typeId,
      roomCount: qForm.roomCount,
      nights: qForm.nights,
      checkInDate: qForm.checkInDate,
      requirement: qForm.requirement || undefined
    })
    const data = res.data || {}
    current.value = { ...data, quote: data.quote || null }
    if (data.valid === false) {
      ElMessage.warning(data.message || '报价单未通过校验，请调整参数后重试')
    } else {
      ElMessage.success('报价单已生成，请人工确认')
    }
    loadQuotes()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    generating.value = false
  }
}

async function confirm(approved) {
  if (!current.value || !current.value.quoteId) return
  try {
    await ElMessageBox.confirm(
      approved ? '确认该报价单有效并对外报价？' : '确认驳回该报价单？驳回后需重新生成。',
      '人工确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  confirming.value = true
  try {
    await aiApi.confirmQuote(current.value.quoteId, approved)
    ElMessage.success(approved ? '报价单已确认' : '报价单已驳回')
    current.value = { ...current.value, status: approved ? 'CONFIRMED' : 'REJECTED' }
    loadQuotes()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    confirming.value = false
  }
}

function viewQuote(row) {
  tab.value = 'quote'
  current.value = normalizeQuote(row)
}

onMounted(async () => {
  loadStatus()
  loadQuotes()
  try {
    const res = await publicApi.roomTypes()
    types.value = res.data || []
    if (types.value.length) {
      qForm.typeId = types.value[0].id
      qForm.checkInDate = today()
    }
  } catch (e) {
    // 错误已由拦截器提示
  }
})
</script>
