<template>
  <div class="dashboard">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline size="small">
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始" end-placeholder="结束" style="width: 230px"/>
        </el-form-item>
        <el-form-item label="趋势粒度">
          <el-radio-group v-model="query.type">
            <el-radio-button label="day">按日</el-radio-button>
            <el-radio-button label="week">按周</el-radio-button>
            <el-radio-button label="month">按月</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadAll">查询</el-button>
        </el-form-item>
        <el-form-item v-if="canRebuild">
          <el-button :loading="rebuilding" @click="rebuild">重算日汇总</el-button>
        </el-form-item>
        <el-form-item>
          <el-tag size="small" :type="scopeTagType">数据范围：{{ scopeText }}</el-tag>
          <el-tag size="small" class="source-tag" :type="sourceTagType">{{ sourceText }}</el-tag>
        </el-form-item>
      </el-form>
      <el-alert v-if="summary.dataSourceNote" :type="sourceTagType" :closable="false" show-icon
                class="source-note">
        <template #title>数据来源：{{ summary.dataSourceNote }}</template>
      </el-alert>
    </el-card>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.label" :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :class="card.tone">{{ card.value }}<span class="stat-unit">{{ card.unit }}</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="isEmpty" description="该日期范围内没有汇总数据 —— 可点击「重算日汇总」后再查询"/>

    <template v-else>
      <el-row :gutter="16">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><span>考勤趋势（{{ trendTypeText }}）</span></template>
            <div ref="trendRef" class="chart chart-main"/>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never">
            <template #header><span>出勤构成</span></template>
            <div ref="pieRef" class="chart chart-main"/>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="chart-row">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span>部门对比</span></template>
            <div ref="deptRef" class="chart chart-sub"/>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>场所时段分布</span>
                <el-select v-model="query.placeId" clearable placeholder="全部场所" size="small"
                           style="width: 160px" @change="onPlaceChange">
                  <el-option v-for="p in places" :key="p.placeId" :label="p.placeName" :value="p.placeId"/>
                </el-select>
              </div>
            </template>
            <div ref="placeRef" class="chart chart-sub"/>
            <div class="hint">{{ heatNote }}</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchDepartmentStat, fetchPlaceHeat, fetchSummary, fetchTrend, rebuildDailyStat } from '@/api/statistics'
import { pagePlaces } from '@/api/place'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const trendRef = ref(null)
const pieRef = ref(null)
const deptRef = ref(null)
const placeRef = ref(null)
let trendChart = null
let pieChart = null
let deptChart = null
let placeChart = null

const loading = ref(false)
const rebuilding = ref(false)
const dateRange = ref([])
const places = ref([])
const heatNote = ref('')
const heatData = ref({ hours: [], places: [] })
const dataScope = ref('ALL')
const trendPoints = ref([])
const deptItems = ref([])
const summary = reactive({
  personDays: 0, attendanceRate: 0, checkinCount: 0, lateDays: 0, earlyDays: 0,
  absentDays: 0, avgWorkMinutes: 0, normalDays: 0, dataSource: 'real', dataSourceNote: ''
})

const query = reactive({ type: 'day', placeId: null })

const canRebuild = computed(() => userStore.hasPermission('statistics:rebuild'))
const isEmpty = computed(() => summary.personDays === 0)

const scopeText = computed(() => ({ ALL: '全部', DEPT: '本部门', SELF: '仅本人' }[dataScope.value] || dataScope.value))
const scopeTagType = computed(() => (dataScope.value === 'ALL' ? 'success' : 'warning'))
const sourceTagType = computed(() => (
  { real: 'success', mixed: 'warning', sample: 'danger' }[summary.dataSource] || 'info'
))
const sourceText = computed(() => (
  { real: '真实数据', mixed: '含演示数据', sample: '演示数据' }[summary.dataSource] || summary.dataSource
))
const trendTypeText = computed(() => ({ day: '按日', week: '按周', month: '按月' }[query.type]))

/** 平均工时以小时展示：分钟数在卡片上不够直观 */
const statCards = computed(() => [
  { label: '出勤率', value: summary.attendanceRate, unit: '%', tone: 'tone-primary' },
  { label: '打卡次数', value: summary.checkinCount, unit: ' 次', tone: '' },
  { label: '迟到', value: summary.lateDays, unit: ' 人天', tone: 'tone-warn' },
  { label: '早退', value: summary.earlyDays, unit: ' 人天', tone: 'tone-warn' },
  { label: '缺勤', value: summary.absentDays, unit: ' 人天', tone: 'tone-danger' },
  { label: '平均工时', value: (summary.avgWorkMinutes / 60).toFixed(1), unit: ' 小时', tone: '' }
])

function params() {
  return {
    dateFrom: dateRange.value?.[0],
    dateTo: dateRange.value?.[1],
    type: query.type,
    placeId: query.placeId || undefined
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [sumData, trendData, deptData] = await Promise.all([
      fetchSummary(params()),
      fetchTrend(params()),
      fetchDepartmentStat(params())
    ])
    Object.assign(summary, sumData.summary || {})
    dataScope.value = sumData.dataScope || 'ALL'
    trendPoints.value = trendData.points || []
    deptItems.value = deptData.items || []
    await loadPlaceHeat()
    await nextTick()
    // 空态时图表 DOM 被 v-if 移除，此时没有可渲染的画布
    if (!isEmpty.value) {
      renderCharts()
      renderPlace()
    }
  } finally {
    loading.value = false
  }
}

/** 只取数据不渲染：首次加载时图表 DOM 可能还没创建（空态与图表区互斥渲染） */
async function loadPlaceHeat() {
  const data = await fetchPlaceHeat(params())
  heatNote.value = data.note || ''
  heatData.value = { hours: data.hours || [], places: data.places || [] }
}

async function onPlaceChange() {
  await loadPlaceHeat()
  await nextTick()
  renderPlace()
}

async function rebuild() {
  rebuilding.value = true
  try {
    const data = await rebuildDailyStat({
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1]
    })
    ElMessage.success(`重算完成：${data.days} 天 / ${data.affectedRows} 行（幂等，重复执行结果一致）`)
    await loadAll()
  } finally {
    rebuilding.value = false
  }
}

/**
 * 重新绑定图表实例。
 *
 * 空态与图表区是互斥渲染的（v-if / v-else）：从"没有数据"切到"有数据"时，
 * canvas 是新创建的 DOM，旧实例还挂在被销毁的节点上，直接 setOption 不会报错
 * 但也画不出来。这里检查实例绑定的 DOM 是否还在文档里，不在就重新 init。
 */
function ensureCharts() {
  if (!trendChart || !trendChart.getDom()?.isConnected) {
    trendChart = trendRef.value ? echarts.init(trendRef.value) : null
  }
  if (!pieChart || !pieChart.getDom()?.isConnected) {
    pieChart = pieRef.value ? echarts.init(pieRef.value) : null
  }
  if (!deptChart || !deptChart.getDom()?.isConnected) {
    deptChart = deptRef.value ? echarts.init(deptRef.value) : null
  }
  if (!placeChart || !placeChart.getDom()?.isConnected) {
    placeChart = placeRef.value ? echarts.init(placeRef.value) : null
  }
}

function renderCharts() {
  ensureCharts()
  renderTrend()
  renderPie()
  renderDept()
}

function renderTrend() {
  const points = trendPoints.value
  if (!trendChart) {
    return
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['打卡次数', '出勤率'], bottom: 0 },
    grid: { left: 48, right: 48, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: points.map((p) => p.period) },
    yAxis: [
      { type: 'value', name: '次数' },
      { type: 'value', name: '出勤率', axisLabel: { formatter: '{value}%' }, max: 100 }
    ],
    series: [
      {
        name: '打卡次数', type: 'bar', data: points.map((p) => p.checkinCount),
        itemStyle: { color: '#409eff' }
      },
      {
        name: '出勤率', type: 'line', yAxisIndex: 1, smooth: true,
        data: points.map((p) => p.attendanceRate), itemStyle: { color: '#67c23a' }
      }
    ]
  }, true)
}

function renderPie() {
  if (!pieChart) {
    return
  }
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人天 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
      data: [
        { name: '正常', value: summary.normalDays, itemStyle: { color: '#67c23a' } },
        { name: '迟到', value: summary.lateDays, itemStyle: { color: '#e6a23c' } },
        { name: '早退', value: summary.earlyDays, itemStyle: { color: '#f0a020' } },
        { name: '缺勤', value: summary.absentDays, itemStyle: { color: '#f56c6c' } }
      ]
    }]
  }, true)
}

function renderDept() {
  if (!deptChart) {
    return
  }
  deptChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['打卡次数', '迟到', '缺勤'], bottom: 0 },
    grid: { left: 48, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: deptItems.value.map((d) => d.deptName) },
    yAxis: { type: 'value', name: '人天' },
    series: [
      { name: '打卡次数', type: 'bar', data: deptItems.value.map((d) => d.checkinCount), itemStyle: { color: '#409eff' } },
      { name: '迟到', type: 'bar', data: deptItems.value.map((d) => d.lateDays), itemStyle: { color: '#e6a23c' } },
      { name: '缺勤', type: 'bar', data: deptItems.value.map((d) => d.absentDays), itemStyle: { color: '#f56c6c' } }
    ]
  }, true)
}

/**
 * 场所时段分布：每个场所一条折线，横轴 0~23 点。
 * 没有地图 Key 画不出真正的热力图层，就用时段折线如实呈现"几点最挤"。
 */
function renderPlace() {
  if (!placeChart) {
    return
  }
  const hours = heatData.value.hours
  const placeTotals = heatData.value.places
  const nameOf = new Map((placeTotals || []).map((p) => [p.placeId, p.placeName]))
  const placeIds = [...new Set((hours || []).map((h) => h.placeId))].slice(0, 5)
  const series = placeIds.map((id, index) => {
    const byHour = new Map(
      (hours || []).filter((h) => h.placeId === id).map((h) => [h.hour, h.count])
    )
    return {
      name: nameOf.get(id) || `场所 ${id}`,
      type: 'line',
      smooth: true,
      data: Array.from({ length: 24 }, (_, h) => byHour.get(h) || 0),
      itemStyle: { color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399'][index % 5] }
    }
  })
  placeChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 48, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, h) => `${h}点`) },
    yAxis: { type: 'value', name: '打卡次数' },
    series: series.length ? series : [{ name: '无数据', type: 'line', data: [] }]
  }, true)
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
  deptChart?.resize()
  placeChart?.resize()
}

onMounted(async () => {
  // 默认最近 14 天：与后端缺省窗口一致，也让补录的演示数据一进来就能看见
  const to = new Date()
  const from = new Date(to.getTime() - 13 * 86400000)
  const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  dateRange.value = [fmt(from), fmt(to)]

  // 图表实例延迟到真正渲染时再创建（ensureCharts）：
  // 首屏若落在空态，图表 DOM 并不存在，此时 init 会拿到 null
  window.addEventListener('resize', onResize)

  try {
    places.value = (await pagePlaces({ pageNum: 1, pageSize: 100 })).list || []
  } catch {
    places.value = []
  }
  await loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  pieChart?.dispose()
  deptChart?.dispose()
  placeChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  margin-bottom: 0;
}

.source-tag {
  margin-left: 8px;
}

.source-note {
  margin-top: 8px;
}

.stat-row {
  margin: 0 !important;
}

.stat-card {
  text-align: center;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.tone-primary {
  color: #409eff;
}

.tone-warn {
  color: #e6a23c;
}

.tone-danger {
  color: #f56c6c;
}

.chart-row {
  margin-top: 0 !important;
}

.chart {
  width: 100%;
}

.chart-main {
  height: 320px;
}

.chart-sub {
  height: 300px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
