<template>
  <SiteHeader />
  <div class="page">
    <div class="page-title">客人中心</div>

    <div v-loading="loading">
      <div class="stat-grid" style="margin-bottom:16px">
        <div class="stat">
          <div class="label">客人编号 / 等级</div>
          <div class="value" style="font-size:16px">
            {{ guest.guestNo || '-' }} ·
            <el-tag size="small" :type="guestLevelTag(guest.memberLevel)">{{ guestLevelText(guest.memberLevel) }}</el-tag>
          </div>
        </div>
        <div class="stat">
          <div class="label">预存押金余额</div>
          <div class="value">￥{{ yuan(guest.balanceCents) }}</div>
        </div>
        <div class="stat">
          <div class="label">累计入住 / 累计消费</div>
          <div class="value" style="font-size:16px">
            {{ guest.totalStayCount ?? 0 }} 次 / ￥{{ yuan(guest.totalConsumeCents) }}
          </div>
        </div>
        <div class="stat">
          <div class="label">消费折扣</div>
          <div class="value" style="font-size:16px">{{ discountDesc }}</div>
        </div>
        <div class="stat">
          <div class="label">积分</div>
          <div class="value">{{ guest.points ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="label">状态</div>
          <div class="value" style="font-size:16px">
            <el-tag size="small" :type="guest.status === 'BLACKLIST' ? 'danger' : 'success'">
              {{ guest.status === 'BLACKLIST' ? '已列入黑名单' : '正常' }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="row">
        <div class="card grow">
          <div class="card-title">我的档案</div>
          <el-form label-width="90px" size="small">
            <el-form-item label="姓名">
              <el-input v-model="form.realName" disabled />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" disabled />
            </el-form-item>
            <el-form-item label="性别 / 生日">
              <el-input :model-value="`${guest.gender || '-'} / ${guest.birthday || '未填写'}`" disabled />
            </el-form-item>
            <el-form-item label="证件号">
              <el-input v-model="form.idCard" placeholder="补全证件号，入住时可直接核验" maxlength="18" />
            </el-form-item>
            <el-form-item label="联系地址">
              <el-input v-model="form.address" placeholder="如：重庆市沙坪坝区…" />
            </el-form-item>
            <el-form-item label="偏好备注">
              <el-input v-model="form.remark" type="textarea" :rows="2"
                        placeholder="如：无烟房、高楼层、荞麦枕（前台会看到该备注）" />
            </el-form-item>
            <el-button type="primary" size="small" :loading="saving" @click="saveProfile">保存档案</el-button>
          </el-form>
        </div>

        <div class="card grow">
          <div class="card-title">在线预存押金</div>
          <div class="muted" style="line-height:1.9;margin-bottom:10px">
            预存押金计入你的账户余额，办理入住时优先从余额扣除押金，退房结算后多余部分可退回余额。
            预存金额不可提现、不可转让，仅可用于本酒店消费。
          </div>
          <el-radio-group v-model="depositYuan">
            <el-radio-button :value="500">500 元</el-radio-button>
            <el-radio-button :value="1000">1000 元</el-radio-button>
            <el-radio-button :value="2000">2000 元</el-radio-button>
            <el-radio-button :value="3000">3000 元</el-radio-button>
          </el-radio-group>
          <div style="margin-top:12px">
            <el-input-number v-model="depositYuan" :min="100" :max="100000" :step="100" style="width:160px" />
            <span class="muted" style="margin-left:8px">元</span>
          </div>
          <div style="margin-top:12px">
            <el-button type="primary" :loading="depositing" @click="doDeposit">立即预存</el-button>
            <span class="muted" style="margin-left:10px">
              预存后余额预计为 ￥{{ yuan((guest.balanceCents || 0) + depositYuan * 100) }}
            </span>
          </div>
          <el-divider />
          <div class="muted">快捷入口</div>
          <div class="row" style="margin-top:8px">
            <el-button size="small" @click="$router.push('/rooms')">查房价</el-button>
            <el-button size="small" @click="$router.push('/guest/reservations')">我的预订</el-button>
            <el-button size="small" @click="$router.push('/guest/bills')">我的账单</el-button>
            <el-button size="small" @click="$router.push('/guest/transactions')">预存流水</el-button>
            <el-button size="small" @click="$router.push('/ai')">AI 助手</el-button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">最近流水</div>
        <el-table :data="transactions" size="small">
          <el-table-column prop="txNo" label="流水号" width="160" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.amountCents >= 0 ? 'success' : 'warning'">
                {{ txTypeText(row.txType) }}
              </el-tag>
            </template>
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
          <el-table-column prop="remark" label="说明" min-width="220" />
          <el-table-column prop="createdAt" label="时间" width="170" />
        </el-table>
        <div v-if="!transactions.length" class="empty">暂无流水记录</div>
        <div style="margin-top:10px">
          <el-button size="small" @click="$router.push('/guest/transactions')">查看全部流水</el-button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">最近订单</div>
        <el-table :data="orders" size="small">
          <el-table-column prop="reservationNo" label="预订单号" width="170" />
          <el-table-column prop="typeName" label="房型" width="130" />
          <el-table-column prop="roomNo" label="房间" width="90" />
          <el-table-column label="入住 / 离店" width="200">
            <template #default="{ row }">{{ row.checkInDate }} → {{ row.checkOutDate }}</template>
          </el-table-column>
          <el-table-column label="间夜" width="100">
            <template #default="{ row }">{{ row.roomCount }} 间 × {{ row.nights }} 晚</template>
          </el-table-column>
          <el-table-column label="房费总额" width="120">
            <template #default="{ row }"><span class="price">￥{{ yuan(row.totalCents) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="reservationStatusTag(row.status)">
                {{ reservationStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!orders.length" class="empty">暂无订单，可在「房型与房价」页查看房价后联系前台预订</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SiteHeader from '@/components/SiteHeader.vue'
import {
  discountText,
  guestApi,
  guestLevelTag,
  guestLevelText,
  reservationStatusTag,
  reservationStatusText,
  txTypeText,
  yuan
} from '@/api'

const loading = ref(false)
const saving = ref(false)
const depositing = ref(false)
const guest = ref({})
const transactions = ref([])
const orders = ref([])
const depositYuan = ref(1000)

const form = reactive({ idCard: '', address: '', remark: '' })

/** 会员折扣：等级决定房价试算时的折扣 */
const discountDesc = computed(() => {
  const level = guest.value.memberLevel
  if (level === 'GOLD') return discountText(90)
  if (level === 'SILVER') return discountText(95)
  return '无折扣（累计入住与消费可升级）'
})

async function load() {
  loading.value = true
  try {
    const res = await guestApi.profile()
    const data = res.data || {}
    guest.value = data.guest || data.profile || data
    transactions.value = data.recentTransactions || []
    orders.value = data.recentOrders || data.reservations || []
    form.idCard = guest.value.idCard || ''
    form.address = guest.value.address || ''
    form.remark = guest.value.remark || ''
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    await guestApi.updateProfile({ idCard: form.idCard, address: form.address, remark: form.remark })
    ElMessage.success('档案已保存，前台办理入住时会看到最新信息')
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function doDeposit() {
  if (!depositYuan.value || depositYuan.value <= 0) {
    ElMessage.warning('请输入预存金额')
    return
  }
  depositing.value = true
  try {
    const res = await guestApi.deposit({
      amountCents: depositYuan.value * 100,
      remark: '客人在线预存押金'
    })
    const data = res.data || {}
    ElMessage.success(`预存成功，当前余额 ￥${yuan(data.balanceAfter ?? (guest.value.balanceCents || 0) + depositYuan.value * 100)}`)
    load()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    depositing.value = false
  }
}

onMounted(load)
</script>
