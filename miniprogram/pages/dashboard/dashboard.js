// 管理看板：统计概览 + 出勤趋势 + 场所时段分布（管理角色专用，statistics:view）
// 纯视图渲染（无 ECharts），图表用 view 高度百分比实现
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const RANGES = [
  { label: '近7天', days: 7 },
  { label: '近14天', days: 14 },
  { label: '近30天', days: 30 }
]

function isoDate(d) {
  return d.getFullYear() + '-' + fmt.pad2(d.getMonth() + 1) + '-' + fmt.pad2(d.getDate())
}

function rangeOf(days) {
  const to = new Date()
  const from = new Date(to.getTime() - (days - 1) * 86400000)
  return { dateFrom: isoDate(from), dateTo: isoDate(to) }
}

Page({
  data: {
    ranges: RANGES.map(function (r) { return r.label }),
    rangeIndex: 1,
    loading: true,
    denied: false,
    // 概览
    cards: [],
    metaText: '',
    scopeText: '',
    // 趋势
    trendBars: [],
    trendEmpty: false,
    // 场所热力
    places: [],
    placeEmpty: false
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadAll()
  },

  onRangeTap: function (e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (idx === this.data.rangeIndex) return
    this.setData({ rangeIndex: idx })
    this.loadAll()
  },

  onPullDownRefresh: function () {
    this.loadAll(function () { wx.stopPullDownRefresh() })
  },

  params: function () {
    const r = RANGES[this.data.rangeIndex]
    const p = rangeOf(r.days)
    return { dateFrom: p.dateFrom, dateTo: p.dateTo }
  },

  loadAll: function (done) {
    const that = this
    this.setData({ loading: true, denied: false })
    const p = this.params()
    let finished = 0
    function settle() {
      finished += 1
      if (finished >= 3) {
        that.setData({ loading: false })
        if (done) done()
      }
    }
    this.loadSummary(p, settle)
    this.loadTrend(p, settle)
    this.loadPlaces(p, settle)
  },

  handleDenied: function () {
    this.setData({ denied: true, loading: false })
  },

  loadSummary: function (p, done) {
    const that = this
    api.get('/api/statistics/summary', p)
      .then(function (data) {
        const s = data.summary || {}
        const sourceText = s.dataSource === 'real' ? '真实数据'
          : (s.dataSource === 'sample' ? '演示数据' : '含演示数据混合统计')
        that.setData({
          cards: [
            { label: '出勤率', value: (s.attendanceRate != null ? s.attendanceRate.toFixed(1) : '-') + '%', cls: 'c-blue' },
            { label: '打卡次数', value: s.checkinCount != null ? s.checkinCount : '-', cls: 'c-green' },
            { label: '正常(人天)', value: s.normalDays != null ? s.normalDays : '-', cls: 'c-green' },
            { label: '迟到(人天)', value: s.lateDays != null ? s.lateDays : '-', cls: 'c-orange' },
            { label: '早退(人天)', value: s.earlyDays != null ? s.earlyDays : '-', cls: 'c-orange' },
            { label: '缺勤(人天)', value: s.absentDays != null ? s.absentDays : '-', cls: 'c-red' }
          ],
          metaText: s.dateFrom + ' ~ ' + s.dateTo + ' · ' + s.employeeCount + ' 名员工 · ' + sourceText,
          scopeText: data.dataScope === 'ALL' ? '数据范围：全部' : (data.dataScope === 'DEPT' ? '数据范围：本部门' : '数据范围：仅本人')
        })
        done()
      })
      .catch(function (err) {
        if (err && err.code === 40301) that.handleDenied()
        done()
      })
  },

  loadTrend: function (p, done) {
    const that = this
    const q = Object.assign({ type: 'day' }, p)
    api.get('/api/statistics/trend', q)
      .then(function (data) {
        const points = (data.points || []).filter(function (pt) { return pt.checkinCount > 0 })
        if (points.length === 0) {
          that.setData({ trendBars: [], trendEmpty: true })
          done()
          return
        }
        const max = Math.max.apply(null, points.map(function (pt) { return pt.checkinCount }))
        const bars = points.map(function (pt) {
          const h = Math.max(8, Math.round(pt.checkinCount / max * 100))
          return {
            period: (pt.period || '').slice(5),  // 10-29
            count: pt.checkinCount,
            late: pt.lateDays || 0,
            height: h,
            sample: pt.sample
          }
        })
        that.setData({ trendBars: bars, trendEmpty: false })
        done()
      })
      .catch(function () { done() })
  },

  loadPlaces: function (p, done) {
    const that = this
    api.get('/api/statistics/place-heat', p)
      .then(function (data) {
        const rows = data.hours || []
        // 聚合：场所 → 总打卡次数
        const agg = {}
        rows.forEach(function (r) {
          const k = r.placeName || ('场所#' + r.placeId)
          agg[k] = (agg[k] || 0) + (r.count || 0)
        })
        const names = Object.keys(agg)
        if (names.length === 0) {
          that.setData({ places: [], placeEmpty: true })
          done()
          return
        }
        const max = Math.max.apply(null, names.map(function (n) { return agg[n] }))
        const places = names
          .sort(function (a, b) { return agg[b] - agg[a] })
          .map(function (n) {
            return {
              name: n,
              count: agg[n],
              pct: Math.round(agg[n] / max * 100)
            }
          })
        that.setData({ places: places, placeEmpty: false })
        done()
      })
      .catch(function () { done() })
  }
})
