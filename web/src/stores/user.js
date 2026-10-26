import { defineStore } from 'pinia'

/**
 * 登录态：token、用户信息、角色与权限码。
 *
 * <p><strong>边界说明</strong>：权限码只用于前端展示控制（隐藏按钮、隐藏菜单项），
 * 不构成任何安全边界。真正的鉴权在后端拦截器中完成，
 * 前端即便绕开隐藏逻辑直接请求接口，也会被后端拒绝（已实测返回 40301）。</p>
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lz_token') || '',
    userId: localStorage.getItem('lz_user_id') || '',
    username: localStorage.getItem('lz_username') || '',
    nickname: localStorage.getItem('lz_nickname') || '',
    employeeId: localStorage.getItem('lz_employee_id') || '',
    roles: JSON.parse(localStorage.getItem('lz_roles') || '[]'),
    permissions: JSON.parse(localStorage.getItem('lz_permissions') || '[]')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    displayName: (state) => state.nickname || state.username || '未登录',
    hasPermission: (state) => (code) => state.permissions.includes(code),
    hasRole: (state) => (code) => state.roles.includes(code)
  },
  actions: {
    setLogin(payload) {
      this.token = payload.token || ''
      this.userId = payload.userId || ''
      this.username = payload.username || ''
      this.nickname = payload.nickname || ''
      this.employeeId = payload.employeeId || ''
      this.roles = payload.roles || []
      this.permissions = payload.permissions || []
      localStorage.setItem('lz_token', this.token)
      localStorage.setItem('lz_user_id', this.userId)
      localStorage.setItem('lz_username', this.username)
      localStorage.setItem('lz_nickname', this.nickname)
      localStorage.setItem('lz_employee_id', this.employeeId)
      localStorage.setItem('lz_roles', JSON.stringify(this.roles))
      localStorage.setItem('lz_permissions', JSON.stringify(this.permissions))
    },
    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.employeeId = ''
      this.roles = []
      this.permissions = []
      localStorage.removeItem('lz_token')
      localStorage.removeItem('lz_user_id')
      localStorage.removeItem('lz_username')
      localStorage.removeItem('lz_nickname')
      localStorage.removeItem('lz_employee_id')
      localStorage.removeItem('lz_roles')
      localStorage.removeItem('lz_permissions')
    }
  }
})
