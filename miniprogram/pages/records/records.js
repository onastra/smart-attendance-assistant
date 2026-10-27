// 我的考勤：分页加载，支持下拉刷新、上拉加载更多、按结果筛选
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const RESULT_FILTERS = [
  { label: '全部', value: '' },
  { label: '正常', value: 'NORMAL' },
  { label: '迟到', value: 'LATE' },
  { label: '早退', value: 'EARLY' }
]

const PAGE_SIZE = 15

Page({
  data: {
    filterLabels: RESULT_FILTERS.map(function (o) { return o.label }),
    filterIndex: 0,
    items: [],
    pageNum: 1,
    total: 0,
    hasMore: true,
    loading: false
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.reload()
  },

  onPullDownRefresh: function () {
    this.reload(function () {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom: function () {
    if (!this.data.hasMore || this.data.loading) return
    this.loadPage(this.data.pageNum + 1)
  },

  onFilterChange: function (e) {
    this.setData({ filterIndex: Number(e.detail.value) })
    this.reload()
  },

  buildQuery: function (pageNum) {
    const filter = RESULT_FILTERS[this.data.filterIndex]
    return {
      pageNum: pageNum,
      pageSize: PAGE_SIZE,
      result: filter.value || undefined
    }
  },

  reload: function (done) {
    const that = this
    this.setData({ items: [], pageNum: 1, hasMore: true })
    this.loadPage(1, done)
  },

  loadPage: function (pageNum, done) {
    const that = this
    this.setData({ loading: true })
    api.get('/api/my/attendance/records', this.buildQuery(pageNum))
      .then(function (data) {
        const page = data.page || {}
        const list = page.list || []
        const mapped = list.map(function (r) {
          const meta = fmt.resultMeta(r.result, r.resultText)
          return {
            id: r.id,
            timeText: fmt.shortTime(r.checkinTime),
            typeText: r.checkinTypeText || r.checkinType || '-',
            placeName: r.placeName || '-',
            resultText: meta.text,
            tagCls: meta.cls,
            sourceText: fmt.sourceText(r.source)
          }
        })
        const items = pageNum === 1 ? mapped : that.data.items.concat(mapped)
        that.setData({
          items: items,
          pageNum: pageNum,
          total: page.total || 0,
          hasMore: items.length < (page.total || 0)
        })
      })
      .catch(function () {
        // 提示已统一弹出
      })
      .then(function () {
        that.setData({ loading: false })
        if (done) done()
      })
  }
})
