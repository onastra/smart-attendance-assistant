// AI 助手：知识库问答（/api/kb/ask，Qwen + 向量检索，带引用来源）
const api = require('../../utils/request')
const auth = require('../../utils/auth')

const SUGGESTIONS = [
  '年假有几天？怎么申请？',
  '迟到会怎么处理？',
  '加班补偿标准是什么？',
  '出差报销流程是什么？',
  '病假需要什么证明？'
]

// 本地会话 id：仅用于页面内区分轮次；后端按 qa-logs 落库
let seq = 0

Page({
  data: {
    messages: [],
    inputValue: '',
    thinking: false,
    llmReady: true,
    scrollTo: '',
    suggestions: SUGGESTIONS
  },

  onShow: function () {
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 同步 tabBar 高亮（索引 3 = 助手）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setSelected(3)
    }
    if (this.data.messages.length === 0) {
      this.checkLlm()
      this.pushAi(
        '你好，我是联智智能助手 👋\n我可以基于公司知识库回答考勤、请假、加班、报销等制度问题，回答会附带来源依据。',
        null
      )
    }
  },

  checkLlm: function () {
    const that = this
    api.get('/api/kb/llm-status')
      .then(function (data) {
        that.setData({ llmReady: !!data.available })
      })
      .catch(function () {
        that.setData({ llmReady: false })
      })
  },

  pushUser: function (text) {
    seq += 1
    const msg = { id: 'm' + seq, role: 'user', text: text }
    this.data.messages.push(msg)
    this.setData({ messages: this.data.messages, scrollTo: msg.id })
    return msg
  },

  pushAi: function (text, meta) {
    seq += 1
    const msg = {
      id: 'm' + seq,
      role: 'ai',
      text: text,
      grounded: meta ? meta.grounded : null,
      latencyText: meta ? meta.latencyText : '',
      sources: meta ? meta.sources : []
    }
    this.data.messages.push(msg)
    this.setData({ messages: this.data.messages, scrollTo: msg.id })
    return msg
  },

  updateLastAi: function (text, meta) {
    const list = this.data.messages
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].role === 'ai') {
        const key = 'messages[' + i + ']'
        const patch = {}
        patch[key + '.text'] = text
        if (meta) {
          patch[key + '.grounded'] = meta.grounded
          patch[key + '.latencyText'] = meta.latencyText
          patch[key + '.sources'] = meta.sources
        }
        this.setData(patch)
        this.setData({ scrollTo: list[i].id })
        return
      }
    }
  },

  onInput: function (e) {
    this.setData({ inputValue: e.detail.value })
  },

  onSuggestionTap: function (e) {
    const q = e.currentTarget.dataset.q
    this.ask(q)
  },

  onSend: function () {
    const q = (this.data.inputValue || '').trim()
    if (!q || this.data.thinking) return
    this.setData({ inputValue: '' })
    this.ask(q)
  },

  ask: function (question) {
    const that = this
    if (this.data.thinking) return
    this.pushUser(question)
    const thinkingMsg = this.pushAi('正在检索知识库…', null)
    this.setData({ thinking: true })
    api.post('/api/kb/ask', { question: question })
      .then(function (data) {
        const sources = (data.sources || []).slice(0, 3).map(function (s) {
          return { title: s.documentTitle || '知识库文档', section: s.section || '' }
        })
        const meta = {
          grounded: data.grounded,
          latencyText: data.latencyMs ? (data.latencyMs / 1000).toFixed(1) + 's' : '',
          sources: sources
        }
        const text = (data.answer || '（后端未返回内容）') + (meta.sources.length > 0 ? '\n\n📎 来源' : '')
        that.updateLastAi(text, meta)
        that.setData({ thinking: false })
      })
      .catch(function () {
        that.updateLastAi('回答失败，请稍后重试。若持续失败，请联系管理员检查 AI 服务配置。', null)
        that.setData({ thinking: false })
      })
  },

  clearChat: function () {
    if (this.data.thinking) return
    this.setData({ messages: [] })
    this.onShow()
  }
})
