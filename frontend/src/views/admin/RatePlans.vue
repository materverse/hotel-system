<template>
  <div>
    <div class="page-title">房价策略</div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.typeId" placeholder="全部房型" clearable style="width:180px" @change="load">
          <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px" @change="load">
          <el-option label="生效中" value="ACTIVE" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openForm()">新增策略</el-button>
        <el-button style="margin-left:auto" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="typeName" label="房型" width="160" />
        <el-table-column prop="planName" label="策略名称" min-width="160" />
        <el-table-column prop="startDate" label="生效开始" width="130" />
        <el-table-column prop="endDate" label="生效结束" width="130" />
        <el-table-column label="策略价" width="140">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.priceCents) }}</span> / 晚</template>
        </el-table-column>
        <el-table-column label="对比门市价" width="170">
          <template #default="{ row }">
            <span v-if="marketPriceOf(row.typeId)">
              {{ diffText(row) }}
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="是否当前生效" width="130">
          <template #default="{ row }">
            <el-tag v-if="isActiveNow(row)" size="small" type="success">今日生效</el-tag>
            <el-tag v-else-if="row.status === 'ACTIVE'" size="small" type="info">未到生效期</el-tag>
            <span v-else class="muted">已停用</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openForm(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
                       @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!records.length && !loading" class="empty">暂无房价策略</div>

      <div class="muted" style="margin-top:12px;line-height:1.9">
        规则说明：某日期存在生效的房价策略时，房价试算按策略价执行且<b>不再叠加</b>会员折扣；
        无策略时按客人等级折扣使用会员价/门市价。同一房型同一天有多条生效策略时，取价格较高的一条。
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑房价策略' : '新增房价策略'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="房型">
          <el-select v-model="form.typeId" placeholder="请选择房型" style="width:100%">
            <el-option v-for="t in types" :key="t.id"
                       :label="`${t.typeName}（门市价 ￥${yuan(t.priceCents)}/晚）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略名称">
          <el-input v-model="form.planName" placeholder="如：周末价 / 国庆涨价 / 淡季促销" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生效开始">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效结束">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="策略价(元/晚)">
          <el-input-number v-model="priceYuan" :min="0" :max="100000" :step="50" style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rateApi, roomApi, today, toCents, yuan } from '@/api'

const records = ref([])
const types = ref([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const priceYuan = ref(0)
const todayText = today()

const query = reactive({ typeId: null, status: '' })
const form = reactive({
  id: null, typeId: null, planName: '', startDate: todayText,
  endDate: todayText, priceCents: 0, status: 'ACTIVE'
})

function marketPriceOf(typeId) {
  const t = types.value.find((x) => x.id === typeId)
  return t ? t.priceCents : 0
}

/** 与门市价的差额文案 */
function diffText(row) {
  const market = marketPriceOf(row.typeId)
  const diff = row.priceCents - market
  if (diff === 0) return '与门市价持平'
  const percent = market ? ((diff * 100) / market).toFixed(1) : '0'
  return diff > 0 ? `高于门市价 ￥${yuan(diff)}（+${percent}%）` : `低于门市价 ￥${yuan(-diff)}（${percent}%）`
}

function isActiveNow(row) {
  return row.status === 'ACTIVE' && row.startDate <= todayText && row.endDate >= todayText
}

async function load() {
  loading.value = true
  try {
    const res = await rateApi.list({
      typeId: query.typeId || undefined,
      status: query.status || undefined
    })
    const data = res.data
    records.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function loadTypes() {
  try {
    const res = await roomApi.types()
    const data = res.data
    types.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    types.value = []
  }
}

function openForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      typeId: row.typeId,
      planName: row.planName,
      startDate: row.startDate,
      endDate: row.endDate,
      priceCents: row.priceCents,
      status: row.status
    })
    priceYuan.value = Number(yuan(row.priceCents))
  } else {
    Object.assign(form, {
      id: null,
      typeId: types.value[0]?.id || null,
      planName: '',
      startDate: todayText,
      endDate: todayText,
      priceCents: 0,
      status: 'ACTIVE'
    })
    priceYuan.value = 0
  }
  formVisible.value = true
}

async function submit() {
  if (!form.typeId) {
    ElMessage.warning('请选择房型')
    return
  }
  if (!form.planName) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!form.startDate || !form.endDate || form.endDate < form.startDate) {
    ElMessage.warning('生效结束日期不能早于开始日期')
    return
  }
  submitting.value = true
  try {
    await rateApi.save({
      id: form.id || undefined,
      typeId: form.typeId,
      planName: form.planName,
      startDate: form.startDate,
      endDate: form.endDate,
      priceCents: toCents(priceYuan.value),
      status: form.status
    })
    ElMessage.success(form.id ? '房价策略已更新' : '房价策略已创建')
    formVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'ACTIVE' ? '启用' : '停用'}策略《${row.planName}》（${row.typeName}）？`,
      '策略状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await rateApi.changeStatus(row.id, next)
    ElMessage.success('策略状态已更新')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  loadTypes()
  load()
})
</script>
