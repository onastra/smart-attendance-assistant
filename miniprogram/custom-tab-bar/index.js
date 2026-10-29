// 自定义 tabBar：4 个普通 tab + 中央凸起"扫码"大按钮
// 中央按钮不切页，直接拉起摄像头扫码打卡（结果页为打卡 tab）
const checkin = require('../utils/checkin')

Component({
  data: {
    selected: 0,
    // scan 项只作占位渲染，点击走 scanTap
    list: [
      { pagePath: '/pages/index/index', text: '首页', icon: 'tab-home', iconOn: 'tab-home-on' },
      { pagePath: '/pages/records/records', text: '考勤', icon: 'tab-records', iconOn: 'tab-records-on' },
      { pagePath: '/pages/checkin/checkin', text: '打卡', scan: true },
      { pagePath: '/pages/assistant/assistant', text: '助手', icon: 'tab-ai', iconOn: 'tab-ai-on' },
      { pagePath: '/pages/profile/profile', text: '我的', icon: 'tab-me', iconOn: 'tab-me-on' }
    ],
    scanning: false
  },

  methods: {
    // 各 tab 页在 onShow 里调用 getTabBar().setSelected(i) 同步高亮
    setSelected: function (idx) {
      this.setData({ selected: idx })
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
      checkin.scanAndSubmit({
        onResult: function (result) {
          // 结果统一落到"打卡"tab 展示（含成功卡片与失败 toast）
          try {
            wx.setStorageSync('lz_last_checkin_result', result)
          } catch (e) { /* 存储异常不影响主流程 */ }
          wx.switchTab({ url: '/pages/checkin/checkin' })
        }
      })
      // scanCode 的相机页关闭后才会继续执行到这里（成功/取消都走 fail/success 回调）
      setTimeout(function () { that.setData({ scanning: false }) }, 1500)
    }
  }
})
