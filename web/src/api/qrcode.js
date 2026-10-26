import request from '@/utils/request'

/**
 * 考勤二维码接口（后端 lz-attendance 模块）。
 */
export function issueQrcode(data) {
  return request.post('/qrcodes', data)
}

export function pageQrcodes(params) {
  return request.get('/qrcodes', { params })
}

export function invalidateQrcode(id) {
  return request.post(`/qrcodes/${id}/invalidate`)
}

/**
 * 取二维码 PNG。
 *
 * <p>不能直接用 <img src="/api/qrcodes/1/image">：该接口需要登录态，
 * 而 img 标签发出的请求不会带上 Authorization 头，会被后端判为未登录。
 * 这里改为带 token 取回二进制再转成临时 URL。</p>
 */
export async function fetchQrcodeImage(id) {
  const userStore = (await import('@/stores/user')).useUserStore()
  const response = await fetch(`/api/qrcodes/${id}/image`, {
    headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}
  })
  if (!response.ok) {
    throw new Error(`二维码图片获取失败（HTTP ${response.status}）`)
  }
  const blob = await response.blob()
  return URL.createObjectURL(blob)
}
