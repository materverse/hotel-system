<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <div class="logo">城科酒店 · 管理后台</div>

      <template v-for="group in visibleGroups" :key="group.title">
        <div class="group">{{ group.title }}</div>
        <router-link v-for="item in group.items" :key="item.path" :to="item.path">
          {{ item.title }}
        </router-link>
      </template>

      <div class="group">前台</div>
      <router-link to="/">返回前台首页</router-link>
    </aside>

    <main class="admin-main">
      <div class="toolbar">
        <span class="muted">
          当前登录：{{ auth.displayName }}（{{ roleText(auth.role) }}）
          ｜ 今天是 {{ todayText }}
        </span>
        <el-button size="small" style="margin-left:auto" @click="changePassword">修改口令</el-button>
        <el-button size="small" @click="logout">退出登录</el-button>
      </div>
      <router-view />
    </main>

    <!-- 修改自己的登录口令 -->
    <el-dialog v-model="pwdVisible" title="修改登录口令" width="420px">
      <el-form label-width="90px">
        <el-form-item label="原口令">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新口令">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新口令">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, today } from '@/api'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const router = useRouter()

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const todayText = today()

// 菜单按角色过滤：客房服务只保留房态相关，前台去掉管理员专属项
const groups = [
  {
    title: '前台业务',
    items: [
      { title: '前台看板', path: '/admin/board', roles: ['ADMIN', 'FRONTDESK', 'HOUSEKEEPING'] },
      { title: '预订管理', path: '/admin/reservations', roles: ['ADMIN', 'FRONTDESK'] },
      { title: '客人管理', path: '/admin/guests', roles: ['ADMIN', 'FRONTDESK'] },
      { title: '账单结算', path: '/admin/bills', roles: ['ADMIN', 'FRONTDESK'] }
    ]
  },
  {
    title: '客房与房价',
    items: [
      { title: '客房管理', path: '/admin/rooms', roles: ['ADMIN', 'FRONTDESK', 'HOUSEKEEPING'] },
      { title: '房价策略', path: '/admin/rate-plans', roles: ['ADMIN', 'FRONTDESK'] },
      { title: '消费项目', path: '/admin/consume-items', roles: ['ADMIN', 'FRONTDESK'] }
    ]
  },
  {
    title: '统计报表',
    items: [
      { title: '数据概览', path: '/admin/dashboard', roles: ['ADMIN', 'FRONTDESK'] },
      { title: '财务报表', path: '/admin/finance', roles: ['ADMIN'] },
      { title: '房态统计', path: '/admin/room-stats', roles: ['ADMIN', 'FRONTDESK'] }
    ]
  },
  {
    title: 'AI 与权限',
    items: [
      { title: 'AI 知识库', path: '/admin/knowledge', roles: ['ADMIN'] },
      { title: 'AI 评测', path: '/admin/ai-eval', roles: ['ADMIN'] },
      { title: '员工与权限', path: '/admin/users', roles: ['ADMIN'] }
    ]
  }
]

const visibleGroups = computed(() =>
  groups
    .map((g) => ({ ...g, items: g.items.filter((i) => i.roles.includes(auth.role)) }))
    .filter((g) => g.items.length)
)

function roleText(role) {
  return { ADMIN: '管理员', FRONTDESK: '前台接待', HOUSEKEEPING: '客房服务', GUEST: '客人' }[role] || role || '-'
}

function changePassword() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function submitPassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原口令与新口令')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新口令至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新口令不一致')
    return
  }
  pwdSaving.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('口令已修改，请重新登录')
    pwdVisible.value = false
    await auth.logout()
    router.push('/login')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    pwdSaving.value = false
  }
}

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>
