<template>
  <div class="setting-page">
    <el-card shadow="never" class="setting-card">
      <template #header>
        <div class="card-header">
          <span>上下班时间与判定口径</span>
          <el-tag size="small" type="info">全局生效 · 保存后立即生效，无需重启</el-tag>
        </div>
      </template>

      <el-form :model="form" label-width="110px" v-loading="loading" style="max-width: 560px">
        <el-form-item label="上班时间" required>
          <el-time-select v-model="form.workStartTime" start="00:00" step="00:05" end="23:55"
                          placeholder="选择上班时间" style="width: 160px"/>
        </el-form-item>
        <el-form-item label="下班时间" required>
          <el-time-select v-model="form.workEndTime" start="00:00" step="00:05" end="23:55"
                          placeholder="选择下班时间" style="width: 160px"/>
        </el-form-item>
        <el-form-item label="迟到宽限">
          <el-input-number v-model="form.lateGraceMinutes" :min="0" :max="240" :step="5"/>
          <span class="unit">分钟</span>
        </el-form-item>
        <el-form-item label="早退宽限">
          <el-input-number v-model="form.earlyLeaveGraceMinutes" :min="0" :max="240" :step="5"/>
          <span class="unit">分钟</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions :column="1" border size="small" class="rule-desc" style="max-width: 560px">
        <el-descriptions-item label="正常">
          上班卡不晚于「上班时间 + 迟到宽限」，且下班卡不早于「下班时间 − 早退宽限」
        </el-descriptions-item>
        <el-descriptions-item label="迟到">
          上班卡晚于「上班时间 + {{ form.lateGraceMinutes }} 分钟」
        </el-descriptions-item>
        <el-descriptions-item label="早退">
          下班卡早于「下班时间 − {{ form.earlyLeaveGraceMinutes }} 分钟」
        </el-descriptions-item>
        <el-descriptions-item label="异常">
          无效打卡（非本人场所、超出围栏被拒、伪造码等失败尝试不产生结果，只记操作日志）
        </el-descriptions-item>
        <el-descriptions-item label="外勤">
          外勤打卡不参与迟到/早退判定，一律记正常
        </el-descriptions-item>
      </el-descriptions>
      <p class="apply-note">
        当前配置对所有员工统一生效（单班次口径）；「我的考勤」日汇总可在
        数据看板页点击「重算」按新口径重新判定历史记录。
      </p>
    </el-card>

    <el-card shadow="never" v-if="meta.updatedAt">
      <template #header><span>最近修改</span></template>
      <el-descriptions :column="3" size="small">
        <el-descriptions-item label="修改时间">{{ formatTime(meta.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="修改人">{{ meta.updatedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前口径">
          {{ form.workStartTime }} ~ {{ form.workEndTime }}
          （迟到宽限 {{ form.lateGraceMinutes }} 分钟 / 早退宽限 {{ form.earlyLeaveGraceMinutes }} 分钟）
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAttendanceSettings, updateAttendanceSettings } from '@/api/attendance'

const loading = ref(false)
const saving = ref(false)
const meta = reactive({ updatedAt: null, updatedByName: '' })
const form = reactive({
  workStartTime: '09:00',
  workEndTime: '18:00',
  lateGraceMinutes: 15,
  earlyLeaveGraceMinutes: 0
})

async function load() {
  loading.value = true
  try {
    const data = await getAttendanceSettings()
    form.workStartTime = normalizeTime(data.workStartTime)
    form.workEndTime = normalizeTime(data.workEndTime)
    form.lateGraceMinutes = data.lateGraceMinutes
    form.earlyLeaveGraceMinutes = data.earlyLeaveGraceMinutes
    meta.updatedAt = data.updatedAt
    meta.updatedByName = data.updatedByName || ''
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!form.workStartTime || !form.workEndTime) {
    ElMessage.warning('请选择上班与下班时间')
    return
  }
  if (form.workEndTime <= form.workStartTime) {
    ElMessage.warning('下班时间必须晚于上班时间')
    return
  }
  saving.value = true
  try {
    const data = await updateAttendanceSettings({ ...form })
    ElMessage.success('已保存，打卡判定立即按新口径生效')
    meta.updatedAt = data.updatedAt
    meta.updatedByName = data.updatedByName || ''
  } finally {
    saving.value = false
  }
}

/** 后端返回 LocalTime 可能带秒（如 09:00:00），统一截成 HH:mm 供时间选择器使用 */
function normalizeTime(value) {
  const s = String(value || '')
  return s.length >= 5 ? s.slice(0, 5) : s
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

onMounted(load)
</script>

<style scoped>
.setting-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.rule-desc {
  margin-top: 8px;
}

.apply-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
