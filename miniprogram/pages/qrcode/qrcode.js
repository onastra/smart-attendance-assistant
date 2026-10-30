// 管理员/主管签发员工打卡二维码：选场所 → 选类型 → 生成 → 大图展示（动态码自动续发）
// 与后台 Web「考勤二维码」页、H5 /h5/qrcode 共用同一套 /api/qrcodes 接口
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const config = require('../../config')

const QR_TYPES = [
  { label: '动态码（120 秒轮换）', value: 'DYNAMIC', ttl: 120 },
  { label: '固定码（24 小时张贴）', value: 'FIXED', ttl: 86400 }
]

const CHECKIN_TYPES = [
  { label: '自动判定（按打卡时间）', value: 'AUTO' },
  { label: '上班', value: 'ON_DUTY' },
  { label: '下班', value: 'OFF_DUTY' },
  { label: '外勤', value: 'FIELD' }
]

Page({
  data: {
    places: [],
    placeNames: [],
    placeIndex: 0,
    qrTypeLabels: QR_TYPES.map(function (o) { return o.label }),
    qrTypeIndex: 0,
    checkinLabels: CHECKIN_TYPES.map(function (o) { return o.label }),
    checkinIndex: 0,
    onceOnly: false,
    issuing: false,
    qr: null,
    qrImage: '',
    remain: 0,
    checkinText: '自动判定'
  },

  onLoad: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 只有持「生成二维码」权限的账号（管理员 / 部门主管）才能签发
    if (!auth.hasPermission('attendance:qrcode:create')) {
      wx.showToast({ title: '当前账号无签发权限', icon: 'none' })
      setTimeout(function () { wx.navigateBack() }, 1200)
      return
    }
    this.loadPlaces()
  },

  onUnload: function () {
    this.stopTimer()
  },

  onHide: function () {
    this.stopTimer()
  },

  loadPlaces: function () {
    const that = this
    api.get('/api/places', { pageNum: 1, pageSize: 100 })
      .then(function (data) {
        const list = (data && data.list) || []
        that.setData({
          places: list,
          placeNames: list.map(function (p) { return p.placeName })
        })
      })
      .catch(function () { })
  },

  onPlaceChange: function (e) {
    this.setData({ placeIndex: Number(e.detail.value) })
  },

  onQrTypeChange: function (e) {
    this.setData({ qrTypeIndex: Number(e.detail.value) })
  },

  onCheckinChange: function (e) {
    const idx = Number(e.detail.value)
    this.setData({ checkinIndex: idx, checkinText: CHECKIN_TYPES[idx].label })
  },

  onOnceChange: function (e) {
    this.setData({ onceOnly: !!e.detail.value })
  },

  issue: function () {
    const place = this.data.places[this.data.placeIndex]
    if (!place) {
      wx.showToast({ title: '请先选择办公场所', icon: 'none' })
      return
    }
    if (this.data.issuing) return
    const that = this
    const type = QR_TYPES[this.data.qrTypeIndex]
    this.setData({ issuing: true })
    api.post('/api/qrcodes', {
      placeId: place.id,
      qrType: type.value,
      ttlSeconds: type.ttl,
      onceOnly: this.data.onceOnly ? 1 : 0,
      checkinType: CHECKIN_TYPES[this.data.checkinIndex].value
    })
      .then(function (result) {
        that.setData({
          qr: {
            id: result.id,
            placeName: result.placeName,
            qrType: result.qrType,
            checkinType: result.checkinType,
            onceOnly: result.onceOnly,
            payload: result.payload,
            expireTime: String(result.expireTime || '').replace('T', ' ').slice(0, 19)
          },
          remain: Number(result.ttlSeconds || 0)
        })
        that.renderImage(result.id)
        that.startTimer()
        wx.showToast({ title: '二维码已生成', icon: 'success' })
      })
      .catch(function () { })
      .then(function () { that.setData({ issuing: false }) })
  },

  // 图片接口需要登录态：img 直接引用 URL 带不上 Authorization，改为下载后显示临时文件
  renderImage: function (id) {
    const that = this
    wx.downloadFile({
      url: config.BASE_URL + '/api/qrcodes/' + id + '/image',
      header: { 'Authorization': 'Bearer ' + (auth.getToken() || '') },
      success: function (res) {
        if (res.statusCode === 200) {
          that.setData({ qrImage: res.tempFilePath })
        } else {
          wx.showToast({ title: '二维码图片加载失败', icon: 'none' })
        }
      },
      fail: function () {
        wx.showToast({ title: '二维码图片加载失败', icon: 'none' })
      }
    })
  },

  startTimer: function () {
    const that = this
    this.stopTimer()
    this.timer = setInterval(function () {
      const remain = that.data.remain - 1
      if (remain > 0) {
        that.setData({ remain: remain })
        return
      }
      that.setData({ remain: 0 })
      // 动态码到期自动续发，员工随时扫到的都是有效码；固定码到期则停在这里
      const qr = that.data.qr
      if (qr && qr.qrType === 'DYNAMIC' && !that.data.issuing) {
        that.stopTimer()
        that.issue()
      }
    }, 1000)
  },

  stopTimer: function () {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  copyPayload: function () {
    const qr = this.data.qr
    if (!qr || !qr.payload) return
    wx.setClipboardData({
      data: qr.payload,
      success: function () { wx.showToast({ title: '已复制二维码原文', icon: 'success' }) }
    })
  },

  invalidate: function () {
    const that = this
    const qr = this.data.qr
    if (!qr || !qr.id) return
    wx.showModal({
      title: '作废二维码',
      content: '作废后该码立即不可用于打卡，确认继续？',
      success: function (res) {
        if (!res.confirm) return
        api.post('/api/qrcodes/' + qr.id + '/invalidate', {})
          .then(function () {
            that.stopTimer()
            that.setData({ remain: 0 })
            wx.showToast({ title: '已作废', icon: 'success' })
          })
          .catch(function () { })
      }
    })
  }
})
