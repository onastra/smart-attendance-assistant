// 登录页：账号口令登录（与后台共用同一套账号体系与 JWT）
const api = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    username: '',
    password: '',
    loading: false
  },

  onUsernameInput: function (e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput: function (e) {
    this.setData({ password: e.detail.value })
  },

  doLogin: function () {
    const that = this
    const username = (this.data.username || '').trim()
    const password = this.data.password || ''
    if (!username || !password) {
      wx.showToast({ title: '请输入账号和口令', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    api.post('/api/auth/login', { username: username, password: password })
      .then(function (data) {
        auth.setToken(data.token)
        auth.setProfile({
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          employeeId: data.employeeId,
          roles: data.roles || [],
          permissions: data.permissions || []
        })
        wx.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(function () {
          wx.switchTab({ url: '/pages/index/index' })
        }, 500)
      })
      .catch(function () {
        // 错误提示已由 request 统一弹出
      })
      .then(function () {
        that.setData({ loading: false })
      })
  },

  fillDemo: function (e) {
    const role = e.currentTarget.dataset.role
    if (role === 'admin') {
      this.setData({ username: 'admin', password: 'Admin@123' })
    } else if (role === 'manager') {
      this.setData({ username: 'manager01', password: 'Admin@123' })
    } else {
      this.setData({ username: 'LZ0001', password: 'Emp@123' })
    }
  }
})
