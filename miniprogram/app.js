// 入口：不做全局拦截，登录检查放在各页面 onShow 里（tabBar 页无法用 reLaunch 拦截启动页）
App({
  onLaunch: function () {
    // 预留：后续接入微信登录 / 推送等全局逻辑
  }
})
