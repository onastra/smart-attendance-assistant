// 打卡流程共享封装：首页直扫与打卡页复用同一条校验链
// 二维码格式 → 签名 → 有效期 → 场所 → 绑定 → 幂等 → 重复窗口 → 围栏
const api = require('./request')

const TYPE_OPTIONS = [
  { label: '自动判定（按时间）', value: '' },
  { label: '上班', value: 'ON_DUTY' },
  { label: '下班', value: 'OFF_DUTY' },
  { label: '外勤', value: 'FIELD' }
]

// 定位失败不阻断打卡：后端对未上报坐标会跳过围栏校验并在记录中标注
function fetchLocation() {
  return new Promise(function (resolve) {
    wx.getLocation({
      type: 'gcj02',
      success: function (res) {
        resolve({ lng: res.longitude, lat: res.latitude })
      },
      fail: function () {
        resolve(null)
      }
    })
  })
}

// 提交打卡：content = 二维码原文；checkinType = '' 表示自动判定
// resolve(格式化后的结果对象)，reject 透传 request 的错误（提示已统一弹出）
function submit(content, checkinType) {
  return api.post('/api/checkin/request-id', {})
    .then(function (data) {
      return fetchLocation().then(function (coords) {
        return api.post('/api/checkin', {
          content: content,
          requestId: data.requestId,
          checkinType: checkinType || undefined,
          lng: coords ? coords.lng : undefined,
          lat: coords ? coords.lat : undefined
        })
      })
    })
    .then(function (result) {
      return {
        title: result.idempotent ? '重复提交已拦截（幂等生效）' : '打卡成功',
        success: !result.idempotent,
        placeName: result.placeName || '-',
        employeeName: result.employeeName || '',
        typeText: result.checkinTypeText || result.checkinType || '-',
        resultText: result.resultText || result.result || '-',
        timeText: String(result.checkinTime || '').replace('T', ' ').slice(0, 19),
        locationChecked: !!result.locationChecked,
        distanceText: result.locationChecked
          ? '围栏校验通过，距场所 ' + Number(result.distanceMeters).toFixed(1) + ' 米'
          : '未做围栏校验（未上报坐标或场所未启用）',
        source: result.source || ''
      }
    })
}

// 扫码 → 确认 → 提交（打卡页与自定义 tabBar 共用）
// opts: { checkinType, onResult(formatted) }
function scanAndSubmit(opts) {
  const options = opts || {}
  wx.scanCode({
    onlyFromCamera: false,
    success: function (res) {
      const content = res.result || ''
      if (!content) {
        wx.showToast({ title: '二维码内容为空', icon: 'none' })
        return
      }
      wx.showModal({
        title: '确认打卡',
        content: content.length > 120 ? content.slice(0, 120) + '…' : content,
        confirmText: '打卡',
        success: function (m) {
          if (!m.confirm) return
          wx.showLoading({ title: '提交中…', mask: true })
          submit(content, options.checkinType)
            .then(function (result) {
              wx.hideLoading()
              if (options.onResult) options.onResult(result)
            })
            .catch(function () {
              wx.hideLoading()
            })
        }
      })
    },
    fail: function () {
      // 用户取消扫码，不打扰
    }
  })
}

module.exports = {
  TYPE_OPTIONS: TYPE_OPTIONS,
  fetchLocation: fetchLocation,
  submit: submit,
  scanAndSubmit: scanAndSubmit
}
