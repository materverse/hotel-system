<template>
  <SiteHeader />
  <div class="page" style="max-width:460px">
    <div class="card">
      <div class="card-title">登录城科酒店管理系统</div>
      <el-form :model="form" label-width="70px" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                    @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="submit">登录</el-button>
      </el-form>
      <div class="muted" style="margin-top:14px;line-height:1.9">
        演示账号（初始密码均为 123456）：<br />
        管理员 admin ｜ 前台 manager01 ｜ 客房服务 housekeeper01 ｜ 客人 guest01
      </div>
      <div style="margin-top:10px">
        还没有账号？<router-link to="/register">客人自助注册</router-link>
        <span class="muted" style="margin-left:8px">（员工账号由管理员在后台创建）</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect) {
      router.push(String(redirect))
    } else if (auth.isStaff) {
      router.push(auth.staffHome)
    } else if (auth.isGuest) {
      router.push('/guest')
    } else {
      router.push('/')
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>
