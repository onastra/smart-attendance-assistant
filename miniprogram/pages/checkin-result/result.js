// 打卡结果页：成功/幂等/失败都落在这一页，给一个明确的终态，避免"点了没反应"
const checkin = require('../../utils/checkin')
const fmt = require('../../utils/format')

Page({
  data: {
    r: null,
    // ok / dup / fail 三种视觉主题
    theme: 'fail',
    iconText: '!',
    resultCls: 'tag-gray'
  },

  onLoad: function () {
    const r = checkin.takeResult()
    if (!r) {
      wx.switchTab({ url: '/pages/index/index' })
      return
    }
    let theme = 'fail'
    let iconText = '×'
    if (r.ok) {
      theme = r.idempotent ? 'dup' : 'ok'
      iconText = r.idempotent ? 'i' : '✓'
    }
    const meta = fmt.resultMeta(r.result, r.resultText)
    this.setData({
      r: r,
      theme: theme,
      iconText: iconText,
      resultCls: meta.cls
    })
  },

  goRecords: function () {
    wx.switchTab({ url: '/pages/records/records' })
  },

  goHome: function () {
    wx.switchTab({ url: '/pages/index/index' })
  },

  // 失败后直接再扫一次
  rescan: function () {
    checkin.scanThenConfirm({})
  }
})
