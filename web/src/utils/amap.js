/**
 * 高德 JS API 2.0 动态加载器。
 *
 * <p><strong>Key 来源约定</strong>：本文件与整个 web/src 下都不允许出现
 * 硬编码 Key（公开仓库防泄漏，verify-phaseF.mjs 有 grep 自查）。
 * Key 与安全密钥的唯一来源是后端 /api/places/map-status——
 * 后端从 application-local.yml（gitignored）读出后下发，
 * 前端拿到后按需动态注入 &lt;script&gt;。</p>
 *
 * <p>JS API Key 本质上"前端可见"（任何地图网站的 Key 都能从网络面板看到），
 * 真正的防线是高德控制台为该 Key 配置的域名白名单；本机开发期需把
 * http://localhost:5173 加入白名单，详见 docs/setup-guide.md。</p>
 *
 * <p>高德 2021-12 后申请的 Key 必须配套「安全密钥」，
 * 通过 window._AMapSecurityConfig 在 SDK 加载前注入（未配置时后端不下发该字段）。</p>
 */

const AMAP_JS_URL = 'https://webapi.amap.com/maps?v=2.0'
/** 选址抽屉需要的插件：POI 搜索 + 输入提示 */
const AMAP_PLUGINS = 'AMap.PlaceSearch,AMap.AutoComplete'

/** 单例 Promise：同一页面只允许注入一次 SDK script */
let loadPromise = null

/**
 * 动态加载高德 JS API，成功后 resolve 全局 AMap 对象。
 *
 * @param {{apiKey: string, securityJsCode?: string}} status
 *        map-status 接口返回的 Key 与安全密钥（可选）
 * @returns {Promise<typeof window.AMap>}
 */
export function loadAMap({ apiKey, securityJsCode } = {}) {
  if (!apiKey) {
    return Promise.reject(new Error('未配置高德 Key，无法加载地图'))
  }
  if (loadPromise) {
    return loadPromise
  }
  loadPromise = new Promise((resolve, reject) => {
    if (typeof window === 'undefined') {
      loadPromise = null
      reject(new Error('非浏览器环境'))
      return
    }
    // 已被别处加载过（如热更新残留）：直接复用
    if (window.AMap) {
      resolve(window.AMap)
      return
    }
    if (securityJsCode) {
      // 必须在 SDK script 加载前设置，否则安全校验不生效
      window._AMapSecurityConfig = { securityJsCode }
    }
    const script = document.createElement('script')
    script.src = `${AMAP_JS_URL}&key=${encodeURIComponent(apiKey)}&plugin=${AMAP_PLUGINS}`
    script.async = true
    script.onload = () => {
      if (window.AMap) {
        resolve(window.AMap)
      } else {
        // 加载成功但全局对象缺失：多为 Key 非法被控制台拒绝
        loadPromise = null
        reject(new Error('高德 JS API 加载完成但未注册全局对象，请检查 Key 是否有效（Web端 JS API 类型）'))
      }
    }
    script.onerror = () => {
      // 允许失败后重试：清掉单例，下一次调用重新注入
      loadPromise = null
      reject(new Error('高德 JS API 加载失败（检查网络、Key 域名白名单是否包含当前站点）'))
    }
    document.head.appendChild(script)
  })
  return loadPromise
}
