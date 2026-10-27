// 打卡页：wx.scanCode 扫二维码 → 幂等请求号 → 提交打卡
// 与 H5 打卡页同一套校验链：二维码格式 → 签名 → 有效期 → 场所 → 绑定 → 幂等 → 重复窗口 → 围栏
const api = require('../../utils/request')
const auth = require('../../utils/auth')

const TYPE_OPTIONS = [
  { label: '自动判定（按时间）', value: '' },
  { label: '上班', value: 'ON_DUTY' },
  { label: '下班', value: 'OFF_DUTY' },
  { label: '外勤', value: 'FIELD' }
]

Page({
  data: {
    typeIndex: 0,
    typeLabels: TYPE_OPTIONS.map(function (o) { return o.label }),
    manualContent: '',
    useLocation: true,
    locationText: '',
    submitting: false,
    // 最近一次打卡结果（已格式化，供 WXML 直接展示）
    result: null
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.detail.value) })
  },

  onManualInput: function (e) {
    this.setData({ manualContent: e.detail.value })
  },

  onUseLocationChange: function (e) {
    this.setData({ useLocation: e.detail.value, locationText: '' })
  },

  // 定位失败不阻断打卡：后端对未上报坐标会跳过围栏校验并在记录中标注
  fetchLocation: function () {
    const that = this
    if (!this.data.useLocation) {
      return Promise.resolve(null)
    }
    return new Promise(function (resolve) {
      wx.getLocation({
        type: 'gcj02',
        success: function (res) {
          that.setData({
            locationText: '已获取定位（' + res.longitude.toFixed(5) + ', ' + res.latitude.toFixed(5) + '）'
          })
          resolve({ lng: res.longitude, lat: res.latitude })
        },
        fail: function () {
          that.setData({ locationText: '定位失败，将按"未上报坐标"提交（跳过围栏校验）' })
          resolve(null)
        }
      })
    })
  },

  scanAndCheckin: function () {
    const that = this
    wx.scanCode({
      onlyFromCamera: false,
      success: function (res) {
        const content = res.result || ''
        if (!content) {
          wx.showToast({ title: '二维码内容为空', icon: 'none' })
          return
        }
        that.confirmAndSubmit(content)
      },
      fail: function () {
        // 用户取消扫码，不打扰
      }
    })
  },

  submitManual: function () {
    const content = (this.data.manualContent || '').trim()
    if (!content) {
      wx.showToast({ title: '请粘贴二维码内容', icon: 'none' })
      return
    }
    this.confirmAndSubmit(content)
  },

  confirmAndSubmit: function (content) {
    const that = this
    wx.showModal({
      title: '确认打卡',
      content: content.length > 120 ? content.slice(0, 120) + '…' : content,
      confirmText: '打卡',
      success: function (res) {
        if (res.confirm) {
          that.doCheckin(content)
        }
      }
    })
  },

  doCheckin: function (content) {
    const that = this
    if (this.data.submitting) return
    this.setData({ submitting: true })
    // 1) 先拿幂等请求号，2) 带上二维码原文提交
    api.post('/api/checkin/request-id', {})
      .then(function (data) {
        return that.fetchLocation().then(function (coords) {
          return api.post('/api/checkin', {
            content: content,
            requestId: data.requestId,
            checkinType: TYPE_OPTIONS[that.data.typeIndex].value || undefined,
            lng: coords ? coords.lng : undefined,
            lat: coords ? coords.lat : undefined
          })
        })
      })
      .then(function (result) {
        that.setData({
          result: {
            title: result.idempotent ? '重复提交已拦截（幂等生效）' : '打卡成功',
            placeName: result.placeName || '-',
            employeeName: result.employeeName || '',
            typeText: result.checkinTypeText || result.checkinType || '-',
            resultText: result.resultText || result.result || '-',
            timeText: String(result.checkinTime || '').replace('T', ' ').slice(0, 19),
            distanceText: result.locationChecked
              ? '围栏校验通过，距场所 ' + Number(result.distanceMeters).toFixed(1) + ' 米'
              : '未做围栏校验（未上报坐标或场所未启用）',
            source: result.source || ''
          },
          manualContent: ''
        })
      })
      .catch(function () {
        // 错误提示已由 request 统一弹出
      })
      .then(function () {
        that.setData({ submitting: false })
      })
  }
})
