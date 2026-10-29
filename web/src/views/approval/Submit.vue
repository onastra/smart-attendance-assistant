<template>
  <div class="approval-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>提交请假 / 加班 / 调休申请</span>
          <el-tag size="small" type="info">提交后进入待审批，可随时撤回</el-tag>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" inline label-width="90px">
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 160px" @change="onTypeChange">
            <el-option label="请假" value="LEAVE"/>
            <el-option label="加班" value="OVERTIME"/>
            <el-option label="调休" value="COMPENS"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'LEAVE'" label="假别">
          <el-select v-model="form.subType" style="width: 160px" placeholder="选择假别">
            <el-option label="年假" value="年假"/>
            <el-option label="事假" value="事假"/>
            <el-option label="病假" value="病假"/>
            <el-option label="婚丧假" value="婚丧假"/>
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="form.startTime" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择开始时间" style="width: 220px"/>
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="form.endTime" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择结束时间" style="width: 220px"/>
        </el-form-item>
        <el-form-item label="事由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="1" placeholder="请说明请假/加班原因" style="width: 360px"/>
        </el-form-item>
        <el-form-item label="附件">
          <el-button size="small" @click="pickFile" v-if="!form.attachmentFileId">上传附件</el-button>
          <el-tag v-else size="small" type="success" closable @close="clearAttachment">
            {{ attachmentName || '已上传' }}
          </el-tag>
          <input ref="fileInput" type="file" hidden @change="onFileChange"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="form.type === 'OVERTIME'" type="info" :closable="false" show-icon
                title="加班核算：审批通过后，加班时长按 1:1 自动折算为调休额度（可后续提交「调休」使用）"/>
      <el-alert v-else-if="form.type === 'COMPENS'" type="warning" :closable="false" show-icon>
        <template #title>
          调休余额
          <b>{{ balances ? balances.compensAvailable : '-' }}</b> 小时
          <span v-if="compensOver">，本次申请 {{ formHours }} 小时已超出余额，审批时将无法通过</span>
          <span v-else>（加班审批通过后自动增加，申请调休按实际时长扣减）</span>
        </template>
      </el-alert>
      <el-alert v-else-if="balances" type="info" :closable="false" show-icon>
        <template #title>
          我的额度：年假剩余
          <b>{{ balances.leaveAvailable ?? balances.leaveQuotaHours - balances.leaveUsedHours }}</b> 小时，
          调休可用
          <b>{{ balances.compensAvailable }}</b> 小时（如需用调休抵扣，类型请选「调休」）
        </template>
      </el-alert>
    </el-card>

    <el-card shadow="never">
      <template #header><span>我的申请</span></template>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="applicationNo" label="单号" width="190" show-overflow-tooltip/>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'LEAVE' && row.subType === '调休'" size="small" type="success">调休</el-tag>
            <el-tag v-else size="small" :type="row.type === 'LEAVE' ? 'primary' : 'warning'">
              {{ row.type === 'LEAVE' ? '请假' : '加班' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subType" label="子类" width="120" show-overflow-tooltip/>
        <el-table-column label="起" width="160" prop="startTime"/>
        <el-table-column label="止" width="160" prop="endTime"/>
        <el-table-column label="时长(h)" width="90" prop="durationHours"/>
        <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip/>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="110" show-overflow-tooltip/>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-if="row.attachmentFileId" @click="downloadAttachment(row)">附件</el-button>
            <el-button link type="danger" size="small" :disabled="row.status !== 'SUBMITTED'"
                       @click="handleCancel(row)">撤回</el-button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelApplication,
  fetchMyBalances,
  pageMyApplications,
  submitApplication
} from '@/api/approval'
import { downloadFile, uploadFile } from '@/api/file'

const balances = ref(null)

const formRef = ref()
const fileInput = ref()
const submitting = ref(false)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const attachmentName = ref('')
const query = reactive({ pageNum: 1, pageSize: 10 })

const form = reactive({
  type: 'LEAVE',
  subType: '',
  startTime: '',
  endTime: '',
  reason: '',
  attachmentFileId: null
})

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  reason: [{ required: true, message: '请填写事由', trigger: 'blur' }]
}

function statusText(status) {
  return {
    SUBMITTED: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤回'
  }[status] || status
}

function statusType(status) {
  return {
    SUBMITTED: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info'
  }[status] || 'info'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const result = await uploadFile(file, 'PRIVATE')
    form.attachmentFileId = result.id
    attachmentName.value = file.name
    ElMessage.success('附件已上传')
  } catch (e) {
    ElMessage.error('附件上传失败')
  } finally {
    event.target.value = ''
  }
}

function clearAttachment() {
  form.attachmentFileId = null
  attachmentName.value = ''
}

function onTypeChange() {
  // 切到加班/调休时清掉假别，避免把"年假"当子类提交（调休的子类由提交时固定注入）
  if (form.type !== 'LEAVE') {
    form.subType = ''
  }
}

// 本次申请的时长（与后端 durationHours 同口径：分钟差 / 60，保留 2 位）
const formHours = computed(() => {
  if (!form.startTime || !form.endTime) return 0
  const minutes = (new Date(form.endTime) - new Date(form.startTime)) / 60000
  if (!isFinite(minutes) || minutes <= 0) return 0
  return Math.round((minutes / 60) * 100) / 100
})

// 调休申请是否超出可用余额（仅前端提示，权威校验在审批环节）
const compensOver = computed(() =>
  form.type === 'COMPENS' && !!balances.value && formHours.value > Number(balances.value.compensAvailable || 0)
)

async function handleSubmit() {
  await formRef.value.validate()
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择开始与结束时间')
    return
  }
  submitting.value = true
  try {
    // 「调休」在前端是独立类型，落库复用请假类型 + 固定子类，后端按子类扣调休余额
    const payload = form.type === 'COMPENS'
      ? { ...form, type: 'LEAVE', subType: '调休' }
      : { ...form }
    await submitApplication(payload)
    ElMessage.success('申请已提交')
    Object.assign(form, {
      subType: '', startTime: '', endTime: '', reason: '', attachmentFileId: null
    })
    attachmentName.value = ''
    await loadList()
    await loadBalances()
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row) {
  await ElMessageBox.confirm('撤回后该申请回到草稿状态（此处直接关闭），确认撤回？', '提示', { type: 'warning' })
  await cancelApplication(row.id)
  ElMessage.success('已撤回')
  await loadList()
}

async function downloadAttachment(row) {
  try {
    const blob = await downloadFile(row.attachmentFileId)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await pageMyApplications({ ...query })
    list.value = (data.list || []).map((it) => ({ ...it, startTime: formatTime(it.startTime), endTime: formatTime(it.endTime) }))
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadBalances() {
  try {
    balances.value = await fetchMyBalances()
  } catch {
    balances.value = null
  }
}

function onPageChange(page) {
  query.pageNum = page
  loadList()
}

onMounted(() => {
  loadList()
  loadBalances()
})
</script>

<style scoped>
.approval-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
