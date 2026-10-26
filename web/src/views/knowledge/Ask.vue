<template>
  <div class="page">
    <el-alert v-if="llm && !llm.available" type="warning" :closable="false" class="mode-alert">
      <template #title>
        检索模式（retrieval-only）：{{ llm.notice }}
      </template>
    </el-alert>
    <el-alert v-else-if="llm" type="success" :closable="false" class="mode-alert">
      <template #title>生成增强已启用：{{ llm.model }}</template>
    </el-alert>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>知识库问答</span>
          <span class="hint">答案只基于已上传且你有权阅读的文档；检索不到会明确告知，不做推测</span>
        </div>
      </template>

      <el-form @submit.prevent>
        <el-form-item>
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：上班时间是几点？迟到怎么算？"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="documentId" clearable placeholder="全部文档" style="width: 260px">
            <el-option v-for="d in documents" :key="d.id" :label="d.title" :value="d.id" />
          </el-select>
          <el-button type="primary" :loading="asking" :disabled="!question.trim()" style="margin-left: 8px" @click="ask">
            提问
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="answer" class="answer-area">
        <div class="answer-meta">
          <el-tag size="small" :type="answer.grounded ? 'success' : 'warning'">
            {{ answer.grounded ? '有检索依据' : '无足够依据' }}
          </el-tag>
          <el-tag size="small" :type="answer.mode === 'llm-augmented' ? 'primary' : 'info'">
            {{ answer.mode === 'llm-augmented' ? '生成增强' : '检索模式' }}
          </el-tag>
          <span v-if="answer.topScore" class="meta-text">最高相关度 {{ answer.topScore.toFixed(4) }}</span>
          <span class="meta-text">耗时 {{ answer.latencyMs }} ms</span>
        </div>

        <div class="answer-body" :class="{ muted: !answer.grounded }">{{ answer.answer }}</div>

        <template v-if="answer.sources && answer.sources.length">
          <el-divider content-position="left">来源引用</el-divider>
          <div v-for="(s, i) in answer.sources" :key="s.chunkId" class="source-item">
            <div class="source-head">
              <span class="source-index">[{{ i + 1 }}]</span>
              <span class="source-title">{{ s.documentTitle }}</span>
              <span class="source-section">{{ s.section }}</span>
              <span class="source-score">相关度 {{ Number(s.score).toFixed(4) }}</span>
            </div>
            <div class="source-excerpt">{{ s.excerpt }}</div>
          </div>
        </template>
      </div>

      <el-empty v-else description="输入问题开始提问" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { askQuestion, fetchLlmStatus, fetchDocuments } from '@/api/kb'

const question = ref('')
const documentId = ref(null)
const documents = ref([])
const answer = ref(null)
const asking = ref(false)
const llm = ref(null)

async function ask() {
  asking.value = true
  try {
    answer.value = await askQuestion(question.value.trim(), documentId.value)
  } catch (e) {
    // 50301（模型不可用等）已经在拦截器里提示过，这里不重复弹窗
    answer.value = null
  } finally {
    asking.value = false
  }
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
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mode-alert :deep(.el-alert__title) {
  font-size: 13px;
  line-height: 1.5;
  white-space: normal;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.answer-area {
  margin-top: 8px;
}
.answer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.meta-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.answer-body {
  white-space: pre-wrap;
  line-height: 1.7;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}
.answer-body.muted {
  color: var(--el-text-color-secondary);
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
.source-score {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.source-excerpt {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  padding-left: 24px;
}
</style>
