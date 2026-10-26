import request from '@/utils/request'

/**
 * 认证相关接口。
 */
export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getProfile() {
  return request.get('/auth/profile')
}
