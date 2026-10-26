import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 统一请求实例。
 *
 * 约定：
 * - 后端统一返回 { code, message, data }，code=0 视为成功；
 * - 401 清除本地登录态并跳转登录页；403 明确提示无权限（说明后端已做鉴权，前端只是在解释结果）；
 * - 50301 表示依赖的外部能力未配置（地图 Key、模型 Key 等），必须如实提示，不允许静默降级成假数据。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非 JSON（如二维码 PNG、文件下载）直接透传
    if (body instanceof Blob || body instanceof ArrayBuffer) {
      return body
    }
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      const message = body.message || '请求失败'
      switch (body.code) {
        case 40101: {
          const userStore = useUserStore()
          userStore.logout()
          ElMessage.error('登录状态失效，请重新登录')
          break
        }
        case 40301:
          ElMessage.error('没有访问该资源的权限')
          break
        case 50301:
          ElMessage.warning(message)
          break
        default:
          ElMessage.error(message)
      }
      // 把业务失败透传给调用方，便于页面做差异化处理（例如打卡失败原因展示）
      return Promise.reject({ code: body.code, message, data: body.data })
    }
    return body
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        useUserStore().logout()
        ElMessage.error('登录状态失效，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有访问该资源的权限')
      } else {
        ElMessage.error(`网络异常（HTTP ${status}）`)
      }
    } else {
      ElMessage.error('无法连接后端服务，请确认后端已启动')
    }
    return Promise.reject(error)
  }
)

export default request
