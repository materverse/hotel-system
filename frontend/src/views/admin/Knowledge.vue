<template>
  <div>
    <div class="page-title">AI 知识库管理</div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="按标题 / 分类筛选" style="width:220px" clearable
                  @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openForm()">新增文档</el-button>
        <el-button type="warning" :loading="reindexing" @click="doReindex">重建索引</el-button>
        <span class="muted" style="margin-left:auto">
          文档 {{ docs.length }} 篇 ｜ 片段 {{ status.knowledgeChunks ?? 0 }} 个 ｜
          索引大小 {{ status.indexSize ?? 0 }} ｜ topK {{ status.topK ?? '-' }} ｜ 阈值 {{ status.scoreThreshold ?? '-' }}
        </span>
      </div>

      <el-table :data="filteredDocs" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="文档标题" min-width="200" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="资料来源" width="150" />
        <el-table-column prop="chunkCount" label="片段数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'READY' ? 'success' : 'info'">
              {{ row.status === 'READY' ? '已索引' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDoc(row)">查看</el-button>
            <el-button size="small" @click="openForm(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!filteredDocs.length && !loading" class="empty">暂无知识库文档，可点击「新增文档」录入酒店制度与问答语料</div>
    </div>

    <div class="card">
      <div class="card-title">检索测试（RAG 召回效果验证）</div>
      <div class="toolbar">
        <el-input v-model="searchForm.question" placeholder="输入一个客人的问题，例如：押金什么时候退？"
                  style="width:420px" clearable @keyup.enter="doSearch" />
        <el-input-number v-model="searchForm.k" :min="1" :max="20" style="width:130px" />
        <span class="muted">topK</span>
        <el-button type="primary" :loading="searching" @click="doSearch">检索</el-button>
      </div>

      <div v-if="searchResult" style="margin-top:10px">
        <el-alert v-if="searchResult.noAnswer" type="warning" :closable="false" style="margin-bottom:10px"
                  title="知识库中没有达到阈值的内容，线上问答会明确说明无依据并建议转人工，不会编造答案。" />
        <div class="muted" style="margin-bottom:10px">
          命中 {{ searchResult.hitCount ?? (searchResult.hits || []).length }} 条 ｜
          topK {{ searchResult.topK ?? '-' }} ｜ 阈值 {{ searchResult.threshold ?? '-' }} ｜
          无答案判定 {{ searchResult.noAnswer ? '是' : '否' }}
        </div>
        <el-table :data="searchResult.hits || []" size="small">
          <el-table-column label="序号" width="70">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column prop="title" label="文档标题" width="200" />
          <el-table-column prop="source" label="来源" width="140" />
          <el-table-column prop="score" label="相似度" width="110" />
          <el-table-column prop="snippet" label="片段内容" min-width="320" show-overflow-tooltip />
        </el-table>
      </div>
      <div v-else class="empty">输入问题后点击「检索」查看知识库召回结果</div>
    </div>

    <!-- 新增 / 编辑文档 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑知识库文档' : '新增知识库文档'" width="720px" top="6vh">
      <el-form label-width="100px">
        <el-form-item label="文档标题">
          <el-input v-model="form.title" placeholder="如：入住与退房制度 / 押金与退费说明" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：入住须知 / 收费政策 / 会员权益" style="width:260px" />
        </el-form-item>
        <el-form-item label="资料来源">
          <el-input v-model="form.source" placeholder="如：前台操作手册 v2" style="width:320px" />
        </el-form-item>
        <el-form-item label="文档正文">
          <el-input v-model="form.content" type="textarea" :rows="12"
                    placeholder="粘贴酒店制度、常见问答等文本内容，保存后需点击「重建索引」才会参与检索。" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="READY">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看文档 -->
    <el-dialog v-model="viewVisible" :title="viewDocData.title" width="720px" top="6vh">
      <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="分类">{{ viewDocData.category }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ viewDocData.source || '-' }}</el-descriptions-item>
        <el-descriptions-item label="片段数">{{ viewDocData.chunkCount ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <div class="doc-content">{{ viewDocData.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, kbApi } from '@/api'

const docs = ref([])
const loading = ref(false)
const submitting = ref(false)
const reindexing = ref(false)
const searching = ref(false)
const status = ref({})

const query = reactive({ keyword: '' })
const formVisible = ref(false)
const viewVisible = ref(false)
const viewDocData = ref({})
const searchResult = ref(null)

const form = reactive({
  id: null, title: '', category: '入住须知', source: '', content: '', status: 'READY'
})

const searchForm = reactive({ question: '', k: 5 })

const filteredDocs = computed(() => {
  const kw = query.keyword.trim()
  if (!kw) return docs.value
  return docs.value.filter((d) => (d.title || '').includes(kw) || (d.category || '').includes(kw))
})

async function load() {
  loading.value = true
  try {
    const res = await kbApi.docs()
    const data = res.data
    docs.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
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

function openForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      category: row.category,
      source: row.source,
      content: row.content,
      status: row.status
    })
  } else {
    Object.assign(form, { id: null, title: '', category: '入住须知', source: '', content: '', status: 'READY' })
  }
  formVisible.value = true
}

async function submit() {
  if (!form.title) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请填写文档正文')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title,
      category: form.category || '通用',
      source: form.source || undefined,
      content: form.content,
      status: form.status
    }
    if (form.id) {
      await kbApi.update(form.id, payload)
    } else {
      await kbApi.create(payload)
    }
    ElMessage.success(form.id ? '文档已更新，建议重建索引后生效' : '文档已创建，请点击「重建索引」使其参与检索')
    formVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function viewDoc(row) {
  viewDocData.value = row
  viewVisible.value = true
  // 列表若不返回正文，则单独拉一次详情
  if (!row.content) {
    try {
      const res = await kbApi.docs({ id: row.id })
      const data = res.data
      const found = Array.isArray(data) ? data.find((d) => d.id === row.id) : data
      if (found) viewDocData.value = { ...row, ...found }
    } catch (e) {
      // 忽略：正文缺失时仅展示元信息
    }
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除知识库文档《${row.title}》？删除后其片段将不再参与检索。`,
      '删除文档',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await kbApi.remove(row.id)
    ElMessage.success('文档已删除')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function doReindex() {
  reindexing.value = true
  try {
    const res = await kbApi.reindex()
    const data = res.data || {}
    ElMessage.success(
      `索引重建完成：文档 ${data.docCount ?? data.docs ?? '-'} 篇，片段 ${data.chunkCount ?? data.chunks ?? '-'} 个`
    )
    load()
    loadStatus()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    reindexing.value = false
  }
}

async function doSearch() {
  if (!searchForm.question.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  searching.value = true
  try {
    const res = await kbApi.search({ question: searchForm.question, k: searchForm.k })
    searchResult.value = res.data || null
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  load()
  loadStatus()
})
</script>

<style scoped>
.doc-content {
  white-space: pre-wrap;
  line-height: 1.9;
  max-height: 460px;
  overflow-y: auto;
  background: #fbfdff;
  border: 1px solid var(--hotel-border);
  border-radius: 8px;
  padding: 14px;
}
</style>
