// 打卡 tab：类型选择 + 直扫（进确认页）+ 手动输入兜底（同样进确认页，校验链一致）
// 打卡结果不在这里展示，统一由 pages/checkin-result/result 呈现
const checkin = require('../../utils/checkin')
const auth = require('../../utils/auth')

Page({
  data: {
    typeIndex: 0,
    typeLabels: checkin.TYPE_OPTIONS.map(function (o) { return o.short }),
    typeFullLabels: checkin.TYPE_OPTIONS.map(function (o) { return o.label }),
    manualContent: ''
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 管理员等未绑定档案的账号不打卡：入口已隐藏，这里兜底拦截直开 URL 的情况
    if (!(auth.getProfile() || {}).employeeId) {
      wx.showToast({ title: '管理员账号无需打卡', icon: 'none' })
      setTimeout(function () { wx.switchTab({ url: '/pages/index/index' }) }, 1200)
      return
    }
    // 同步 tabBar 高亮（索引 2 = 打卡）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(2)
    }
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.currentTarget.dataset.index) })
  },

  // 扫码：直接进确认页，选好的类型会带过去
  scanAndCheckin: function () {
    checkin.scanThenConfirm({ typeIndex: this.data.typeIndex })
  },

  onManualInput: function (e) {
    this.setData({ manualContent: e.detail.value })
  },

  // 手动粘贴：同样先预校验再确认，不跳过校验链
  submitManual: function () {
    const content = (this.data.manualContent || '').trim()
    if (!content) {
      wx.showToast({ title: '请粘贴二维码内容', icon: 'none' })
      return
    }
    checkin.openConfirm(content, this.data.typeIndex)
  },

  goRecords: function () {
    wx.switchTab({ url: '/pages/records/records' })
  }
})
