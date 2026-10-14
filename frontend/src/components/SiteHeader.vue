<template>
  <header class="site-header">
    <div class="inner">
      <span class="brand">城科酒店</span>
      <nav>
        <router-link to="/">首页</router-link>
        <router-link to="/rooms">房型与房价</router-link>
        <router-link to="/ai">AI 前台助手</router-link>
        <router-link v-if="auth.isGuest" to="/guest">客人中心</router-link>
        <router-link v-if="auth.isGuest" to="/guest/reservations">我的预订</router-link>
        <router-link v-if="auth.isGuest" to="/guest/bills">我的账单</router-link>
        <router-link v-if="auth.isStaff" to="/admin">管理后台</router-link>
      </nav>
      <div class="user-area">
        <template v-if="auth.isLogin">
          <span>你好，{{ auth.displayName }}</span>
          <el-button size="small" text style="color:#fff" @click="goCenter">我的</el-button>
          <el-button size="small" @click="logout">退出</el-button>
        </template>
        <template v-else>
          <el-button size="small" type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button size="small" @click="$router.push('/register')">客人注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const router = useRouter()

/** 「我的」按角色跳到各自的工作台 */
function goCenter() {
  if (auth.isStaff) router.push(auth.staffHome)
  else if (auth.isGuest) router.push('/guest')
  else router.push('/ai')
}

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>
