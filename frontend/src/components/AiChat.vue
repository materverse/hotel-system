<template>
  <div class="chat-wrap">
    <div class="chat-box" ref="boxRef">
      <div class="msg assistant">
        <span class="who">系统</span>
        <div class="bubble">{{ greeting }}</div>
      </div>
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <span class="who">{{ m.role === 'user' ? auth.displayName || '我' : 'AI 前台助手' }}</span>
        <div class="bubble">
          {{ m.content }}
          <span v-if="m.degraded" class="tag-degraded">降级应答</span>
          <div v-if="m.needHumanConfirm" class="tag-degraded" style="display:inline-block;margin-top:6px">
            需人工确认
          </div>
          <div v-if="m.citations && m.citations.length" class="citation">
            <div style="font-weight:700;margin-bottom:4px">引用来源（知识库检索结果）</div>
            <div v-for="(c, ci) in m.citations" :key="ci">
              [{{ ci + 1 }}] 《{{ c.title }}》（{{ c.source }}，相似度 {{ c.score }}）<br />
              <span class="muted">{{ c.snippet }}</span>
            </div>
          </div>
          <div v-if="m.trace && m.trace.length" class="trace">
            <div style="font-weight:700;margin-bottom:4px">工具调用轨迹（可追踪的多步任务）</div>
            <div v-for="t in m.trace" :key="t.step">
              第 {{ t.step }} 步 · {{ t.tool }}（{{ t.success ? '成功' : '失败' }}，{{ t.elapsedMs }}ms）<br />
              参数：{{ t.arguments }}<br />
              返回：{{ t.result }}
            </div>
          </div>
          <div v-if="m.latencyMs" class="muted" style="margin-top:6px">
            模型 {{ m.model }} ｜ 提示词 {{ m.promptVersion }} ｜ 耗时 {{ m.latencyMs }}ms
          </div>
        </div>
      </div>
      <div v-if="loading" class="msg assistant">
        <span class="who">AI 前台助手</span>
        <div class="bubble">正在检索知识库并生成回答……</div>
      </div>
    </div>

    <div class="toolbar" style="margin-top:12px">
      <el-select v-model="scene" style="width:190px">
        <el-option label="知识库问答（RAG）" value="QA" />
        <el-option label="业务数据助手（工具调用）" value="AGENT" />
        <el-option label="入住报告（自然语言报表）" value="REPORT" />
      </el-select>
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        style="flex:1;min-width:260px"
        :placeholder="placeholder"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      <el-button v-if="loading" @click="stop">停止</el-button>
      <el-button @click="clear">清空对话</el-button>
    </div>
    <div class="muted" style="margin-top:8px">
      提示：知识库中没有依据时系统会明确说明并建议转人工，不会编造答案；报价、退款等涉及金额的操作一律需要人工确认后才会生效。
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const messages = ref([])
const question = ref('')
const scene = ref('QA')
const loading = ref(false)
const convNo = ref('')
const boxRef = ref(null)
let aborted = false

const greeting = computed(() => {
  if (scene.value === 'AGENT') {
    return '你好，我可以查询本酒店的真实业务数据（房态与出租率、今日到店离店、营收统计、客人档案、预订与账单），并告诉你每一步调用了哪个工具取数。'
  }
  if (scene.value === 'REPORT') {
    return '我可以基于你的历史入住与消费流水，生成一份自然语言的入住报告（需要客人账号登录）。'
  }
  return '你好，我是城科酒店 AI 前台助手。你可以问我入住与退房时间、押金与退费规则、房型设施、房价策略、发票与停车、会员权益等问题，我会基于酒店知识库回答并标注引用来源。'
})

const placeholder = computed(() => {
  if (scene.value === 'AGENT') return '例如：今天有几间待打扫？这个月营收多少？'
  if (scene.value === 'REPORT') return '例如：帮我生成我的入住报告'
  return '例如：入住和退房时间是怎么规定的？押金什么时候退？'
})

async function send() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  messages.value.push({ role: 'user', content: text })
  question.value = ''
  loading.value = true
  aborted = false
  scrollToBottom()
  try {
    const res = await aiApi.ask(scene.value, { question: text, convNo: convNo.value || undefined })
    if (aborted) return
    const data = res.data || {}
    convNo.value = data.conversationNo
    messages.value.push({
      role: 'assistant',
      content: data.answer,
      citations: data.citations,
      trace: data.trace,
      degraded: data.degraded,
      needHumanConfirm: data.needHumanConfirm,
      latencyMs: data.latencyMs,
      model: data.model,
      promptVersion: data.promptVersion
    })
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

/** 前端停止等待：请求已在途，停止后不再展示该轮结果 */
function stop() {
  aborted = true
  loading.value = false
  ElMessage.info('已停止等待本次回答')
}

function clear() {
  messages.value = []
  convNo.value = ''
}

function scrollToBottom() {
  nextTick(() => {
    if (boxRef.value) boxRef.value.scrollTop = boxRef.value.scrollHeight
  })
}

onMounted(async () => {
  try {
    const res = await aiApi.status()
    const data = res.data || {}
    if (!data.keyConfigured) {
      ElMessage.warning('当前未配置模型密钥，AI 将返回降级提示，其它功能不受影响')
    }
  } catch (e) {
    // 忽略：AI 状态查询失败不影响其它功能
  }
})
</script>

<style scoped>
.chat-wrap {
  display: flex;
  flex-direction: column;
}
.chat-box {
  min-height: 420px;
  max-height: 560px;
}
</style>
