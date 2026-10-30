// 自定义 tabBar：4 个普通 tab + 中央凸起"扫码"大按钮
// 中央按钮不切页，直接拉起摄像头扫码打卡（结果页为打卡 tab）
// 「考勤」tab 仅对绑定了员工档案的账号显示：管理员等未绑定档案的账号没有个人考勤
const checkin = require('../utils/checkin')
const auth = require('../utils/auth')

// 全量 tab 定义（originIndex 是各页面 onShow 里 setSelected 传的原始索引）
const ALL_TABS = [
  { pagePath: '/pages/index/index', text: '首页', icon: 'tab-home', iconOn: 'tab-home-on', originIndex: 0 },
  { pagePath: '/pages/records/records', text: '考勤', icon: 'tab-records', iconOn: 'tab-records-on', originIndex: 1 },
  { pagePath: '/pages/checkin/checkin', text: '打卡', scan: true, originIndex: 2 },
  { pagePath: '/pages/assistant/assistant', text: '助手', icon: 'tab-ai', iconOn: 'tab-ai-on', originIndex: 3 },
  { pagePath: '/pages/profile/profile', text: '我的', icon: 'tab-me', iconOn: 'tab-me-on', originIndex: 4 }
]

// 按档案绑定情况计算当前应展示的 tab 列表
function tabsForProfile() {
  const profile = auth.getProfile() || {}
  if (profile.employeeId) return ALL_TABS
  return ALL_TABS.filter(function (t) { return t.pagePath !== '/pages/records/records' })
}

Component({
  data: {
    selected: 0,
    // scan 项只作占位渲染，点击走 scanTap
    list: ALL_TABS,
    scanning: false
  },

  methods: {
    // 各 tab 页在 onShow 里调用 getTabBar().setSelected(i) 同步高亮（i 是 originIndex）
    setSelected: function (idx) {
      const list = tabsForProfile()
      let selected = -1
      for (let i = 0; i < list.length; i++) {
        if (list[i].originIndex === idx) { selected = i; break }
      }
      if (selected < 0) selected = 0
      this.setData({ selected: selected, list: list })
    },

    onItemTap: function (e) {
      const idx = Number(e.currentTarget.dataset.index)
      const item = this.data.list[idx]
      if (!item || item.scan) return
      if (this.data.selected === idx) return
      wx.switchTab({ url: item.pagePath })
    },

    scanTap: function () {
      const that = this
      if (this.data.scanning) return
      this.setData({ scanning: true })
      // 扫码后不再弹二维码原文，直接进"确认打卡"页（预校验 → 确认 → 结果页）
      checkin.scanThenConfirm({})
      // scanCode 的相机页关闭后才会继续执行到这里（成功/取消都走 success/fail 回调）
      setTimeout(function () { that.setData({ scanning: false }) }, 1500)
    }
  }
})
