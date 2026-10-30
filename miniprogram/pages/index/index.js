// 首页：问候 + 扫码直扫 + 最近打卡概览 + 常用功能入口
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')
const checkin = require('../../utils/checkin')

Page({
  data: {
    greeting: '你好',
    nickname: '',
    username: '',
    roleText: '',
    avatarChar: '联',
    canApprove: false,
    isAdmin: false,
    isEmployee: true,
    lastRecord: null,
    lastRecordText: '',
    lastResultMeta: null
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 同步 tabBar 高亮（索引 0 = 首页）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(0)
    }
    this.renderProfile()
    this.loadLastRecord()
  },

  renderProfile: function () {
    const profile = auth.getProfile() || {}
    const roles = profile.roles || []
    const hour = new Date().getHours()
    let greeting = '你好'
    if (hour < 6) greeting = '夜深了'
    else if (hour < 12) greeting = '上午好'
    else if (hour < 14) greeting = '中午好'
    else if (hour < 18) greeting = '下午好'
    else greeting = '晚上好'
    const nickname = profile.nickname || profile.username || '同学'
    this.setData({
      greeting: greeting,
      nickname: nickname,
      username: profile.username || '',
      roleText: roles.length > 0 ? roles.join(' / ') : '员工',
      avatarChar: (nickname || '联').slice(0, 1),
      canApprove: auth.hasPermission('application:approve'),
      isAdmin: auth.hasPermission('statistics:view'),
      // 未绑定员工档案（管理员等）不打卡也没有个人考勤：隐藏扫码 CTA、状态条与"我的考勤"
      isEmployee: !!profile.employeeId
    })
  },

  loadLastRecord: function () {
    // 管理员等未绑定档案的账号没有"最近打卡"，不发无效请求
    if (!(auth.getProfile() || {}).employeeId) return
    const that = this
    api.get('/api/my/attendance/records', { pageNum: 1, pageSize: 1 })
      .then(function (data) {
        const list = (data.page && data.page.list) || []
        if (list.length === 0) {
          that.setData({ lastRecord: null, lastRecordText: '', lastResultMeta: null })
          return
        }
        const r = list[0]
        const meta = fmt.resultMeta(r.result, r.resultText)
        that.setData({
          lastRecord: r,
          lastRecordText: fmt.shortTime(r.checkinTime) + ' · ' + (r.checkinTypeText || '') + ' · ' + meta.text,
          lastResultMeta: meta
        })
      })
      .catch(function () {
        // 请求失败提示已统一弹出
      })
  },

  // 首页直扫：摄像头直接拉起 → 确认页 → 结果页（结果不再用浮层，统一走独立状态页）
  scanCheckin: function () {
    checkin.scanThenConfirm({})
  },

  // 摄像头不可用/二维码破损时的兜底入口
  goManual: function () {
    wx.switchTab({ url: '/pages/checkin/checkin' })
  },

  goRecords: function () {
    wx.switchTab({ url: '/pages/records/records' })
  },

  goApply: function () {
    wx.navigateTo({ url: '/pages/apply/apply' })
  },

  goApplications: function () {
    wx.navigateTo({ url: '/pages/applications/applications?tab=mine' })
  },

  goTodo: function () {
    wx.navigateTo({ url: '/pages/applications/applications?tab=todo' })
  },

  goAssistant: function () {
    wx.switchTab({ url: '/pages/assistant/assistant' })
  },

  goDashboard: function () {
    wx.navigateTo({ url: '/pages/dashboard/dashboard' })
  }
})
