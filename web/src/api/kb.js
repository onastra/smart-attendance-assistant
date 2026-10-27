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

export function askQuestion(question, documentId = null, topK = null, conversationId = null) {
  return request({ url: '/kb/ask', method: 'post', data: { question, documentId, topK, conversationId } })
}

export function fetchLlmStatus() {
  return request({ url: '/kb/llm-status', method: 'get' })
}

export function fetchQaLogs(params) {
  return request({ url: '/kb/qa-logs', method: 'get', params })
}

export function fetchConversations(limit = 50) {
  return request({ url: '/kb/conversations', method: 'get', params: { limit } })
}

export function fetchConversationMessages(id) {
  return request({ url: `/kb/conversations/${id}/messages`, method: 'get' })
}

export function deleteConversation(id) {
  return request({ url: `/kb/conversations/${id}`, method: 'delete' })
}

/**
 * 流式提问（POST + SSE）。
 *
 * 后端事件序列：meta（来源与模式）→ delta*（增量文本）→ done / error。
 * 提问体在 body 里，所以不能用 GET 的 EventSource，用 fetch 手动解析事件流。
 *
 * @param {object} payload { question, documentId, topK, conversationId }
 * @param {object} handlers { onMeta, onDelta, onDone, onError }
 * @param {string} token 登录令牌（Bearer）
 * @returns {AbortController} 用于中途取消
 */
export function askStream(payload, handlers, token) {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const controller = new AbortController()
  fetch(`${base}/kb/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload),
    signal: controller.signal
  }).then(async (resp) => {
    if (!resp.ok || !resp.body) {
      handlers.onError?.(`请求失败（HTTP ${resp.status}）`)
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let eventName = ''
    let dataLines = []
    const dispatch = () => {
      if (!eventName && dataLines.length === 0) return
      const raw = dataLines.join('\n')
      dataLines = []
      const name = eventName
      eventName = ''
      try {
        const data = name === 'delta' ? JSON.parse(raw) : JSON.parse(raw)
        if (name === 'meta') handlers.onMeta?.(data)
        else if (name === 'delta') handlers.onDelta?.(data)
        else if (name === 'done') handlers.onDone?.(data)
        else if (name === 'error') handlers.onError?.(data?.message || '生成失败')
      } catch {
        // 半截事件直接丢弃，下一段还能续上
      }
    }
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx).replace(/\r$/, '')
        buffer = buffer.slice(idx + 1)
        if (line === '') dispatch()
        else if (line.startsWith('event:')) eventName = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
      }
    }
    dispatch()
  }).catch((e) => {
    if (e.name !== 'AbortError') {
      handlers.onError?.(e.message || '网络异常')
    }
  })
  return controller
}
