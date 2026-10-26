import request from '@/utils/request'

/**
 * 人脸识别接口（模块 G：适配层）。
 *
 * 每个响应都带 mock 字段。为 true 时表示这条结果来自模拟实现，
 * 前端必须展示"模拟"徽章——把模拟结果呈现得像真实人脸核验，是最不该犯的错。
 */

export function enrollFace(file, employeeId = null) {
  const form = new FormData()
  form.append('image', file)
  if (employeeId) {
    form.append('employeeId', employeeId)
  }
  return request({
    url: '/face/enroll',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function verifyFace(file, employeeId = null) {
  const form = new FormData()
  form.append('image', file)
  if (employeeId) {
    form.append('employeeId', employeeId)
  }
  return request({
    url: '/face/verify',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function fetchFaceStatus() {
  return request({ url: '/face/status', method: 'get' })
}

export function fetchFaceFeatures(params) {
  return request({ url: '/face/features', method: 'get', params })
}
