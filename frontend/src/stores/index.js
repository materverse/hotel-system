import { defineStore } from 'pinia'
import { authApi } from '@/api'

/** 登录状态：保存在 localStorage，刷新后仍保持登录 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('hotel_token') || '',
    user: JSON.parse(localStorage.getItem('hotel_user') || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => (state.user ? state.user.role : ''),
    isAdmin: (state) => !!state.user && state.user.role === 'ADMIN',
    isGuest: (state) => !!state.user && state.user.role === 'GUEST',
    isFrontDesk: (state) => !!state.user && state.user.role === 'FRONTDESK',
    isHousekeeping: (state) => !!state.user && state.user.role === 'HOUSEKEEPING',
    /** 是否属于员工端（前台 / 客房 / 管理员） */
    isStaff: (state) =>
      !!state.user && ['ADMIN', 'FRONTDESK', 'HOUSEKEEPING'].includes(state.user.role),
    guestId: (state) => (state.user ? state.user.guestId : null),
    staffId: (state) => (state.user ? state.user.staffId : null),
    displayName: (state) => (state.user ? state.user.realName || state.user.username : ''),
    /** 员工端默认落地页 */
    staffHome: (state) => {
      if (!state.user) return '/login'
      return state.user.role === 'HOUSEKEEPING' ? '/admin/rooms' : '/admin/board'
    }
  },
  actions: {
    /** 登录成功后写入令牌与用户信息；登录返回体既支持扁平结构也支持嵌套 user 结构 */
    applyLogin(data) {
      const info = data && data.user ? { ...data.user, ...data } : { ...data }
      this.token = data.token
      this.user = info
      localStorage.setItem('hotel_token', this.token)
      localStorage.setItem('hotel_user', JSON.stringify(this.user))
    },
    async login(form) {
      const res = await authApi.login(form)
      this.applyLogin(res.data || {})
      return res
    },
    async register(form) {
      const res = await authApi.register(form)
      this.applyLogin(res.data || {})
      return res
    },
    /** 用后端返回的最新身份信息刷新本地缓存 */
    async refreshMe() {
      try {
        const res = await authApi.me()
        if (res.data) {
          this.user = { ...this.user, ...res.data }
          localStorage.setItem('hotel_user', JSON.stringify(this.user))
        }
      } catch (e) {
        // 令牌失效时由拦截器统一处理，这里忽略
      }
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 令牌已失效时忽略
      }
      this.token = ''
      this.user = null
      localStorage.removeItem('hotel_token')
      localStorage.removeItem('hotel_user')
    }
  }
})
