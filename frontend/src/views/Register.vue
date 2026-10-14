<template>
  <SiteHeader />
  <div class="page" style="max-width:560px">
    <div class="card">
      <div class="card-title">客人自助注册</div>
      <div class="muted" style="margin-bottom:14px;line-height:1.8">
        注册后将自动为你建立客人档案与客人编号，可直接享受房价试算与会员折扣、在线预存押金、
        查看自己的预订与账单。员工账号请联系管理员在后台创建。
      </div>
      <el-form :model="form" label-width="90px" @submit.prevent>
        <el-form-item label="登录账号">
          <el-input v-model="form.username" placeholder="至少 4 位，字母或数字" />
        </el-form-item>
        <el-form-item label="登录密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="与证件一致的真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号，用于接收入住提醒" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="form.idCard" maxlength="18" placeholder="身份证号（入住时需出示原件）" />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择生日" style="width:100%" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="submit">提交注册</el-button>
      </el-form>
      <div style="margin-top:12px">
        已有账号？<router-link to="/login">直接登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import { useAuthStore } from '@/stores'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const confirmPassword = ref('')
const form = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  gender: '男',
  idCard: '',
  birthday: ''
})

/** 提交前做一次前端基础校验，减少无意义的请求 */
function validate() {
  if (!form.username || form.username.trim().length < 4) {
    ElMessage.warning('登录账号至少 4 位')
    return false
  }
  if (!form.password || form.password.length < 6) {
    ElMessage.warning('登录密码至少 6 位')
    return false
  }
  if (form.password !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  if (!form.realName || !form.realName.trim()) {
    ElMessage.warning('请填写真实姓名')
    return false
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请填写正确的 11 位手机号')
    return false
  }
  return true
}

async function submit() {
  if (!validate()) return
  loading.value = true
  try {
    await auth.register({ ...form })
    ElMessage.success('注册成功，已自动登录')
    router.push('/guest')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>
