<template>
  <div class="approval-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批中心</span>
          <el-tag size="small" type="info">仅待审批单据可操作；终态单据不可重复审批</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="待我审批" name="todo"/>
        <el-tab-pane v-if="canViewAll" label="全部记录" name="all"/>
      </el-tabs>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="applicationNo" label="单号" width="190" show-overflow-tooltip/>
        <el-table-column prop="employeeName" label="申请人" width="110"/>
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
            <template v-if="row.status === 'SUBMITTED'">
              <el-button link type="success" size="small" @click="openApprove(row, 'APPROVE')">通过</el-button>
              <el-button link type="danger" size="small" @click="openApprove(row, 'REJECT')">驳回</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" background layout="total, prev, pager, next"
                     :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
                     @current-change="onPageChange"/>
    </el-card>

    <el-dialog v-model="approveVisible" title="审批" width="460px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="单号">{{ approveForm.no }}</el-form-item>
        <el-form-item label="决定">
          <el-radio-group v-model="approveForm.action">
            <el-radio value="APPROVE">通过</el-radio>
            <el-radio value="REJECT">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="approveForm.action === 'REJECT' ? '驳回意见' : '审批意见'">
          <el-input v-model="approveForm.comment" type="textarea" :rows="3"
                    :placeholder="approveForm.action === 'REJECT' ? '驳回必须填写意见' : '选填'"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="handleApprove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { approveApplication, pageAllApplications, pageApprovable } from '@/api/approval'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canViewAll = computed(() => userStore.hasPermission('application:list'))

const activeTab = ref('todo')
const loading = ref(false)
const approving = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

const approveVisible = ref(false)
const approveForm = reactive({ id: null, no: '', action: 'APPROVE', comment: '' })

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

async function loadList() {
  loading.value = true
  try {
    const params = { ...query }
    const data = activeTab.value === 'all'
      ? await pageAllApplications(params)
      : await pageApprovable(params)
    list.value = (data.list || []).map((it) => ({
      ...it, startTime: formatTime(it.startTime), endTime: formatTime(it.endTime)
    }))
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  query.pageNum = 1
  loadList()
}

function onPageChange(page) {
  query.pageNum = page
  loadList()
}

function openApprove(row, action) {
  approveForm.id = row.id
  approveForm.no = row.applicationNo
  approveForm.action = action
  approveForm.comment = ''
  approveVisible.value = true
}

async function handleApprove() {
  if (approveForm.action === 'REJECT' && !approveForm.comment.trim()) {
    ElMessage.warning('驳回必须填写审批意见')
    return
  }
  approving.value = true
  try {
    await approveApplication(approveForm.id, {
      action: approveForm.action, comment: approveForm.comment
    })
    ElMessage.success(approveForm.action === 'APPROVE' ? '已通过' : '已驳回')
    approveVisible.value = false
    await loadList()
  } finally {
    approving.value = false
  }
}

onMounted(loadList)
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
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
