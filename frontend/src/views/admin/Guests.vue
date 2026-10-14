<template>
  <div>
    <div class="page-title">客人管理</div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="姓名 / 手机号 / 客人编号 / 证件号"
                  style="width:260px" clearable @keyup.enter="search" @clear="search" />
        <el-select v-model="query.level" placeholder="全部等级" clearable style="width:140px" @change="search">
          <el-option label="普通" value="NORMAL" />
          <el-option label="白银" value="SILVER" />
          <el-option label="黄金" value="GOLD" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button type="success" @click="openForm()">新增客人</el-button>
        <el-button style="margin-left:auto" @click="load">刷新</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="guestNo" label="客人编号" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="gender" label="性别" width="70" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="证件号（脱敏）" width="180">
          <template #default="{ row }">{{ row.maskedIdCard || maskIdCard(row.idCard) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="guestLevelTag(row.memberLevel)">{{ guestLevelText(row.memberLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预存余额" width="120">
          <template #default="{ row }">￥{{ yuan(row.balanceCents) }}</template>
        </el-table-column>
        <el-table-column prop="totalStayCount" label="入住次数" width="100" />
        <el-table-column label="累计消费" width="120">
          <template #default="{ row }">￥{{ yuan(row.totalConsumeCents) }}</template>
        </el-table-column>
        <el-table-column prop="points" label="积分" width="90" />
        <el-table-column prop="address" label="联系地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'BLACKLIST' ? 'danger' : 'success'">
              {{ row.status === 'BLACKLIST' ? '黑名单' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openProfile(row)">档案</el-button>
            <el-button size="small" @click="openForm(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 'BLACKLIST' ? 'success' : 'danger'"
                       @click="toggleBlacklist(row)">
              {{ row.status === 'BLACKLIST' ? '解除拉黑' : '拉黑' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:12px" layout="total, prev, pager, next, sizes"
                     :total="total" :page-size="size" :current-page="page"
                     :page-sizes="[10, 20, 50]" @current-change="onPage" @size-change="onSizeChange" />
      <div v-if="!records.length && !loading" class="empty">没有符合条件的客人</div>
    </div>

    <!-- 新增 / 编辑客人 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑客人' : '新增客人'" width="600px">
      <el-form label-width="100px">
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号，用于检索与入住提醒" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="证件类型">
          <el-select v-model="form.idType" style="width:160px">
            <el-option label="身份证" value="身份证" />
            <el-option label="护照" value="护照" />
            <el-option label="驾驶证" value="驾驶证" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="form.idCard" maxlength="18" />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="会员等级">
          <el-radio-group v-model="form.memberLevel">
            <el-radio-button value="NORMAL">普通</el-radio-button>
            <el-radio-button value="SILVER">白银</el-radio-button>
            <el-radio-button value="GOLD">黄金</el-radio-button>
          </el-radio-group>
          <div class="muted" style="margin-top:4px">
            结算时系统也会按累计入住次数与消费金额自动升级等级。
          </div>
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2"
                    placeholder="如：过敏史、偏好无烟房、公司协议客户" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 客人档案 -->
    <el-dialog v-model="profileVisible" :title="`客人档案：${profile.guest?.realName || ''}`" width="860px" top="6vh">
      <div v-loading="profileLoading">
        <el-descriptions :column="3" border size="small" style="margin-bottom:14px">
          <el-descriptions-item label="客人编号">{{ profile.guest?.guestNo }}</el-descriptions-item>
          <el-descriptions-item label="等级">
            <el-tag size="small" :type="guestLevelTag(profile.guest?.memberLevel)">
              {{ guestLevelText(profile.guest?.memberLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ profile.guest?.status === 'BLACKLIST' ? '黑名单' : '正常' }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.guest?.phone }}</el-descriptions-item>
          <el-descriptions-item label="性别 / 生日">
            {{ profile.guest?.gender || '-' }} / {{ profile.guest?.birthday || '未填写' }}
          </el-descriptions-item>
          <el-descriptions-item label="证件号">
            {{ profile.guest?.maskedIdCard || maskIdCard(profile.guest?.idCard) }}
          </el-descriptions-item>
          <el-descriptions-item label="预存余额">￥{{ yuan(profile.guest?.balanceCents) }}</el-descriptions-item>
          <el-descriptions-item label="累计入住">{{ profile.guest?.totalStayCount ?? 0 }} 次</el-descriptions-item>
          <el-descriptions-item label="累计消费">￥{{ yuan(profile.guest?.totalConsumeCents) }}</el-descriptions-item>
          <el-descriptions-item label="联系地址" :span="3">{{ profile.guest?.address || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ profile.guest?.remark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-title">入住历史</div>
        <el-table :data="profile.stays || profile.history || []" size="small">
          <el-table-column prop="reservation_no" label="预订单号" width="170" />
          <el-table-column prop="type_name" label="房型" width="130" />
          <el-table-column prop="room_no" label="房间" width="90" />
          <el-table-column prop="check_in_date" label="入住日期" width="120" />
          <el-table-column prop="check_out_date" label="离店日期" width="120" />
          <el-table-column prop="nights" label="晚数" width="80" />
          <el-table-column label="房费" width="120">
            <template #default="{ row }">￥{{ yuan(row.total_cents ?? row.totalCents) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="reservationStatusTag(row.status)">
                {{ reservationStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!(profile.stays || profile.history || []).length" class="empty">暂无入住记录</div>

        <div class="card-title" style="margin-top:16px">预存与消费流水</div>
        <el-table :data="profile.transactions || []" size="small">
          <el-table-column prop="txNo" label="流水号" width="170" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ txTypeText(row.txType) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">
              <span :class="row.amountCents >= 0 ? 'income' : 'price'">
                {{ row.amountCents > 0 ? '+' : '' }}{{ yuan(row.amountCents) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">￥{{ yuan(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="100" />
          <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="170" />
        </el-table>
        <div v-if="!(profile.transactions || []).length" class="empty">暂无流水记录</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  guestLevelTag,
  guestLevelText,
  manageGuestApi,
  reservationStatusTag,
  reservationStatusText,
  txTypeText,
  yuan
} from '@/api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const submitting = ref(false)

const query = reactive({ keyword: '', level: '' })

const formVisible = ref(false)
const profileVisible = ref(false)
const profileLoading = ref(false)
const profile = ref({})

const form = reactive({
  id: null,
  realName: '',
  phone: '',
  gender: '男',
  idType: '身份证',
  idCard: '',
  birthday: '',
  memberLevel: 'NORMAL',
  address: '',
  remark: ''
})

/** 列表若未返回脱敏证件号，前端兜底做一次掩码展示 */
function maskIdCard(idCard) {
  if (!idCard) return '-'
  if (idCard.length < 8) return idCard
  return idCard.slice(0, 4) + '**********' + idCard.slice(-4)
}

async function load() {
  loading.value = true
  try {
    const res = await manageGuestApi.list({
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      page: page.value,
      size: size.value
    })
    const data = res.data || {}
    records.value = data.records || (Array.isArray(data) ? data : [])
    total.value = data.total ?? records.value.length
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPage(p) {
  page.value = p
  load()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  load()
}

function openForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      realName: row.realName,
      phone: row.phone,
      gender: row.gender || '男',
      idType: row.idType || '身份证',
      idCard: row.idCard || '',
      birthday: row.birthday || '',
      memberLevel: row.memberLevel || 'NORMAL',
      address: row.address || '',
      remark: row.remark || ''
    })
  } else {
    Object.assign(form, {
      id: null, realName: '', phone: '', gender: '男', idType: '身份证',
      idCard: '', birthday: '', memberLevel: 'NORMAL', address: '', remark: ''
    })
  }
  formVisible.value = true
}

async function submitForm() {
  if (!form.realName) {
    ElMessage.warning('请填写客人姓名')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone || '')) {
    ElMessage.warning('请填写正确的 11 位手机号')
    return
  }
  submitting.value = true
  try {
    await manageGuestApi.save({
      id: form.id || undefined,
      realName: form.realName,
      phone: form.phone,
      gender: form.gender,
      idType: form.idType,
      idCard: form.idCard || undefined,
      birthday: form.birthday || undefined,
      memberLevel: form.memberLevel,
      address: form.address || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success(form.id ? '客人信息已更新' : '客人登记成功')
    formVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function toggleBlacklist(row) {
  const next = row.status === 'BLACKLIST' ? 'NORMAL' : 'BLACKLIST'
  try {
    await ElMessageBox.confirm(
      next === 'BLACKLIST'
        ? `确认将客人 ${row.realName}（${row.guestNo}）列入黑名单？列入后无法办理预订。`
        : `确认解除客人 ${row.realName} 的黑名单状态？`,
      '客人状态变更',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await manageGuestApi.changeStatus(row.id, next)
    ElMessage.success(next === 'BLACKLIST' ? '已列入黑名单' : '已解除黑名单')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function openProfile(row) {
  profileVisible.value = true
  profileLoading.value = true
  profile.value = { guest: row }
  try {
    const res = await manageGuestApi.detail(row.id)
    profile.value = res.data || { guest: row }
  } catch (e) {
    profile.value = { guest: row }
  } finally {
    profileLoading.value = false
  }
}

onMounted(load)
</script>
