<template>
  <div>
    <div class="page-title">AI 评测（测试集 {{ report.total || 0 }} 条）</div>

    <div class="card">
      <div class="toolbar">
        <el-button type="primary" :loading="running" @click="run">运行全部测试集</el-button>
        <el-button @click="loadCases">查看测试用例</el-button>
        <span class="muted">
          评测覆盖：知识库召回与无答案处理、提示词注入防护、敏感信息脱敏、结构化输出校验、工具调用与数据查询。
        </span>
      </div>

      <div v-if="report.total" class="stat-grid" style="margin-bottom:12px">
        <div class="stat">
          <div class="label">用例总数</div>
          <div class="value">{{ report.total }}</div>
        </div>
        <div class="stat">
          <div class="label">通过</div>
          <div class="value" style="color:#1f8a5c">{{ report.passed }}</div>
        </div>
        <div class="stat">
          <div class="label">未通过</div>
          <div class="value" :style="{ color: report.failed ? '#d9480f' : '#1f8a5c' }">{{ report.failed }}</div>
        </div>
        <div class="stat">
          <div class="label">通过率</div>
          <div class="value">{{ report.passRate }}%</div>
        </div>
      </div>

      <el-table :data="report.results || []" size="small" v-loading="running">
        <el-table-column prop="id" label="用例号" width="100" />
        <el-table-column prop="category" label="类别" width="150" />
        <el-table-column prop="input" label="输入" min-width="220" show-overflow-tooltip />
        <el-table-column prop="expected" label="期望" min-width="200" show-overflow-tooltip />
        <el-table-column prop="actual" label="实际结果" min-width="240" show-overflow-tooltip />
        <el-table-column label="是否通过" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '未通过' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="analysis" label="问题分析" min-width="200" show-overflow-tooltip />
      </el-table>
      <div v-if="!report.total && !running" class="empty">点击「运行全部测试集」生成评测报告</div>
    </div>

    <div class="card">
      <div class="card-title">AI 调用日志（成本与可追溯）</div>
      <div class="toolbar">
        <el-button size="small" @click="loadLogs">刷新日志</el-button>
        <span class="muted">
          今日调用 {{ logStat.todayCalls ?? status.todayCalls ?? 0 }} 次，
          估算成本 ￥{{ yuan(logStat.todayCostCents ?? status.todayCostCents) }}
          ｜ 限流 {{ status.rateLimitPerMinute ?? '-' }} 次/分钟
          ｜ 智能体最大步数 {{ status.agentMaxSteps ?? '-' }}
          ｜ 可用工具 {{ status.toolCount ?? '-' }} 个
        </span>
      </div>
      <el-table :data="logStat.logs || []" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="scene" label="场景" width="120" />
        <el-table-column prop="provider" label="服务商" width="110" />
        <el-table-column prop="model" label="模型" width="140" />
        <el-table-column prop="promptVersion" label="提示词版本" width="110" />
        <el-table-column prop="promptTokens" label="输入token" width="100" />
        <el-table-column prop="outputTokens" label="输出token" width="100" />
        <el-table-column label="成本" width="100">
          <template #default="{ row }">￥{{ yuan(row.costCents) }}</template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column label="结果" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
            <el-tag v-if="row.degraded" size="small" type="warning" style="margin-left:4px">降级</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <div v-if="!(logStat.logs || []).length" class="empty">暂无 AI 调用记录</div>

      <div class="card-title" style="margin-top:16px">按场景统计</div>
      <el-table :data="logStat.scenes || []" size="small">
        <el-table-column prop="scene" label="场景" width="150" />
        <el-table-column prop="call_count" label="调用次数" width="100" />
        <el-table-column prop="success_count" label="成功" width="90" />
        <el-table-column prop="degraded_count" label="降级" width="90" />
        <el-table-column prop="avg_latency" label="平均耗时(ms)" width="130" />
        <el-table-column label="成本" width="120">
          <template #default="{ row }">￥{{ yuan(row.cost_cents) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 测试用例清单 -->
    <el-dialog v-model="casesVisible" title="AI 评测测试用例" width="860px" top="6vh">
      <el-table :data="cases" size="small">
        <el-table-column prop="id" label="用例号" width="100" />
        <el-table-column prop="category" label="类别" width="160" />
        <el-table-column prop="input" label="输入" min-width="240" show-overflow-tooltip />
        <el-table-column prop="expected" label="期望" min-width="240" show-overflow-tooltip />
      </el-table>
      <div v-if="!cases.length" class="empty">暂无测试用例</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi, statsApi, yuan } from '@/api'

const report = ref({})
const running = ref(false)
const logStat = ref({})
const status = ref({})
const cases = ref([])
const casesVisible = ref(false)

async function run() {
  running.value = true
  try {
    const res = await aiApi.runEval()
    report.value = res.data || {}
    ElMessage.success(`评测完成：${report.value.passed}/${report.value.total} 通过（${report.value.passRate}%）`)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    running.value = false
  }
}

async function loadLogs() {
  try {
    const res = await statsApi.ai({ limit: 50 })
    logStat.value = res.data || {}
  } catch (e) {
    logStat.value = {}
  }
}

async function loadStatus() {
  try {
    const res = await aiApi.status()
    status.value = res.data || {}
  } catch (e) {
    status.value = {}
  }
}

async function loadCases() {
  casesVisible.value = true
  if (cases.value.length) return
  try {
    const res = await aiApi.evalCases()
    const data = res.data
    cases.value = Array.isArray(data) ? data : (data && data.cases) || []
  } catch (e) {
    cases.value = []
  }
}

onMounted(() => {
  loadLogs()
  loadStatus()
})
</script>
