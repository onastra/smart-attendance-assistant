<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>人脸注册名单</span>
        <el-tag size="small" type="info">仅系统管理员可见 · 只存特征向量，不存照片</el-tag>
      </div>
    </template>

    <el-form inline size="small" class="filter-form">
      <el-form-item label="关键词">
        <el-input v-model="keyword" clearable placeholder="姓名 / 工号" style="width: 200px"
                  @keyup.enter="load" @clear="load"/>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe size="small">
      <el-table-column label="员工" min-width="140">
        <template #default="{ row }">{{ row.employeeName }}（{{ row.empNo }}）</template>
      </el-table-column>
      <el-table-column label="识别引擎" width="100" prop="provider"/>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '有效' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模拟数据" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.mockFlag === 1" size="small" type="warning">模拟</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="160" prop="enrollTime"/>
      <el-table-column label="特征摘要" min-width="140">
        <template #default="{ row }">
          <span class="feature-preview">{{ (row.featureHash || '').slice(0, 12) }}…</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-popconfirm v-if="row.status === 1" title="删除后该员工需重新注册才能刷脸打卡，确认删除？"
                         @confirm="onRemove(row)">
            <template #reference>
              <el-button size="small" type="danger" link>删除注册</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pagination" background layout="total, prev, pager, next"
                   :total="total" :current-page="pageNum" :page-size="pageSize"
                   @current-change="onPageChange"/>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteFaceFeature, fetchFaceFeatures } from '@/api/face'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 10

async function load() {
  loading.value = true
  try {
    const data = await fetchFaceFeatures({
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  pageNum.value = page
  load()
}

async function onRemove(row) {
  await deleteFaceFeature(row.employeeId)
  ElMessage.success(`已删除 ${row.employeeName} 的人脸注册`)
  load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filter-form {
  margin-bottom: 8px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.feature-preview {
  font-family: monospace;
  color: #909399;
}
</style>
