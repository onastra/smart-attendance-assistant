<template>
  <div class="chat-page">
    <!-- 会话侧栏 -->
    <aside class="conv-panel">
      <el-button type="primary" class="new-conv" @click="startNew">+ 新会话</el-button>
      <div class="conv-list">
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === conversationId }"
          @click="openConversation(c.id)"
        >
          <span class="conv-title">{{ c.title }}</span>
          <el-icon class="conv-del" @click.stop="removeConversation(c.id)"><Delete /></el-icon>
        </div>
        <el-empty v-if="!conversations.length" description="暂无会话" :image-size="40" />
      </div>
    </aside>

    <!-- 聊天主区 -->
    <main class="chat-main">
      <el-alert v-if="llm && !llm.available" type="warning" :closable="false" class="mode-alert">
        <template #title>检索模式（retrieval-only）：{{ llm.notice }}</template>
      </el-alert>
      <el-alert v-else-if="llm" type="success" :closable="false" class="mode-alert">
        <template #title>
          生成增强已启用：{{ llm.model }}<template v-if="llm.vectorEnabled">，语义检索已开启</template>
        </template>
      </el-alert>

      <div ref="msgBox" class="msg-list">
        <div v-if="!messages.length" class="msg-empty">
          <el-empty description="输入问题开始对话；答案只基于已上传且你有权阅读的文档" :image-size="60" />
        </div>

        <template v-for="m in messages" :key="m.key">
          <!-- 用户消息 -->
          <div v-if="m.role === 'user'" class="msg-row user">
            <div class="bubble user-bubble">{{ m.content }}</div>
          </div>
          <!-- 助手消息 -->
          <div v-else class="msg-row assistant">
            <div class="bubble assistant-bubble">
              <div class="msg-meta">
                <el-tag v-if="m.done" size="small" :type="m.grounded ? 'success' : 'warning'">
                  {{ m.grounded ? '有检索依据' : '无足够依据' }}
                </el-tag>
                <el-tag v-if="m.done" size="small" :type="m.mode === 'llm-augmented' ? 'primary' : 'info'">
                  {{ m.mode === 'llm-augmented' ? '生成增强' : '检索模式' }}
                </el-tag>
                <span v-if="m.streaming" class="meta-text">正在生成…</span>
                <span v-else-if="m.latencyMs != null" class="meta-text">{{ m.latencyMs }} ms</span>
              </div>
              <div class="msg-text" :class="{ muted: m.done && !m.grounded }">
                {{ m.content }}<span v-if="m.streaming" class="cursor">▌</span>
              </div>
              <div v-if="m.errorNote" class="error-note">{{ m.errorNote }}</div>

              <template v-if="m.sources && m.sources.length">
                <el-divider content-position="left">来源引用</el-divider>
                <div v-for="(s, i) in m.sources" :key="s.chunkId" class="source-item">
                  <div class="source-head">
                    <span class="source-index">[{{ i + 1 }}]</span>
                    <span class="source-title">{{ s.documentTitle }}</span>
                    <span class="source-section">{{ s.section }}</span>
                    <el-tag v-if="s.matchType" size="small" effect="plain" class="match-tag">
                      {{ matchLabel(s.matchType) }}
                    </el-tag>
                  </div>
                  <div class="source-excerpt">{{ s.excerpt }}</div>
                </div>
              </template>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <div class="input-bar">
        <el-select v-model="documentId" clearable placeholder="全部文档" class="doc-select">
          <el-option v-for="d in documents" :key="d.id" :label="d.title" :value="d.id" />
        </el-select>
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          maxlength="500"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="例如：上班时间是几点？迟到怎么算？（Enter 发送，Shift+Enter 换行）"
          @keydown.enter.exact.prevent="send"
        />
        <el-button v-if="streaming" @click="cancelStream">停止</el-button>
        <el-button v-else type="primary" :disabled="!question.trim()" @click="send">发送</el-button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import {
  askStream,
  deleteConversation,
  fetchConversationMessages,
  fetchConversations,
  fetchDocuments,
  fetchLlmStatus
} from '@/api/kb'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const question = ref('')
const documentId = ref(null)
const documents = ref([])
const llm = ref(null)

const conversations = ref([])
const conversationId = ref(null)
const messages = ref([])
const streaming = ref(false)
const msgBox = ref(null)
let streamController = null
let keySeq = 0

function matchLabel(t) {
  return { keyword: '关键词', vector: '语义', both: '双路命中' }[t] || t
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

function startNew() {
  if (streaming.value) return
  conversationId.value = null
  messages.value = []
}

async function loadConversations() {
  try {
    const data = await fetchConversations()
    conversations.value = data.list || []
  } catch {
    conversations.value = []
  }
}

async function openConversation(id) {
  if (streaming.value) return
  conversationId.value = id
  try {
    const data = await fetchConversationMessages(id)
    messages.value = (data.list || []).map((m) => ({
      key: 'h' + m.id,
      role: m.role,
      content: m.content,
      sources: m.sources ? JSON.parse(m.sources) : [],
      mode: m.mode,
      grounded: true,
      done: true
    }))
    scrollBottom()
  } catch {
    messages.value = []
  }
}

async function removeConversation(id) {
  try {
    await ElMessageBox.confirm('删除该会话及其全部消息？', '删除会话', { type: 'warning' })
  } catch {
    return
  }
  await deleteConversation(id)
  if (conversationId.value === id) startNew()
  loadConversations()
}

function send() {
  const text = question.value.trim()
  if (!text || streaming.value) return
  question.value = ''

  messages.value.push({ key: 'u' + ++keySeq, role: 'user', content: text })
  const assistantMsg = {
    key: 'a' + ++keySeq,
    role: 'assistant',
    content: '',
    sources: [],
    streaming: true,
    done: false,
    grounded: true,
    mode: null,
    latencyMs: null,
    errorNote: ''
  }
  messages.value.push(assistantMsg)
  streaming.value = true
  scrollBottom()

  streamController = askStream(
    { question: text, documentId: documentId.value, conversationId: conversationId.value },
    {
      onMeta(meta) {
        conversationId.value = meta.conversationId
        assistantMsg.sources = meta.sources || []
        assistantMsg.grounded = meta.grounded
        assistantMsg.mode = meta.mode
      },
      onDelta(delta) {
        assistantMsg.content += typeof delta === 'string' ? delta : ''
        scrollBottom()
      },
      onDone(resp) {
        assistantMsg.streaming = false
        assistantMsg.done = true
        assistantMsg.mode = resp.mode
        assistantMsg.grounded = resp.grounded
        assistantMsg.latencyMs = resp.latencyMs
        streaming.value = false
        streamController = null
        loadConversations()
        scrollBottom()
      },
      onError(message) {
        assistantMsg.streaming = false
        assistantMsg.done = true
        assistantMsg.errorNote = assistantMsg.content
          ? `生成中断：${message}（以上回答不完整）`
          : `生成失败：${message}`
        if (!assistantMsg.content) assistantMsg.content = '本轮回答失败，请重试。'
        streaming.value = false
        streamController = null
      }
    },
    userStore.token
  )
}

function cancelStream() {
  streamController?.abort()
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'assistant' && last.streaming) {
    last.streaming = false
    last.done = true
    last.errorNote = '已手动停止（以上回答不完整）'
    if (!last.content) last.content = '已停止生成。'
  }
  streaming.value = false
  streamController = null
}

onMounted(async () => {
  try {
    llm.value = await fetchLlmStatus()
  } catch {
    llm.value = null
  }
  try {
    const data = await fetchDocuments({ pageNum: 1, pageSize: 100 })
    documents.value = data.list || []
  } catch {
    documents.value = []
  }
  loadConversations()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 12px;
  height: calc(100vh - 120px);
}
.conv-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.new-conv {
  width: 100%;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 4px;
}
.conv-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.conv-item:hover {
  background: var(--el-fill-color-light);
}
.conv-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-del {
  visibility: hidden;
  margin-left: 4px;
}
.conv-item:hover .conv-del {
  visibility: visible;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.mode-alert :deep(.el-alert__title) {
  font-size: 13px;
  line-height: 1.5;
  white-space: normal;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-empty {
  margin: auto;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.7;
}
.user-bubble {
  background: var(--el-color-primary);
  color: #fff;
  white-space: pre-wrap;
}
.assistant-bubble {
  background: var(--el-fill-color-lighter);
}
.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.meta-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.msg-text {
  white-space: pre-wrap;
}
.msg-text.muted {
  color: var(--el-text-color-secondary);
}
.cursor {
  color: var(--el-color-primary);
  animation: blink 0.9s step-end infinite;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.error-note {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-danger);
}
.source-item {
  margin-bottom: 10px;
}
.source-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.source-index {
  color: var(--el-color-primary);
  font-weight: 600;
}
.source-title {
  font-weight: 600;
}
.source-section {
  color: var(--el-text-color-secondary);
}
.match-tag {
  margin-left: auto;
}
.source-excerpt {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  padding-left: 24px;
}
.input-bar {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.doc-select {
  width: 180px;
  flex-shrink: 0;
}
.input-bar .el-button {
  flex-shrink: 0;
}
</style>
