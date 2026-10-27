// 提交请假/加班申请（REQ-E-01 的移动端入口）
const api = require('../../utils/request')
const auth = require('../../utils/auth')

const TYPE_OPTIONS = [
  { label: '请假', value: 'LEAVE' },
  { label: '加班', value: 'OVERTIME' }
]

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

Page({
  data: {
    typeLabels: TYPE_OPTIONS.map(function (o) { return o.label }),
    typeIndex: 0,
    // 开始/结束时间拆成日期 + 时间两个 picker，拼成 LocalDateTime 需要的格式
    startDate: '',
    startTime: '09:00',
    endDate: '',
    endTime: '18:00',
    reason: '',
    submitting: false
  },

  onLoad: function () {
    const now = new Date()
    const today = now.getFullYear() + '-' + pad2(now.getMonth() + 1) + '-' + pad2(now.getDate())
    this.setData({ startDate: today, endDate: today })
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.detail.value) })
  },

  onStartDateChange: function (e) { this.setData({ startDate: e.detail.value }) },
  onStartTimeChange: function (e) { this.setData({ startTime: e.detail.value }) },
  onEndDateChange: function (e) { this.setData({ endDate: e.detail.value }) },
  onEndTimeChange: function (e) { this.setData({ endTime: e.detail.value }) },

  onReasonInput: function (e) { this.setData({ reason: e.detail.value }) },

  submit: function () {
    const that = this
    const type = TYPE_OPTIONS[this.data.typeIndex].value
    const reason = (this.data.reason || '').trim()
    if (!this.data.startDate || !this.data.endDate) {
      wx.showToast({ title: '请选择开始与结束日期', icon: 'none' })
      return
    }
    if (reason.length === 0) {
      wx.showToast({ title: '请填写事由', icon: 'none' })
      return
    }
    // 拼成 "2026-10-27T09:00:00"（LocalDateTime 可直接反序列化）
    const startISO = this.data.startDate + 'T' + this.data.startTime + ':00'
    const endISO = this.data.endDate + 'T' + this.data.endTime + ':00'
    if (startISO >= endISO) {
      wx.showToast({ title: '结束时间必须晚于开始时间', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    api.post('/api/applications', {
      type: type,
      startTime: startISO,
      endTime: endISO,
      reason: reason
    })
      .then(function (data) {
        wx.showToast({ title: '提交成功', icon: 'success' })
        setTimeout(function () {
          wx.redirectTo({ url: '/pages/applications/applications?tab=mine' })
        }, 600)
      })
      .catch(function () {
        // 提示已统一弹出（如 TC-E-01 的"结束早于开始被拒"）
      })
      .then(function () {
        that.setData({ submitting: false })
      })
  }
})
