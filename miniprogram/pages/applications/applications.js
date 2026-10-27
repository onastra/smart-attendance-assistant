// 申请列表：两个标签页
// - 我的申请（REQ-E-02）：仅本人，待审批可撤回
// - 待我审批（REQ-E-03/04）：需 application:approve 权限，驳回必须填意见
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const PAGE_SIZE = 15

Page({
  data: {
    tab: 'mine',
    canApprove: false,
    items: [],
    pageNum: 1,
    total: 0,
    hasMore: true,
    loading: false
  },

  onLoad: function (options) {
    this.setData({
      tab: options && options.tab === 'todo' ? 'todo' : 'mine',
      canApprove: auth.hasPermission('application:approve')
    })
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.reload()
  },

  onPullDownRefresh: function () {
    this.reload(function () { wx.stopPullDownRefresh() })
  },

  onReachBottom: function () {
    if (!this.data.hasMore || this.data.loading) return
    this.loadPage(this.data.pageNum + 1)
  },

  switchTab: function (e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab: tab, items: [], pageNum: 1, hasMore: true })
    this.reload()
  },

  mapItem: function (a) {
    return {
      id: a.id,
      applicationNo: a.applicationNo || '',
      typeText: fmt.appTypeText(a.type),
      isLeave: a.type === 'LEAVE',
      rangeText: fmt.fullTime(a.startTime) + ' ~ ' + fmt.fullTime(a.endTime),
      durationHours: a.durationHours,
      reason: a.reason || '',
      statusText: fmt.appStatusMeta(a.status).text,
      statusCls: fmt.appStatusMeta(a.status).cls,
      status: a.status,
      approverName: a.approverName || '',
      approvalComment: a.approvalComment || ''
    }
  },

  reload: function (done) {
    const that = this
    this.loadPage(1, done)
  },

  loadPage: function (pageNum, done) {
    const that = this
    this.setData({ loading: true })
    const url = this.data.tab === 'mine' ? '/api/applications/mine' : '/api/applications/todo'
    api.get(url, { pageNum: pageNum, pageSize: PAGE_SIZE })
      .then(function (page) {
        const list = (page && page.list) || []
        const mapped = list.map(function (a) { return that.mapItem(a) })
        const items = pageNum === 1 ? mapped : that.data.items.concat(mapped)
        that.setData({
          items: items,
          pageNum: pageNum,
          total: (page && page.total) || 0,
          hasMore: items.length < ((page && page.total) || 0)
        })
      })
      .catch(function () {
        // 无审批权限时后端会返回 403，提示已统一弹出
      })
      .then(function () {
        that.setData({ loading: false })
        if (done) done()
      })
  },

  cancelApplication: function (e) {
    const that = this
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '撤回申请',
      content: '撤回后该单据进入终态，不可再审批，确定撤回？',
      success: function (res) {
        if (!res.confirm) return
        api.post('/api/applications/' + id + '/cancel', {})
          .then(function () {
            wx.showToast({ title: '已撤回', icon: 'success' })
            that.reload()
          })
          .catch(function () { })
      }
    })
  },

  approveApplication: function (e) {
    const that = this
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '通过申请',
      content: '确认通过该申请？',
      confirmText: '通过',
      success: function (res) {
        if (!res.confirm) return
        api.post('/api/applications/' + id + '/approve', { action: 'APPROVE', comment: '' })
          .then(function () {
            wx.showToast({ title: '已通过', icon: 'success' })
            that.reload()
          })
          .catch(function () { })
      }
    })
  },

  rejectApplication: function (e) {
    const that = this
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '驳回申请',
      editable: true,
      placeholderText: '驳回必须填写审批意见',
      success: function (res) {
        if (!res.confirm) return
        const comment = (res.content || '').trim()
        if (!comment) {
          wx.showToast({ title: '驳回必须填写审批意见', icon: 'none' })
          return
        }
        api.post('/api/applications/' + id + '/approve', { action: 'REJECT', comment: comment })
          .then(function () {
            wx.showToast({ title: '已驳回', icon: 'success' })
            that.reload()
          })
          .catch(function () { })
      }
    })
  },

  goApply: function () {
    wx.navigateTo({ url: '/pages/apply/apply' })
  }
})
