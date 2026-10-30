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
    permissionCount: 0,
    // 额度（年假/调休）
    hasBalance: false,
    balanceYear: '',
    leaveQuota: '',
    leaveUsed: '',
    leaveLeft: '',
    compensLeft: ''
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 同步 tabBar 高亮（索引 4 = 我的）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(4)
    }
    this.render()
    // 拉一次最新 profile，顺带验证 token 是否仍有效
    const that = this
    // 额度不依赖 profile 请求结果，直接加载（上游失败不连累额度卡）
    this.loadBalance()
    api.get('/api/auth/profile')
      .then(function (data) {
        // 合并式更新：profile 接口可能缺字段（旧后端/网络降级），不能覆盖丢登录时的完整缓存
        const prev = auth.getProfile() || {}
        auth.setProfile({
          userId: data.userId != null ? data.userId : prev.userId,
          username: data.username != null ? data.username : prev.username,
          nickname: data.nickname != null ? data.nickname : prev.nickname,
          employeeId: data.employeeId != null ? data.employeeId : prev.employeeId,
          roles: (data.roles && data.roles.length) ? data.roles : (prev.roles || []),
          permissions: (data.permissions && data.permissions.length) ? data.permissions : (prev.permissions || [])
        })
        that.render()
      })
      .catch(function () { })
  },

  // 我的额度：加班折算的调休余额 + 年假额度（未绑定员工档案时不显示）
  loadBalance: function () {
    const profile = auth.getProfile() || {}
    if (!profile.employeeId) {
      console.warn('[profile] 额度卡隐藏：未绑定员工档案', profile)
      this.setData({ hasBalance: false })
      return
    }
    const that = this
    api.get('/api/applications/balances/my')
      .then(function (b) {
        console.info('[profile] 额度接口返回', b)
        if (!b || !b.year) {
          that.setData({ hasBalance: false })
          return
        }
        const num = function (v) {
          const n = Number(v)
          return isNaN(n) ? '0' : String(Math.round(n * 10) / 10)
        }
        that.setData({
          hasBalance: true,
          balanceYear: b.year,
          leaveQuota: num(b.leaveQuotaHours),
          leaveUsed: num(b.leaveUsedHours),
          leaveLeft: num(b.leaveAvailable),
          compensLeft: num(b.compensAvailable)
        })
      })
      .catch(function (err) {
        console.warn('[profile] 额度接口失败', err)
        that.setData({ hasBalance: false })
      })
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
