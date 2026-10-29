// 打卡确认页：扫码后先展示"将要打卡的场所/类型/围栏预判"，用户确认后再真正提交
// 这里不再让用户面对一长串二维码原文，避免"确认后毫无反馈"的盲操作
const checkin = require('../../utils/checkin')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

Page({
  data: {
    // 三种视图状态：loading（预校验中）/ error（码无效或已过期）/ ready（可确认）
    loading: true,
    error: '',
    submitting: false,

    content: '',
    placeName: '',
    employeeName: '',
    locationText: '正在获取定位…',
    fenceText: '',
    fenceWarn: false,
    remainText: '',
    expired: false,
    onceOnly: false,

    typeLabels: checkin.TYPE_OPTIONS.map(function (o) { return o.short }),
    typeIndex: 0,
    typeHint: ''
  },

  onLoad: function (options) {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const content = decodeURIComponent((options && options.content) || '')
    const presetType = Number((options && options.typeIndex) || 0)
    if (!content) {
      this.setData({ loading: false, error: '没有拿到二维码内容，请重新扫码' })
      return
    }
    const profile = auth.getProfile() || {}
    this.setData({
      content: content,
      employeeName: profile.nickname || profile.username || '',
      typeIndex: presetType >= 0 && presetType < checkin.TYPE_OPTIONS.length ? presetType : 0
    })
    this.prepare()
  },

  onUnload: function () {
    this.stopCountdown()
  },

  // 预校验：先定位（失败不阻断）→ 再问后端场所/时效/围栏
  prepare: function () {
    const that = this
    this.setData({ loading: true, error: '' })
    checkin.fetchLocation(6000)
      .then(function (coords) {
        that.coords = coords
        that.setData({
          locationText: coords ? '已定位，将做围栏校验' : '未授权定位，本次跳过围栏校验'
        })
        return checkin.preview(that.data.content, coords)
      })
      .then(function (info) {
        that.info = info
        that.setData({
          loading: false,
          placeName: info.placeName || '-',
          onceOnly: !!info.onceOnly,
          expired: Number(info.remainingSeconds) <= 0,
          fenceText: that.fenceTextOf(info),
          fenceWarn: info.locationChecked && !info.inRange,
          typeHint: '当前时间建议按「' + (info.suggestedTypeText || '自动') + '」打卡，可手动切换'
        })
        that.startCountdown(Number(info.remainingSeconds) || 0)
      })
      .catch(function (err) {
        that.setData({
          loading: false,
          error: (err && err.message) || '二维码校验失败，请重新扫码'
        })
      })
  },

  fenceTextOf: function (info) {
    if (!info.locationChecked) return '未获取到定位，提交时不做围栏校验'
    const distance = Number(info.distanceMeters || 0).toFixed(1)
    if (info.inRange) {
      return '距场所 ' + distance + ' 米，在 ' + info.radiusMeters + ' 米范围内'
    }
    return '距场所 ' + distance + ' 米，已超出 ' + info.radiusMeters + ' 米范围，提交会被拒绝'
  },

  startCountdown: function (seconds) {
    const that = this
    this.stopCountdown()
    let left = seconds
    const render = function () {
      if (left <= 0) {
        that.setData({ remainText: '二维码已过期', expired: true })
        that.stopCountdown()
        return
      }
      const m = Math.floor(left / 60)
      const s = left % 60
      that.setData({
        remainText: '二维码剩余有效期 ' + fmt.pad2(m) + ':' + fmt.pad2(s),
        expired: false
      })
      left -= 1
    }
    render()
    this.timer = setInterval(render, 1000)
  },

  stopCountdown: function () {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.currentTarget.dataset.index) })
  },

  // 确认打卡：无论成功失败都进结果页，绝不让请求结果悄无声息地消失
  confirm: function () {
    const that = this
    if (this.data.submitting) return
    if (this.data.expired) {
      wx.showToast({ title: '二维码已过期，请重新扫码', icon: 'none' })
      return
    }
    const type = checkin.TYPE_OPTIONS[this.data.typeIndex]
    this.setData({ submitting: true })
    wx.showLoading({ title: '打卡中…', mask: true })
    checkin.submit(this.data.content, type.value, this.coords)
      .then(function (result) {
        wx.hideLoading()
        that.setData({ submitting: false })
        checkin.goResult(result)
      })
      .catch(function (err) {
        wx.hideLoading()
        that.setData({ submitting: false })
        checkin.goResult({
          ok: false,
          title: '打卡失败',
          subtitle: (err && err.message) || '请稍后重试',
          placeName: that.data.placeName || '-',
          employeeName: that.data.employeeName,
          typeText: type.short,
          resultText: '-',
          timeText: '',
          distanceText: that.data.fenceText
        })
      })
  },

  rescan: function () {
    wx.navigateBack({
      fail: function () {
        wx.switchTab({ url: '/pages/index/index' })
      }
    })
  }
})
