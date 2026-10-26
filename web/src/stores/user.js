import { defineStore } from 'pinia'

/**
 * 登录态：token、用户信息、权限码。
 * 权限码仅用于前端展示控制（隐藏按钮），真正的鉴权在后端，前端不具备安全边界。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lz_token') || '',
    username: localStorage.getItem('lz_username') || '',
    permissions: JSON.parse(localStorage.getItem('lz_permissions') || '[]')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    hasPermission: (state) => (code) => state.permissions.includes(code)
  },
  actions: {
    setLogin({ token, username, permissions }) {
      this.token = token
      this.username = username || ''
      this.permissions = permissions || []
      localStorage.setItem('lz_token', this.token)
      localStorage.setItem('lz_username', this.username)
      localStorage.setItem('lz_permissions', JSON.stringify(this.permissions))
    },
    logout() {
      this.token = ''
      this.username = ''
      this.permissions = []
      localStorage.removeItem('lz_token')
      localStorage.removeItem('lz_username')
      localStorage.removeItem('lz_permissions')
    }
  }
})
