<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>我的考勤记录</span>
        <el-tag size="small" type="info">数据范围：仅本人（服务端强制过滤）</el-tag>
      </div>
    </template>

    <el-form :model="query" inline size="small" class="filter-form">
      <el-form-item label="日期范围">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px"/>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.checkinType" clearable placeholder="全部" style="width: 120px">
          <el-option label="上班" value="ON_DUTY"/>
          <el-option label="下班" value="OFF_DUTY"/>
          <el-option label="外勤" value="FIELD"/>
        </el-select>
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="query.result" clearable placeholder="全部" style="width: 120px">
          <el-option label="正常" value="NORMAL"/>
          <el-option label="迟到" value="LATE"/>
          <el-option label="早退" value="EARLY"/>
          <el-option label="异常" value="ABNORMAL"/>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe size="small">
      <el-table-column label="打卡时间" min-width="160" prop="checkinTime"/>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.checkinTypeText }}</template>
      </el-table-column>
      <el-table-column label="场所" min-width="140" prop="placeName"/>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="resultType(row.result)">{{ row.resultText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="90" prop="source"/>
      <el-table-column label="坐标" min-width="150">
        <template #default="{ row }">
          {{ row.lng && row.lat ? `${row.lng}, ${row.lat}` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="180" prop="remark" show-overflow-tooltip/>
    </el-table>
    <el-pagination class="pagination" background layout="total, prev, pager, next"
                   :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
                   @current-change="onPageChange"/>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { fetchMyRecords } from '@/api/attendance'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, checkinType: null, result: null })

async function load() {
  loading.value = true
  try {
    const data = await fetchMyRecords({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      checkinType: query.checkinType || undefined,
      result: query.result || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1]
    })
    list.value = data.page?.list || []
    total.value = data.page?.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  query.pageNum = page
  load()
}

function resultType(result) {
  return { NORMAL: 'success', LATE: 'warning', EARLY: 'warning', ABNORMAL: 'danger' }[result] || 'info'
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
</style>
