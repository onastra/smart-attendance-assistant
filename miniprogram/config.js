// 后端服务地址配置 —— 按场景选一个，别混用：
//
// 【场景 A：连线上阿里云 FC（默认，本文件当前生效）】
//   后端已部署在阿里云函数计算（中国香港），Supabase 云端库，任何网络都能访问。
//   微信开发者工具里需勾选「详情 → 本地设置 → 不校验合法域名、web-view（业务域名）、
//   TLS 版本以及 HTTPS 证书」，否则微信会拦掉这个域名。
//   ⚠️ 手机扫码「预览」会强制校验域名白名单（需 ICP 备案域名，香港节点备不了案），
//      所以真机请用「真机调试」（它跟随工具的"不校验合法域名"勾选），不要用「预览」。
//
// 【场景 B：连本机后端（改端口调试时用）】
//   const BASE_URL = 'http://localhost:8088'
//
// 【场景 C：真机连本机后端（同一 Wi-Fi）】
//   const BASE_URL = 'http://192.168.1.100:8088'   // Windows: ipconfig 看 WLAN 的 IPv4
//
// ============================================================
// 📦 正式发布（上传代码 / 提交审核）的硬性要求：
//      · 必须是 https，不能是 http、localhost 或裸 IP
//      · 域名必须已 ICP 备案（中国大陆），并在微信公众平台
//        开发 → 开发管理 → 开发设置 → 服务器域名 → 「request 合法域名」里加白
//    香港节点没有备案，网页端演示不受影响，小程序正式上线需另备一台大陆区域后端。
// ============================================================

// 线上后端：FC 默认 HTTPS 域名（证书有效，立即可用）
const PROD_FC_URL = 'https://lianzhi-efcclhrziw.cn-hongkong.fcapp.run'

// 线上后端：自定义域名（等 lianzhi.autos 配好 HTTPS 证书后改成这个更友好）
// const PROD_DOMAIN_URL = 'https://lianzhi.autos'

const BASE_URL = PROD_FC_URL

module.exports = {
  BASE_URL: BASE_URL
}
