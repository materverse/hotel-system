<template>
  <div>
    <div class="page-title">员工与权限</div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部在岗状态" clearable style="width:160px" @change="load">
          <el-option label="在岗" value="ON_DUTY" />
          <el-option label="休假" value="REST" />
          <el-option label="离职" value="LEFT" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="姓名 / 工号 / 岗位" style="width:220px" clearable
                  @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openForm()">新增员工</el-button>
        <el-button style="margin-left:auto" @click="loadAll">刷新</el-button>
      </div>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="员工档案" name="staff">
          <el-table :data="staff" v-loading="loading" size="small">
            <el-table-column prop="staffNo" label="工号" width="110" />
            <el-table-column prop="realName" label="姓名" width="110" />
            <el-table-column prop="gender" label="性别" width="70" />
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column prop="position" label="岗位" width="130" />
            <el-table-column prop="workShift" label="班次" width="90" />
            <el-table-column prop="intro" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small"
                        :type="row.status === 'ON_DUTY' ? 'success' : row.status === 'REST' ? 'warning' : 'info'">
                  {{ { ON_DUTY: '在岗', REST: '休假', LEFT: '离职' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openStaffEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!staff.length && !loading" class="empty">暂无员工档案</div>
        </el-tab-pane>

        <el-tab-pane label="登录账号与权限" name="users">
          <el-table :data="users" v-loading="loading" size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="登录账号" width="140" />
            <el-table-column prop="realName" label="姓名" width="110" />
            <el-table-column label="角色" width="130">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : row.role === 'GUEST' ? 'info' : 'success'">
                  {{ roleText(row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '启用' : '已禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginAt" label="最近登录" width="170" />
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleUser(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" @click="resetPwd(row)">重置口令</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!users.length && !loading" class="empty">暂无账号数据</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新增员工 -->
    <el-dialog v-model="formVisible" title="新增员工账号" width="600px">
      <el-form label-width="100px">
        <el-form-item label="登录账号">
          <el-input v-model="form.username" placeholder="至少 4 位，员工用于登录后台" />
        </el-form-item>
        <el-form-item label="初始口令">
          <el-input v-model="form.password" placeholder="留空则默认 123456" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio-button value="ADMIN">管理员</el-radio-button>
            <el-radio-button value="FRONTDESK">前台接待</el-radio-button>
            <el-radio-button value="HOUSEKEEPING">客房服务</el-radio-button>
          </el-radio-group>
          <div class="muted" style="margin-top:4px;line-height:1.7">
            管理员拥有全部权限；前台可办理预订、入住、退房与结算；客房服务主要使用房态图与打扫功能。
          </div>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="form.position" placeholder="如：前台接待 / 客房服务 / 值班经理" />
        </el-form-item>
        <el-form-item label="班次">
          <el-radio-group v-model="form.workShift">
            <el-radio-button value="白班">白班</el-radio-button>
            <el-radio-button value="夜班">夜班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.intro" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStaff">创建账号</el-button>
      </template>
    </el-dialog>

    <!-- 编辑员工档案 -->
    <el-dialog v-model="staffEditVisible" :title="`编辑员工：${staffForm.realName || ''}`" width="540px">
      <el-form label-width="100px">
        <el-form-item label="工号">
          <el-input :model-value="staffForm.staffNo" disabled />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="staffForm.position" />
        </el-form-item>
        <el-form-item label="班次">
          <el-radio-group v-model="staffForm.workShift">
            <el-radio-button value="白班">白班</el-radio-button>
            <el-radio-button value="夜班">夜班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="在岗状态">
          <el-radio-group v-model="staffForm.status">
            <el-radio-button value="ON_DUTY">在岗</el-radio-button>
            <el-radio-button value="REST">休假</el-radio-button>
            <el-radio-button value="LEFT">离职</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="staffForm.intro" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="staffEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStaffEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleText, staffApi, userApi } from '@/api'

const tab = ref('staff')
const loading = ref(false)
const submitting = ref(false)

const staff = ref([])
const users = ref([])

const query = reactive({ status: '', keyword: '' })
const formVisible = ref(false)
const staffEditVisible = ref(false)

const form = reactive({
  username: '', password: '', realName: '', role: 'FRONTDESK', phone: '',
  gender: '男', position: '前台接待', workShift: '白班', intro: ''
})

const staffForm = reactive({
  id: null, staffNo: '', position: '', workShift: '白班', status: 'ON_DUTY', intro: ''
})

async function load() {
  loading.value = true
  try {
    const res = await staffApi.list({
      status: query.status || undefined,
      keyword: query.keyword || undefined
    })
    const data = res.data
    staff.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await userApi.list({})
    const data = res.data
    users.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function loadAll() {
  load()
  loadUsers()
}

function onTabChange(name) {
  if (name === 'users' && !users.value.length) loadUsers()
}

function openForm() {
  Object.assign(form, {
    username: '', password: '', realName: '', role: 'FRONTDESK', phone: '',
    gender: '男', position: '前台接待', workShift: '白班', intro: ''
  })
  formVisible.value = true
}

async function submitStaff() {
  if (!form.username || form.username.trim().length < 4) {
    ElMessage.warning('登录账号至少 4 位')
    return
  }
  if (!form.realName) {
    ElMessage.warning('请填写员工姓名')
    return
  }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  submitting.value = true
  try {
    await staffApi.save({
      username: form.username,
      password: form.password || undefined,
      realName: form.realName,
      role: form.role,
      phone: form.phone || undefined,
      gender: form.gender,
      position: form.position,
      workShift: form.workShift,
      intro: form.intro || undefined
    })
    ElMessage.success('员工账号创建成功，初始口令为：' + (form.password || '123456'))
    formVisible.value = false
    loadAll()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function openStaffEdit(row) {
  Object.assign(staffForm, {
    id: row.id,
    staffNo: row.staffNo,
    position: row.position,
    workShift: row.workShift,
    status: row.status,
    intro: row.intro
  })
  staffEditVisible.value = true
}

async function submitStaffEdit() {
  submitting.value = true
  try {
    await staffApi.update(staffForm.id, {
      position: staffForm.position,
      workShift: staffForm.workShift,
      status: staffForm.status,
      intro: staffForm.intro
    })
    ElMessage.success('员工档案已更新')
    staffEditVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleUser(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${next === 1 ? '启用' : '禁用'}账号 ${row.username}（${row.realName}）？` +
        (next === 0 ? '禁用后该账号将无法登录。' : ''),
      '账号状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await userApi.changeStatus(row.id, next)
    ElMessage.success('账号状态已更新')
    loadUsers()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function resetPwd(row) {
  let newPassword = ''
  try {
    const res = await ElMessageBox.prompt(
      `为账号 ${row.username}（${row.realName}）设置新的登录口令（至少 6 位）：`,
      '重置口令',
      {
        inputPlaceholder: '留空则重置为 123456',
        inputValidator: (v) => (!v || v.length >= 6 ? true : '口令至少 6 位'),
        type: 'warning'
      }
    )
    newPassword = res.value
  } catch (e) {
    return
  }
  try {
    await userApi.resetPassword(row.id, newPassword || '123456')
    ElMessage.success('口令已重置为：' + (newPassword || '123456'))
    loadUsers()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

onMounted(loadAll)
</script>
