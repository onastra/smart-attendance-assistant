// 打卡流程共享封装：扫码 → 预校验 → 确认页 → 提交 → 结果页
// 设计取舍：不再用 showModal 弹"一长串二维码原文"让用户盲确认，
// 而是先调 /api/checkin/preview 拿到场所名/有效期/围栏预判，进确认页给人看清再打卡。
const api = require('./request')

// 打卡类型文案映射。注意：员工端不提供类型选择——
// 类型由管理员/主管签发二维码时指定，码上为 AUTO 时后端按当前时间判定上班/下班。
const TYPE_TEXT = {
  AUTO: '自动判定',
  ON_DUTY: '上班',
  OFF_DUTY: '下班',
  FIELD: '外勤'
}

function typeText(type) {
  return TYPE_TEXT[String(type || '').toUpperCase()] || '自动判定'
}

const CONFIRM_PAGE = '/pages/checkin-confirm/confirm'
const RESULT_PAGE = '/pages/checkin-result/result'
const RESULT_KEY = 'lz_checkin_result'

/**
 * 取定位。失败不阻断打卡：后端对未上报坐标会跳过围栏校验并在记录里标注。
 * 加超时兜底——某些机型 getLocation 既不 success 也不 fail，会让确认页一直转圈。
 */
function fetchLocation(timeoutMs) {
  const limit = timeoutMs || 6000
  return new Promise(function (resolve) {
    let settled = false
    const timer = setTimeout(function () {
      if (!settled) {
        settled = true
        resolve(null)
      }
    }, limit)
    wx.getLocation({
      type: 'gcj02',
      success: function (res) {
        if (settled) return
        settled = true
        clearTimeout(timer)
        resolve({ lng: res.longitude, lat: res.latitude })
      },
      fail: function () {
        if (settled) return
        settled = true
        clearTimeout(timer)
        resolve(null)
      }
    })
  })
}

// 预校验：只读，返回场所名/剩余有效期/围栏预判（不产生打卡记录）
function preview(content, coords) {
  return api.post('/api/checkin/preview', {
    content: content,
    lng: coords ? coords.lng : undefined,
    lat: coords ? coords.lat : undefined
  })
}

// 提交打卡：content = 二维码原文
// 不传 checkinType：打卡类型以二维码签发时的指定为准（码上 AUTO 则由后端按时间判定）
function submit(content, coords) {
  return api.post('/api/checkin/request-id', {})
    .then(function (data) {
      return api.post('/api/checkin', {
        content: content,
        requestId: data.requestId,
        lng: coords ? coords.lng : undefined,
        lat: coords ? coords.lat : undefined
      })
    })
    .then(function (result) {
      const distanceText = result.locationChecked
        ? '围栏校验通过，距场所 ' + Number(result.distanceMeters).toFixed(1) + ' 米'
        : '未做围栏校验（未上报坐标或场所未启用）'
      return {
        ok: true,
        idempotent: !!result.idempotent,
        title: result.idempotent ? '这次打卡已被记录过' : '打卡成功',
        subtitle: result.idempotent ? '幂等拦截：重复提交不会产生第二条记录' : '考勤记录已生成',
        employeeName: result.employeeName || '',
        placeName: result.placeName || '-',
        typeText: result.checkinTypeText || result.checkinType || '-',
        resultText: result.resultText || result.result || '-',
        timeText: String(result.checkinTime || '').replace('T', ' ').slice(0, 19),
        distanceText: distanceText,
        result: result.result || ''
      }
    })
}

// 扫码：不弹原始码，直接进确认页
function scanThenConfirm() {
  wx.scanCode({
    onlyFromCamera: false,
    success: function (res) {
      const content = res.result || ''
      if (!content) {
        wx.showToast({ title: '二维码内容为空', icon: 'none' })
        return
      }
      openConfirm(content)
    },
    fail: function () {
      // 用户主动取消扫码，不打扰
    }
  })
}

// 进确认页（手动输入兜底也走这里，保证校验链一致）
function openConfirm(content) {
  wx.navigateTo({ url: CONFIRM_PAGE + '?content=' + encodeURIComponent(content) })
}

// 结果页数据交接：redirectTo 不能带复杂参数，用 storage 过渡
function goResult(result) {
  try {
    wx.setStorageSync(RESULT_KEY, result)
  } catch (e) { /* 存储异常不影响跳转 */ }
  wx.redirectTo({ url: RESULT_PAGE })
}

function takeResult() {
  let result = null
  try {
    result = wx.getStorageSync(RESULT_KEY)
  } catch (e) { /* ignore */ }
  try {
    wx.removeStorageSync(RESULT_KEY)
  } catch (e) { /* ignore */ }
  return result || null
}

module.exports = {
  typeText: typeText,
  fetchLocation: fetchLocation,
  preview: preview,
  submit: submit,
  scanThenConfirm: scanThenConfirm,
  openConfirm: openConfirm,
  goResult: goResult,
  takeResult: takeResult
}
