// 首页：功能入口 + 我的考勤概览（当天最近一条）
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

Page({
  data: {
    nickname: '',
    username: '',
    roleText: '',
    canApprove: false,
    lastRecord: null,
    lastRecordText: ''
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.renderProfile()
    this.loadLastRecord()
  },

  renderProfile: function () {
    const profile = auth.getProfile() || {}
    const roles = profile.roles || []
    this.setData({
      nickname: profile.nickname || profile.username || '同学',
      username: profile.username || '',
      roleText: roles.length > 0 ? roles.join(' / ') : '员工',
      canApprove: auth.hasPermission('application:approve')
    })
  },

  loadLastRecord: function () {
    const that = this
    api.get('/api/my/attendance/records', { pageNum: 1, pageSize: 1 })
      .then(function (data) {
        const list = (data.page && data.page.list) || []
        if (list.length === 0) {
          that.setData({ lastRecord: null, lastRecordText: '今天还没有打卡记录' })
          return
        }
        const r = list[0]
        const meta = fmt.resultMeta(r.result, r.resultText)
        that.setData({
          lastRecord: r,
          lastRecordText: fmt.shortTime(r.checkinTime) + ' · ' + (r.checkinTypeText || '') + ' · ' + meta.text
        })
      })
      .catch(function () {
        // 请求失败提示已统一弹出
      })
  },

  goCheckin: function () {
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
  }
})
