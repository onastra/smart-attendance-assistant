<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>上传知识文档</span>
          <span class="hint">支持 PDF / DOCX / XLSX / PPTX / DOC / XLS / PPT / TXT / MD，单文件 ≤ 10 MB</span>
        </div>
      </template>

      <el-form :inline="true" class="upload-bar">
        <el-form-item>
          <input ref="fileInput" type="file" class="file-input" @change="onFileChange" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="visibility" size="default" style="width: 130px">
            <el-option label="全员公开" value="PUBLIC" />
            <el-option label="指定部门" value="DEPT" />
            <el-option label="仅自己" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="visibility === 'DEPT'" label="部门">
          <el-select v-model="ownerDeptId" size="default" style="width: 180px" placeholder="请选择部门">
            <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" :disabled="!file" @click="submitUpload">
            上传并索引
          </el-button>
        </el-form-item>
        <el-form-item v-if="file">
          <span class="file-name">{{ file.name }}（{{ formatSize(file.size) }}）</span>
        </el-form-item>
      </el-form>

      <el-alert v-if="lastUpload" :type="lastUpload.ok ? 'success' : 'error'" :closable="false" class="result-alert">
        <template #title>
          {{ lastUpload.ok ? '上传成功' : '上传失败' }}：{{ lastUpload.text }}
        </template>
      </el-alert>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>文档列表</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="title" label="文档" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="可见范围" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="visibilityType(row.visibility)">
              {{ visibilityText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            <div v-if="row.status === 'FAILED'" class="fail-reason">{{ row.parseMessage }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="80" />
        <el-table-column prop="createdByName" label="上传人" width="90" />
        <el-table-column prop="createdAt" label="上传时间" width="150" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="rebuild(row)">重建</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!row.fileId"
              :title="row.fileId ? '下载原文（走鉴权接口）' : '该文档没有留存原文件'"
              @click="download(row)"
            >
              下载原文
            </el-button>
            <el-button link type="primary" size="small" @click="openVisibility(row)">可见范围</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有上传任何文档" :image-size="60" />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="(p) => { query.pageNum = p; load() }"
      />
    </el-card>

    <el-dialog v-model="visDialog" title="修改可见范围" width="420px">
      <el-form label-width="90px">
        <el-form-item label="文档">
          <span>{{ current?.title }}</span>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="visForm.visibility" style="width: 100%">
            <el-option label="全员公开" value="PUBLIC" />
            <el-option label="指定部门" value="DEPT" />
            <el-option label="仅自己" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="visForm.visibility === 'DEPT'" label="部门">
          <el-select v-model="visForm.ownerDeptId" style="width: 100%" placeholder="请选择部门">
            <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visDialog = false">取消</el-button>
        <el-button type="primary" @click="submitVisibility">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDocuments, uploadDocument, deleteDocument, rebuildDocument, updateVisibility } from '@/api/kb'
import { downloadFile } from '@/api/file'
import { listDepartments } from '@/api/org'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const uploading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10 })
const departments = ref([])

const file = ref(null)
const fileInput = ref(null)
const visibility = ref('PUBLIC')
const ownerDeptId = ref(null)
const lastUpload = ref(null)

const visDialog = ref(false)
const current = ref(null)
const visForm = reactive({ visibility: 'PUBLIC', ownerDeptId: null })

function onFileChange(event) {
  file.value = event.target.files?.[0] || null
  lastUpload.value = null
}

function formatSize(size) {
  const bytes = Number(size || 0)
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

async function submitUpload() {
  if (!file.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    const data = await uploadDocument(file.value, visibility.value, ownerDeptId.value)
    const doc = data.document
    // 解析失败要如实说：文档已入库但检索不到，用户必须知道原因
    lastUpload.value = {
      ok: doc.status === 'READY',
      text: doc.status === 'READY'
        ? `已切分为 ${doc.chunkCount} 个片段，可开始提问`
        : `解析失败：${doc.parseMessage || '未知原因'}（该文档不会参与检索）`
    }
    file.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    await load()
  } catch (e) {
    lastUpload.value = { ok: false, text: e?.message || '上传失败' }
  } finally {
    uploading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await fetchDocuments(query)
    rows.value = data.list || []
    total.value = data.total || 0
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function rebuild(row) {
  try {
    const data = await rebuildDocument(row.id)
    ElMessage.success(`重建完成，切片数 ${data.document.chunkCount}`)
    await load()
  } catch { /* 失败原因已由 request 拦截器提示 */ }
}

/**
 * 下载原文。
 *
 * 走的是鉴权接口：后端按可见范围判定，不是"知道 id 就能下"。
 * 文件名用文档标题重建，不依赖响应头里的 Content-Disposition——
 * 跨域部署时该响应头默认不暴露给 JS，靠它取名字会在生产环境失效。
 */
async function download(row) {
  try {
    const blob = await downloadFile(row.fileId)
    const ext = (row.fileType || '').toLowerCase()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ext ? `${row.title}.${ext}` : row.title
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (err) {
    if (err && err.message) {
      ElMessage.error(err.message)
    }
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除《${row.title}》会连带删除其全部切片，且不可恢复。确认删除？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteDocument(row.id)
    ElMessage.success('已删除')
    await load()
  } catch { /* 同上 */ }
}

function openVisibility(row) {
  current.value = row
  visForm.visibility = row.visibility
  visForm.ownerDeptId = row.ownerDeptId
  visDialog.value = true
}

async function submitVisibility() {
  try {
    await updateVisibility(current.value.id, visForm.visibility, visForm.ownerDeptId)
    ElMessage.success('已更新')
    visDialog.value = false
    await load()
  } catch { /* 同上 */ }
}

function statusType(status) {
  return { READY: 'success', PARSING: 'warning', FAILED: 'danger' }[status] || 'info'
}

function statusText(status) {
  return { READY: '已索引', PARSING: '解析中', FAILED: '解析失败' }[status] || status
}

function visibilityType(visibility) {
  return { PUBLIC: 'success', DEPT: 'warning', PRIVATE: 'info' }[visibility] || 'info'
}

function visibilityText(row) {
  if (row.visibility === 'DEPT') {
    return row.ownerDeptName || '指定部门'
  }
  return { PUBLIC: '全员', PRIVATE: '仅自己' }[row.visibility] || row.visibility
}

onMounted(async () => {
  await load()
  try {
    departments.value = await listDepartments() || []
  } catch {
    departments.value = []
  }
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
.upload-bar {
  margin-bottom: 4px;
}
.file-input {
  font-size: 13px;
}
.file-name {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.result-alert {
  margin-top: 8px;
}
.fail-reason {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-color-danger);
  line-height: 1.3;
}
.table-card :deep(.el-card__header) {
  padding: 12px 16px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
