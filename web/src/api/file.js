import request from '@/utils/request'

/**
 * 文件接口（阶段 6：受控存储 + 鉴权下载）。
 *
 * 说明：下载走的是 /api/files/{id}/download，只传 id 不传路径——
 * 能不能下载由后端按可见范围判定（PUBLIC 全员 / DEPT 同部门 / PRIVATE 仅上传人），
 * 前端不做"看起来能下载"的假判断。
 */

/**
 * 下载文件，返回 Blob。
 *
 * @param {number} id sys_file 主键
 * @returns {Promise<Blob>}
 */
export async function downloadFile(id) {
  const blob = await request({
    url: `/files/${id}/download`,
    method: 'get',
    responseType: 'blob',
    timeout: 60000
  })
  // responseType=blob 会把后端的错误 JSON 也变成 Blob，
  // 不解析出来的话用户点下载会"毫无反应"，看不出是被鉴权拦了。
  if (blob && blob.type && blob.type.includes('application/json')) {
    const text = await blob.text()
    let body = {}
    try {
      body = JSON.parse(text)
    } catch (e) {
      body = { message: '下载失败' }
    }
    throw { code: body.code, message: body.message || '下载失败', data: body.data }
  }
  return blob
}

/** 我的文件列表（管理员可看全部）；不下发存储路径 */
export function fetchFiles() {
  return request({ url: '/files', method: 'get' })
}

/**
 * 通用上传。
 *
 * @param {File} file
 * @param {string} visibility PUBLIC / DEPT / PRIVATE
 * @param {number|null} ownerDeptId visibility=DEPT 时必填
 */
export function uploadFile(file, visibility = 'PRIVATE', ownerDeptId = null) {
  const form = new FormData()
  form.append('file', file)
  form.append('visibility', visibility)
  if (ownerDeptId) {
    form.append('ownerDeptId', ownerDeptId)
  }
  return request({
    url: '/files',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function deleteFile(id) {
  return request({ url: `/files/${id}`, method: 'delete' })
}
