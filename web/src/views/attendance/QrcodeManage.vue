<template>
  <div class="qrcode-page">
    <el-card shadow="never" class="issue-card">
      <template #header>
        <div class="card-header">
          <span>签发考勤二维码</span>
          <el-tag size="small" type="info">签名由服务端 HMAC-SHA256 生成，篡改即失效</el-tag>
        </div>
      </template>

      <el-form :model="issueForm" inline label-width="90px">
        <el-form-item label="办公场所" required>
          <el-select v-model="issueForm.placeId" placeholder="请选择场所" filterable style="width: 200px">
            <el-option v-for="p in places" :key="p.id" :label="p.placeName" :value="p.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="issueForm.qrType" style="width: 150px" @change="onTypeChange">
            <el-option label="动态码（120 秒）" value="DYNAMIC"/>
            <el-option label="长期固定码（24 小时）" value="FIXED"/>
          </el-select>
        </el-form-item>
        <el-form-item label="有效期(秒)">
          <el-input-number v-model="issueForm.ttlSeconds" :min="10" :max="604800" :step="30"/>
        </el-form-item>
        <el-form-item label="一次性">
          <el-switch v-model="issueForm.onceOnly" :active-value="1" :inactive-value="0"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="issuing" @click="handleIssue">生成二维码</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
                title="动态码有效期很短，页面会在到期后自动续发，避免被截图转发后长期使用；长期固定码用于固定张贴场景，靠管理员手动失效。"/>
    </el-card>

    <el-card v-if="current.id" shadow="never" class="preview-card">
      <template #header>
        <div class="card-header">
          <span>当前二维码</span>
          <el-tag :type="remainSeconds > 0 ? 'success' : 'danger'" size="small">
            {{ remainSeconds > 0 ? `剩余 ${remainSeconds} 秒` : '已过期' }}
          </el-tag>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :xs="24" :sm="10" class="preview-image">
          <img v-if="imageUrl" :src="imageUrl" alt="考勤二维码" class="qr-image"/>
          <el-skeleton v-else style="width: 240px" animated/>
        </el-col>
        <el-col :xs="24" :sm="14">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="场所">{{ current.placeName }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              {{ current.qrType === 'FIXED' ? '长期固定码' : '动态码' }}
              <el-tag size="small" :type="current.onceOnly === 1 ? 'warning' : 'info'">
                {{ current.onceOnly === 1 ? '一次性' : '有效期内可重复' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="签发时间">{{ formatTime(current.issueTime) }}</el-descriptions-item>
            <el-descriptions-item label="过期时间">{{ formatTime(current.expireTime) }}</el-descriptions-item>
          </el-descriptions>
          <div class="payload-box">
            <div class="payload-label">二维码原文（摄像头不可用时可手动粘贴到打卡页）</div>
            <el-input :model-value="current.payload" readonly type="textarea" :rows="3"/>
            <el-button size="small" class="copy-btn" @click="copyPayload">复制原文</el-button>
          </div>
          <div class="preview-actions">
            <el-button size="small" @click="handleIssue">立即续发</el-button>
            <el-button size="small" type="danger" plain @click="handleInvalidate(current.id)">作废该码</el-button>
            <el-button size="small" type="primary" plain @click="openH5">打开 H5 打卡页</el-button>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header><span>签发记录</span></template>
      <el-form :model="query" inline size="small" class="filter-form">
        <el-form-item label="场所">
          <el-select v-model="query.placeId" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="p in places" :key="p.id" :label="p.placeName" :value="p.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option label="有效" value="VALID"/>
            <el-option label="已失效" value="INVALID"/>
            <el-option label="已过期" value="EXPIRED"/>
            <el-option label="已使用" value="USED"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="placeName" label="场所" min-width="140"/>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.qrType === 'FIXED' ? 'warning' : 'primary'">
              {{ row.qrType === 'FIXED' ? '长期固定码' : '动态码' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="随机串" min-width="180" show-overflow-tooltip/>
        <el-table-column label="签发时间" width="170" prop="issueTime"/>
        <el-table-column label="过期时间" width="170" prop="expireTime"/>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewImage(row)">查看</el-button>
            <el-button link type="danger" size="small" :disabled="row.status !== 'VALID'"
                       @click="handleInvalidate(row.id)">失效</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" background layout="total, prev, pager, next"
                     :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
                     @current-change="onPageChange"/>
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchQrcodeImage, invalidateQrcode, issueQrcode, pageQrcodes } from '@/api/qrcode'
import { pagePlaces } from '@/api/place'

const DEFAULT_TTL = { DYNAMIC: 120, FIXED: 86400 }

const places = ref([])
const issueForm = reactive({
  placeId: null,
  qrType: 'DYNAMIC',
  ttlSeconds: DEFAULT_TTL.DYNAMIC,
  onceOnly: 0
})
const current = ref({})
const imageUrl = ref('')
const remainSeconds = ref(0)
const issuing = ref(false)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, placeId: null, status: null })

let timer = null
let objectUrl = null

function onTypeChange(type) {
  issueForm.ttlSeconds = DEFAULT_TTL[type] || 120
}

async function loadPlaces() {
  const data = await pagePlaces({ pageNum: 1, pageSize: 100 })
  places.value = data.list || []
  if (!issueForm.placeId && places.value.length) {
    issueForm.placeId = places.value[0].id
  }
}

async function handleIssue() {
  if (!issueForm.placeId) {
    ElMessage.warning('请先选择办公场所')
    return
  }
  issuing.value = true
  try {
    const result = await issueQrcode({ ...issueForm })
    current.value = result
    remainSeconds.value = Number(result.ttlSeconds || 0)
    await renderImage(result.id)
    startCountdown()
    loadList()
    ElMessage.success('二维码已签发')
  } finally {
    issuing.value = false
  }
}

async function renderImage(id) {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
  objectUrl = await fetchQrcodeImage(id)
  imageUrl.value = objectUrl
}

function startCountdown() {
  stopCountdown()
  timer = setInterval(async () => {
    if (remainSeconds.value > 0) {
      remainSeconds.value -= 1
      // 动态码到期自动续发，模拟真实场景里"二维码一直在变"
      if (remainSeconds.value === 0 && current.value.qrType === 'DYNAMIC') {
        ElMessage.info('二维码已过期，正在自动续发')
        await handleIssue()
      }
    }
  }, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function viewImage(row) {
  current.value = row
  remainSeconds.value = Number(row.remainSeconds || 0)
  await renderImage(row.id)
}

async function handleInvalidate(id) {
  await ElMessageBox.confirm('作废后该二维码立即不可用于打卡，确认继续？', '提示', { type: 'warning' })
  await invalidateQrcode(id)
  ElMessage.success('二维码已作废')
  if (current.value.id === id) {
    remainSeconds.value = 0
  }
  loadList()
}

async function copyPayload() {
  try {
    await navigator.clipboard.writeText(current.value.payload)
    ElMessage.success('已复制二维码原文')
  } catch (e) {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选中文本框内容复制')
  }
}

function openH5() {
  window.open('/h5/checkin', '_blank')
}

async function loadList() {
  loading.value = true
  try {
    const data = await pageQrcodes({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  query.pageNum = page
  loadList()
}

function statusText(status) {
  return { VALID: '有效', INVALID: '已失效', EXPIRED: '已过期', USED: '已使用' }[status] || status
}

function statusType(status) {
  return { VALID: 'success', INVALID: 'danger', EXPIRED: 'info', USED: 'warning' }[status] || 'info'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

onMounted(async () => {
  await loadPlaces()
  await loadList()
})

onBeforeUnmount(() => {
  stopCountdown()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
})
</script>

<style scoped>
.qrcode-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview-image {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.qr-image {
  width: 240px;
  height: 240px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
}

.payload-box {
  margin-top: 12px;
}

.payload-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.copy-btn {
  margin-top: 8px;
}

.preview-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.filter-form {
  margin-bottom: 8px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
