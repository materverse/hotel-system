import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/Register.vue') },
  { path: '/rooms', name: 'rooms', component: () => import('@/views/Rooms.vue') },
  { path: '/ai', name: 'ai', component: () => import('@/views/AiAssistant.vue') },

  // ---------------- 客人自助（role=GUEST） ----------------
  {
    path: '/guest',
    name: 'guest-center',
    meta: { auth: true, role: ['GUEST', 'ADMIN'] },
    component: () => import('@/views/guest/GuestCenter.vue')
  },
  {
    path: '/guest/reservations',
    name: 'guest-reservations',
    meta: { auth: true, role: ['GUEST', 'ADMIN'] },
    component: () => import('@/views/guest/MyReservations.vue')
  },
  {
    path: '/guest/bills',
    name: 'guest-bills',
    meta: { auth: true, role: ['GUEST', 'ADMIN'] },
    component: () => import('@/views/guest/MyBills.vue')
  },
  {
    path: '/guest/transactions',
    name: 'guest-transactions',
    meta: { auth: true, role: ['GUEST', 'ADMIN'] },
    component: () => import('@/views/guest/Transactions.vue')
  },

  // ---------------- 管理端（员工） ----------------
  {
    path: '/admin',
    meta: { auth: true, role: ['ADMIN', 'FRONTDESK', 'HOUSEKEEPING'] },
    component: () => import('@/views/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/board' },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/Dashboard.vue')
      },
      { path: 'board', name: 'admin-board', component: () => import('@/views/admin/Board.vue') },
      {
        path: 'reservations',
        name: 'admin-reservations',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/Reservations.vue')
      },
      { path: 'rooms', name: 'admin-rooms', component: () => import('@/views/admin/Rooms.vue') },
      {
        path: 'guests',
        name: 'admin-guests',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/Guests.vue')
      },
      {
        path: 'bills',
        name: 'admin-bills',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/Bills.vue')
      },
      {
        path: 'consume-items',
        name: 'admin-consume-items',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/ConsumeItems.vue')
      },
      {
        path: 'rate-plans',
        name: 'admin-rate-plans',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/RatePlans.vue')
      },
      {
        path: 'finance',
        name: 'admin-finance',
        meta: { role: ['ADMIN'] },
        component: () => import('@/views/admin/Finance.vue')
      },
      {
        path: 'room-stats',
        name: 'admin-room-stats',
        meta: { role: ['ADMIN', 'FRONTDESK'] },
        component: () => import('@/views/admin/RoomStats.vue')
      },
      {
        path: 'knowledge',
        name: 'admin-knowledge',
        meta: { role: ['ADMIN'] },
        component: () => import('@/views/admin/Knowledge.vue')
      },
      {
        path: 'ai-eval',
        name: 'admin-ai-eval',
        meta: { role: ['ADMIN'] },
        component: () => import('@/views/admin/AiEval.vue')
      },
      {
        path: 'users',
        name: 'admin-users',
        meta: { role: ['ADMIN'] },
        component: () => import('@/views/admin/Users.vue')
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/** 判断角色是否命中 meta.role（支持字符串与数组两种写法） */
function roleMatched(role, expected) {
  if (!expected) return true
  if (Array.isArray(expected)) return expected.includes(role)
  return role === expected
}

// 路由守卫：未登录跳登录页，角色不符回到首页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('hotel_token')
  const user = JSON.parse(localStorage.getItem('hotel_user') || 'null')
  if (to.meta.auth && !token) {
    ElMessage.warning('请先登录')
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta.role && user && !roleMatched(user.role, to.meta.role)) {
    ElMessage.warning('当前账号无权访问该页面')
    next('/')
    return
  }
  next()
})

export default router
