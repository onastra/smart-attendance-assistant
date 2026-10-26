import request from '@/utils/request'

/**
 * 知识库接口（模块 F）。
 *
 * 说明：问答响应里的 mode 与 grounded 必须原样展示给用户——
 * mode=retrieval-only 表示没有模型参与生成，grounded=false 表示检索没有足够依据。
 * 把这两项藏起来，用户就会以为系统"知道"答案，而实际上它只是没话说。
 */

/**
 * 上传文档。
 *
 * @param {File} file
 * @param {string} visibility PUBLIC / DEPT / PRIVATE
 * @param {number|null} ownerDeptId visibility=DEPT 时必填
 */
export function uploadDocument(file, visibility = 'PUBLIC', ownerDeptId = null) {
  const form = new FormData()
  form.append('file', file)
  form.append('visibility', visibility)
  if (ownerDeptId) {
    form.append('ownerDeptId', ownerDeptId)
  }
  return request({
    url: '/kb/documents',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function fetchDocuments(params) {
  return request({ url: '/kb/documents', method: 'get', params })
}

export function deleteDocument(id) {
  return request({ url: `/kb/documents/${id}`, method: 'delete' })
}

export function rebuildDocument(id) {
  return request({ url: `/kb/documents/${id}/rebuild`, method: 'post' })
}

export function updateVisibility(id, visibility, ownerDeptId = null) {
  return request({
    url: `/kb/documents/${id}/visibility`,
    method: 'put',
    params: { visibility, ownerDeptId }
  })
}

export function askQuestion(question, documentId = null, topK = null) {
  return request({ url: '/kb/ask', method: 'post', data: { question, documentId, topK } })
}

export function fetchLlmStatus() {
  return request({ url: '/kb/llm-status', method: 'get' })
}

export function fetchQaLogs(params) {
  return request({ url: '/kb/qa-logs', method: 'get', params })
}
