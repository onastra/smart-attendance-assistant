// 打卡 tab：中央扫码大按钮已下沉到 tabBar，本页承担——
// 1) 接收 tabBar/首页直扫的结果并展示  2) 类型选择  3) 手动输入兜底
const checkin = require('../../utils/checkin')

const RESULT_KEY = 'lz_last_checkin_result'

Page({
  data: {
    typeIndex: 0,
    typeLabels: checkin.TYPE_OPTIONS.map(function (o) { return o.label }),
    manualContent: '',
    submitting: false,
    // 最近一次打卡结果（已格式化，供 WXML 直接展示）
    result: null
  },

  onShow: function () {
    const token = require('../../utils/auth').getToken()
    if (!token) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // tabBar/首页直扫的结果通过 storage 转交（switchTab 不能带参数）
    let pending = null
    try { pending = wx.getStorageSync(RESULT_KEY) } catch (e) { }
    if (pending && pending.title) {
      try { wx.removeStorageSync(RESULT_KEY) } catch (e) { }
      this.setData({ result: pending })
    }
    // 同步 tabBar 高亮（索引 2 = 打卡）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(2)
    }
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.detail.value) })
  },

  onManualInput: function (e) {
    this.setData({ manualContent: e.detail.value })
  },

  scanAndCheckin: function () {
    const that = this
    checkin.scanAndSubmit({
      checkinType: checkin.TYPE_OPTIONS[this.data.typeIndex].value,
      onResult: function (result) {
        that.setData({ result: result })
      }
    })
  },

  submitManual: function () {
    const that = this
    const content = (this.data.manualContent || '').trim()
    if (!content) {
      wx.showToast({ title: '请粘贴二维码内容', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    wx.showLoading({ title: '提交中…', mask: true })
    checkin.submit(content, checkin.TYPE_OPTIONS[this.data.typeIndex].value)
      .then(function (result) {
        that.setData({ result: result, manualContent: '' })
      })
      .catch(function () {
        // 错误提示已由 request 统一弹出
      })
      .then(function () {
        wx.hideLoading()
        that.setData({ submitting: false })
      })
  },

  goRecords: function () {
    wx.switchTab({ url: '/pages/records/records' })
  }
})
