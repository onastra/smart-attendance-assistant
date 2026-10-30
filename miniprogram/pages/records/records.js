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
    // 管理员等未绑定档案的账号没有个人考勤：入口已隐藏，这里兜底拦截直开 URL 的情况
    if (!(auth.getProfile() || {}).employeeId) {
      wx.showToast({ title: '管理员账号无个人考勤', icon: 'none' })
      setTimeout(function () { wx.switchTab({ url: '/pages/index/index' }) }, 1200)
      return
    }
    // 同步 tabBar 高亮（索引 1 = 考勤）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(1)
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

  // 胶囊筛选：点击直接切换
  onFilterTap: function (e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (idx === this.data.filterIndex) return
    this.setData({ filterIndex: idx })
    this.reload()
  },

  buildQuery: function (pageNum) {
    const filter = RESULT_FILTERS[this.data.filterIndex]
    const q = {
      pageNum: pageNum,
      pageSize: PAGE_SIZE
    }
    // 注意：GET 查询参数里不能塞 undefined —— wx.request 会把它序列化成
    // 字符串 "undefined" 传给后端，导致"全部"筛选命中 0 条（2026-10-29 实测踩坑）。
    // 只在选中具体结果时才加 result 键。
    if (filter.value) {
      q.result = filter.value
    }
    return q
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
