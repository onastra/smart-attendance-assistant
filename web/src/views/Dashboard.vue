<template>
  <div>
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>阶段 1：前后端链路验证</span>
          <el-button type="primary" :loading="loading" @click="loadHealth">检查后端状态</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="应用">{{ health.application || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应用状态">
          <el-tag :type="health.status === 'UP' ? 'success' : 'danger'">{{ health.status || '未知' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="服务端时间">{{ health.serverTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据库">
          <el-tag :type="health.database?.connected ? 'success' : 'danger'">
            {{ health.database?.connected ? '已连接' : '未连接' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="!checked" class="tip" type="info" :closable="false" show-icon
                title="点击右上角按钮，请求会经 Vite 代理转发到 http://localhost:8080/api/system/health"/>
      <el-alert v-else-if="health.database && !health.database.connected" class="tip" type="warning" :closable="false" show-icon
                title="数据库未连接属正常现象"
                :description="`当前尚未提供 MySQL 口令或未执行初始化脚本。错误信息：${health.database.message || '无'}。数据库连通后这里应变为「已连接」。`"/>
    </el-card>

    <el-card class="page-card" style="margin-top: 16px" shadow="never">
      <template #header><span>外部能力开关（如实反映，不做假绿）</span></template>
      <el-table :data="dependencyRows" border size="small">
        <el-table-column prop="name" label="能力" width="120"/>
        <el-table-column prop="state" label="当前状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '未启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明"/>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getHealth } from '@/api/system'

const loading = ref(false)
const checked = ref(false)
const health = ref({})

const loadHealth = async () => {
  loading.value = true
  try {
    health.value = await getHealth()
    checked.value = true
  } catch (e) {
    checked.value = true
  } finally {
    loading.value = false
  }
}

const dependencyRows = computed(() => {
  const deps = health.value.dependencies || {}
  const labels = {
    redis: 'Redis 缓存',
    nacos: 'Nacos 注册中心',
    feign: 'OpenFeign',
    llm: '大模型能力',
    map: '地图服务',
    face: '人脸识别'
  }
  return Object.entries(labels).map(([key, label]) => ({
    name: label,
    enabled: !!(deps[key] && deps[key].enabled),
    note: deps[key]?.note || (key === 'map' && `provider=${deps[key]?.provider || 'none'}`) || '-'
  }))
})

onMounted(loadHealth)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tip {
  margin-top: 16px;
}
</style>
