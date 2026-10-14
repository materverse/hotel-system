import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一的 axios 实例：自动携带登录令牌，集中处理错误码
const http = axios.create({
  baseURL: '/api',
  timeout: 90000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('hotel_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 令牌失效统一处理：清理登录态并回到登录页，避免停留在调用失败的页面上 */
function handleUnauthorized() {
  localStorage.removeItem('hotel_token')
  localStorage.removeItem('hotel_user')
  if (window.location.pathname !== '/login') {
    const from = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.replace(`/login?redirect=${from}`)
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 200) {
      if (body.code === 401) {
        handleUnauthorized()
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body
  },
  (error) => {
    const body = error.response && error.response.data
    const status = error.response && error.response.status
    const message = (body && body.message) || error.message || '网络异常，请稍后重试'
    if (status === 401 || (body && body.code === 401)) {
      ElMessage.error('登录已过期，请重新登录')
      handleUnauthorized()
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default http

/** 认证相关接口 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
  changePassword: (data) => http.put('/auth/password', data)
}

/** 公共查询接口（免登录） */
export const publicApi = {
  roomTypes: (params) => http.get('/public/room-types', { params }),
  rooms: (params) => http.get('/public/rooms', { params }),
  overview: () => http.get('/public/overview'),
  quote: (params) => http.get('/public/quote', { params })
}

/** 客人自助接口（需登录，role=GUEST） */
export const guestApi = {
  profile: () => http.get('/me/profile'),
  updateProfile: (data) => http.put('/me/profile', data),
  deposit: (data) => http.post('/me/deposit', data),
  transactions: (params) => http.get('/me/transactions', { params }),
  reservations: (params) => http.get('/me/reservations', { params }),
  // 客人的账单列表（后端接口为 /api/me/bills，与员工端的 /api/bill/list 区分）
  bills: (params) => http.get('/me/bills', { params })
}

/** 前台业务接口（前台 / 客房 / 管理员） */
export const frontApi = {
  // 当日看板
  board: () => http.get('/front/board'),
  // 客人检索与登记
  guests: (params) => http.get('/front/guests', { params }),
  createGuest: (data) => http.post('/front/guests', data),
  // 房价试算
  quote: (params) => http.get('/front/quote', { params }),
  // 预订
  createReservation: (data) => http.post('/front/reservations', data),
  checkin: (id, data) => http.post(`/front/reservations/${id}/checkin`, data),
  checkout: (id) => http.post(`/front/reservations/${id}/checkout`),
  cancelReservation: (id, data) => http.post(`/front/reservations/${id}/cancel`, data),
  availableRooms: (params) => http.get('/front/rooms/available', { params }),
  // 账单操作
  addConsume: (billId, data) => http.post(`/front/bills/${billId}/consume`, data),
  addDiscount: (billId, data) => http.post(`/front/bills/${billId}/discount`, data),
  settle: (billId, data) => http.post(`/front/bills/${billId}/settle`, data)
}

/** 客房管理接口（前台 / 客房 / 管理员） */
export const roomApi = {
  list: (params) => http.get('/room/list', { params }),
  save: (data) => http.post('/room/save', data),
  changeStatus: (id, data) => http.put(`/room/${id}/status`, data),
  clean: (id) => http.post(`/room/clean/${id}`),
  types: (params) => http.get('/room/types', { params }),
  saveType: (data) => http.post('/room/types/save', data),
  ratePlans: (params) => http.get('/room/rate-plans', { params }),
  saveRatePlan: (data) => http.post('/room/rate-plans/save', data)
}

/** 预订管理接口（前台 / 管理员） */
export const reservationApi = {
  list: (params) => http.get('/reservation/list', { params }),
  detail: (id) => http.get(`/reservation/${id}`),
  noShow: (id) => http.post(`/reservation/${id}/noshow`)
}

/** 客人管理接口（前台 / 管理员） */
export const manageGuestApi = {
  list: (params) => http.get('/guest/list', { params }),
  detail: (id) => http.get(`/guest/${id}`),
  save: (data) => http.post('/guest/save', data),
  changeStatus: (id, status) => http.put(`/guest/${id}/status`, { status })
}

/** 账单接口（前台 / 管理员） */
export const billApi = {
  list: (params) => http.get('/bill/list', { params }),
  detail: (id) => http.get(`/bill/${id}`),
  byReservation: (reservationId) => http.get(`/bill/by-reservation/${reservationId}`),
  consumeItems: (params) => http.get('/bill/consume-items', { params }),
  saveConsumeItem: (data) => http.post('/bill/consume-items/save', data)
}

/** 房价策略接口（前台 / 管理员）：后端实际路径在 /api/room/rate-plans 下 */
export const rateApi = {
  list: (params) => http.get('/room/rate-plans', { params }),
  save: (data) => http.post('/room/rate-plans/save', data),
  changeStatus: (id, status) => http.put(`/room/rate-plans/${id}/status`, { status })
}

/** 员工与权限接口（管理员） */
export const staffApi = {
  list: (params) => http.get('/staff/list', { params }),
  save: (data) => http.post('/staff/save', data),
  update: (id, data) => http.put(`/staff/${id}`, data)
}

/** 统计报表接口（管理员） */
export const statsApi = {
  dashboard: () => http.get('/stats/dashboard'),
  finance: (params) => http.get('/stats/finance', { params }),
  room: () => http.get('/stats/room'),
  logs: (params) => http.get('/stats/logs', { params }),
  ai: (params) => http.get('/stats/ai', { params })
}

/** AI 知识库管理接口（管理员） */
export const kbApi = {
  docs: (params) => http.get('/kb/docs', { params }),
  create: (data) => http.post('/kb/docs', data),
  update: (id, data) => http.put(`/kb/docs/${id}`, data),
  remove: (id) => http.delete(`/kb/docs/${id}`),
  reindex: () => http.post('/kb/reindex'),
  search: (params) => http.get('/kb/search', { params })
}

/** AI 能力接口 */
export const aiApi = {
  ask: (scene, data) => http.post('/ai/ask', data, { params: { scene } }),
  generateQuote: (data) => http.post('/ai/quote/generate', data),
  confirmQuote: (id, approved) => http.post(`/ai/quote/${id}/confirm`, { approved }),
  quotes: (params) => http.get('/ai/quote/list', { params }),
  conversations: () => http.get('/ai/conversations'),
  messages: (convNo) => http.get(`/ai/conversations/${convNo}/messages`),
  removeConversation: (convNo) => http.delete(`/ai/conversations/${convNo}`),
  status: () => http.get('/ai/status'),
  evalCases: () => http.get('/ai/eval/cases'),
  runEval: () => http.get('/ai/eval/run')
}

/** 用户账号接口（管理员） */
export const userApi = {
  list: (params) => http.get('/users/list', { params }),
  changeStatus: (id, status) => http.put(`/users/${id}/status`, { status }),
  resetPassword: (id, newPassword) => http.put(`/users/${id}/password`, { newPassword })
}

// ------------------------------------------------------------------
// 通用字典与格式化工具（各页面共用，避免重复定义）
// ------------------------------------------------------------------

/** 金额格式化：分 → 元 */
export function yuan(cents) {
  if (cents === null || cents === undefined || cents === '') return '0.00'
  const n = Number(cents)
  if (Number.isNaN(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/** 元 → 分（表单提交用） */
export function toCents(yuanValue) {
  const n = Number(yuanValue)
  if (Number.isNaN(n)) return 0
  return Math.round(n * 100)
}

/** 房态文案 */
export function roomStatusText(status) {
  return {
    FREE: '空闲',
    OCCUPIED: '已入住',
    CLEANING: '待打扫',
    MAINTENANCE: '维修',
    BOOKED: '已预订'
  }[status] || status || '-'
}

/** 房态标签颜色 */
export function roomStatusTag(status) {
  return {
    FREE: 'success',
    OCCUPIED: 'primary',
    CLEANING: 'warning',
    MAINTENANCE: 'info',
    BOOKED: 'danger'
  }[status] || 'info'
}

/** 预订单状态文案 */
export function reservationStatusText(status) {
  return {
    BOOKED: '已预订',
    CHECKED_IN: '已入住',
    CHECKED_OUT: '已退房',
    CANCELLED: '已取消',
    NO_SHOW: '未到店'
  }[status] || status || '-'
}

/** 预订单状态标签颜色 */
export function reservationStatusTag(status) {
  return {
    BOOKED: 'warning',
    CHECKED_IN: 'success',
    CHECKED_OUT: 'info',
    CANCELLED: 'danger',
    NO_SHOW: 'danger'
  }[status] || 'info'
}

/** 账单状态文案 */
export function billStatusText(status) {
  return { UNSETTLED: '待结算', SETTLED: '已结算', VOID: '已作废' }[status] || status || '-'
}

/** 账单状态标签颜色 */
export function billStatusTag(status) {
  return { UNSETTLED: 'warning', SETTLED: 'success', VOID: 'info' }[status] || 'info'
}

/** 支付方式文案 */
export function payTypeText(payType) {
  return { CASH: '现金', CARD: '刷卡', ONLINE: '线上支付', BALANCE: '预存余额' }[payType] || payType || '-'
}

/** 客人等级文案 */
export function guestLevelText(level) {
  return { GOLD: '黄金', SILVER: '白银', NORMAL: '普通' }[level] || level || '普通'
}

/** 客人等级标签颜色 */
export function guestLevelTag(level) {
  return { GOLD: 'danger', SILVER: 'warning', NORMAL: 'info' }[level] || 'info'
}

/** 会员折扣（百分数 → 中文折扣文案） */
export function discountText(rate) {
  if (rate === null || rate === undefined || rate === '' || rate === 100) return '无折扣'
  return (Number(rate) / 10).toFixed(1) + ' 折'
}

/** 预订渠道文案 */
export function channelText(channel) {
  return { FRONT_DESK: '前台', APP: '自助/APP', PHONE: '电话', WALK_IN: '散客' }[channel] || channel || '-'
}

/** 流水类型文案 */
export function txTypeText(txType) {
  return { DEPOSIT: '预存', CONSUME: '消费', REFUND: '退款', ADJUST: '调整' }[txType] || txType || '-'
}

/** 角色文案 */
export function roleText(role) {
  return {
    ADMIN: '管理员',
    FRONTDESK: '前台接待',
    HOUSEKEEPING: '客房服务',
    GUEST: '客人'
  }[role] || role || '-'
}

/** 日期格式化：今天 / 明天 等，用于默认日期选择 */
export function formatDate(date) {
  const d = date instanceof Date ? date : new Date(date)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 今天日期字符串 */
export function today() {
  return formatDate(new Date())
}

/** 在给定日期上加 n 天 */
export function addDays(dateStr, n) {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + n)
  return formatDate(d)
}
