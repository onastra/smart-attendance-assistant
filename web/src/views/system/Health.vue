<template>
  <div class="page-card">
    <h3>系统与依赖状态</h3>
    <p class="page-tip">
      该页面展示后端 <code>/api/system/health</code> 的原始返回。数据库连通性是真实探测结果；
      外部能力（Redis、Nacos、大模型、地图、人脸）按"如实展示"原则，未配置即显示未启用，不伪造可用状态。
    </p>
    <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    <pre class="json-view">{{ raw }}</pre>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getHealth } from '@/api/system'

const loading = ref(false)
const raw = ref('尚未获取数据')

const load = async () => {
  loading.value = true
  try {
    const data = await getHealth()
    raw.value = JSON.stringify(data, null, 2)
  } catch (e) {
    raw.value = '请求失败：' + (e?.message || '未知错误')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.json-view {
  margin-top: 16px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>
