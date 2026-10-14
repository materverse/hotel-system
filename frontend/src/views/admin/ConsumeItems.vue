<template>
  <div>
    <div class="page-title">消费项目管理</div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width:160px" @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px" @change="load">
          <el-option label="启用中" value="ON_SALE" />
          <el-option label="已停用" value="OFF_SALE" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openForm()">新增项目</el-button>
        <el-button style="margin-left:auto" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="itemName" label="项目名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="130">
          <template #default="{ row }"><span class="price">￥{{ yuan(row.priceCents) }}</span></template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="90" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ON_SALE' ? 'success' : 'info'">
              {{ row.status === 'ON_SALE' ? '启用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openForm(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 'ON_SALE' ? 'warning' : 'success'"
                       @click="toggleStatus(row)">
              {{ row.status === 'ON_SALE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!records.length && !loading" class="empty">暂无消费项目，可点击「新增项目」添加餐饮、洗衣、迷你吧等服务</div>

      <div class="muted" style="margin-top:12px;line-height:1.9">
        说明：停用后的项目不会出现在前台「登记消费」的可选列表中，已登记的历史账单明细不受影响。
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑消费项目' : '新增消费项目'" width="540px">
      <el-form label-width="100px">
        <el-form-item label="项目名称">
          <el-input v-model="form.itemName" placeholder="如：双人早餐 / 洗衣服务 / 迷你吧可乐" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%" filterable allow-create>
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价(元)">
          <el-input-number v-model="priceYuan" :min="0" :max="100000" :precision="2" :step="5" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="份 / 次 / 瓶 / 件" style="width:160px" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ON_SALE">启用</el-radio-button>
            <el-radio-button value="OFF_SALE">停用</el-radio-button>
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
import { billApi, toCents, yuan } from '@/api'

const categories = ['餐饮', '洗衣', '迷你吧', '叫醒服务', '加床', '其它']

const records = ref([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const priceYuan = ref(0)

const query = reactive({ category: '', status: '' })
const form = reactive({
  id: null, itemName: '', category: '餐饮', priceCents: 0,
  unit: '份', description: '', status: 'ON_SALE'
})

async function load() {
  loading.value = true
  try {
    const res = await billApi.consumeItems({
      category: query.category || undefined,
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

function openForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      itemName: row.itemName,
      category: row.category,
      priceCents: row.priceCents,
      unit: row.unit,
      description: row.description,
      status: row.status
    })
    priceYuan.value = Number(yuan(row.priceCents))
  } else {
    Object.assign(form, {
      id: null, itemName: '', category: '餐饮', priceCents: 0,
      unit: '份', description: '', status: 'ON_SALE'
    })
    priceYuan.value = 0
  }
  formVisible.value = true
}

async function submit() {
  if (!form.itemName) {
    ElMessage.warning('请填写项目名称')
    return
  }
  submitting.value = true
  try {
    await billApi.saveConsumeItem({
      id: form.id || undefined,
      itemName: form.itemName,
      category: form.category,
      priceCents: toCents(priceYuan.value),
      unit: form.unit || '份',
      description: form.description || undefined,
      status: form.status
    })
    ElMessage.success(form.id ? '消费项目已更新' : '消费项目已创建')
    formVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'ON_SALE' ? '启用' : '停用'}消费项目《${row.itemName}》？`,
      '状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await billApi.saveConsumeItem({
      id: row.id,
      itemName: row.itemName,
      category: row.category,
      priceCents: row.priceCents,
      unit: row.unit,
      description: row.description,
      status: next
    })
    ElMessage.success('状态已更新')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(load)
</script>
