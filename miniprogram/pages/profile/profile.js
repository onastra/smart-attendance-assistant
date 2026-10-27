// 我的：登录信息、后端地址、退出登录
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const config = require('../../config')

Page({
  data: {
    nickname: '',
    username: '',
    roleText: '',
    employeeBound: true,
    baseUrl: config.BASE_URL,
    permissionCount: 0
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.render()
    // 拉一次最新 profile，顺带验证 token 是否仍有效
    const that = this
    api.get('/api/auth/profile')
      .then(function (data) {
        auth.setProfile({
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          employeeId: data.employeeId,
          roles: (auth.getProfile() || {}).roles || [],
          permissions: data.permissions || []
        })
        that.render()
      })
      .catch(function () { })
  },

  render: function () {
    const profile = auth.getProfile() || {}
    const roles = profile.roles || []
    this.setData({
      nickname: profile.nickname || profile.username || '-',
      username: profile.username || '-',
      roleText: roles.length > 0 ? roles.join(' / ') : '-',
      employeeBound: !!profile.employeeId,
      permissionCount: (profile.permissions || []).length
    })
  },

  logout: function () {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号？',
      success: function (res) {
        if (!res.confirm) return
        auth.clear()
        wx.reLaunch({ url: '/pages/login/login' })
      }
    })
  }
})
