<template>
  <div class="page">
    <!-- 地图能力状态：如实告知当前未接入地图服务 -->
    <el-alert v-if="mapStatus" :type="mapStatus.enabled ? 'success' : 'info'" :closable="false" show-icon>
      <template #title>
        地图坐标采集：{{ mapStatus.enabled ? `已接入（${mapStatus.provider}）` : '未接入' }}
      </template>
      <div class="map-note">{{ mapStatus.note }}</div>
    </el-alert>

    <el-card shadow="never">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="场所名称">
          <el-input v-model="query.placeName" placeholder="模糊匹配" clearable style="width: 160px"/>
        </el-form-item>
        <el-form-item label="所属公司">
          <el-select v-model="query.companyId" placeholder="全部" clearable style="width: 170px">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1"/>
            <el-option label="停用" :value="0"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>办公场所</span>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建场所</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" stripe border empty-text="暂无场所">
        <el-table-column prop="placeName" label="场所名称" min-width="170"/>
        <el-table-column prop="companyName" label="所属公司" min-width="150"/>
        <el-table-column prop="placeCode" label="场所码" min-width="180">
          <template #default="{ row }">
            <span class="code-text">{{ row.placeCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="坐标" min-width="160">
          <template #default="{ row }">
            <span v-if="row.lng != null && row.lat != null">{{ row.lng }}, {{ row.lat }}</span>
            <span v-else class="text-muted">未录入</span>
          </template>
        </el-table-column>
        <el-table-column prop="coverRadiusM" label="打卡半径(米)" width="120"/>
        <el-table-column prop="boundEmployeeCount" label="已绑定员工" width="120"/>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canBind" link type="primary" @click="openBind(row)">绑定员工</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canUpdate" link type="danger" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :page-sizes="[10, 20, 50]" :total="total" @size-change="loadList"
                     @current-change="loadList"/>
    </el-card>

    <!-- 新建 / 编辑场所 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑场所' : '新建场所'" width="560px">
      <el-alert v-if="!editing" type="info" :closable="false" class="dialog-tip"
                title="场所码由系统自动生成" description="生成规则：PLACE-<公司ID>-<8位随机>，去掉了易混淆字符便于人工抄写。"/>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="场所名称" prop="placeName">
          <el-input v-model="form.placeName" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="所属公司">
          <el-select v-model="form.companyId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="省市">
          <el-input v-model="form.province" placeholder="省" style="width: 48%"/>
          <el-input v-model="form.city" placeholder="市" style="width: 48%; margin-left: 4%"/>
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" placeholder="街道门牌等"/>
        </el-form-item>
        <el-form-item label="经度 lng">
          <el-input-number v-model="form.lng" :precision="7" :step="0.0001" controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
        <el-form-item label="纬度 lat">
          <el-input-number v-model="form.lat" :precision="7" :step="0.0001" controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
        <el-form-item v-if="mapStatus?.enabled" label-width="110px">
          <el-button type="primary" plain :icon="MapLocation" @click="openMapPicker">地图选址（推荐）</el-button>
          <span class="map-pick-tip">搜索地点或点击地图取点，坐标自动回填</span>
        </el-form-item>
        <el-form-item label="打卡半径(米)">
          <el-input-number v-model="form.coverRadiusM" :min="10" :max="5000" :step="50"
                           controls-position="right" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 地图选址抽屉：Key 由后端 map-status 下发，前端源码零硬编码。
         注意不能用 destroy-on-close：高德 SDK 实例持有容器 DOM 引用，
         销毁后重开地图会指向已脱离文档的旧节点而白屏，这里保持 DOM 常驻复用实例。 -->
    <el-drawer v-model="mapVisible" title="地图选址（高德）" size="720px" :close-on-click-modal="false"
               append-to-body>
      <div class="picker-wrap">
        <div class="picker-search">
          <el-input v-model="searchKeyword" placeholder="输入地点名称，选择联想项或按回车搜索" clearable
                    @input="onKeywordInput" @keyup.enter="onSearchEnter"
                    @blur="hideSuggestLater" @clear="clearSuggest">
            <template #append>
              <el-button :icon="Search" @click="doSearch">搜索</el-button>
            </template>
          </el-input>
          <!-- 地址联想：AutoComplete 的 tips 自渲染，不用高德默认面板（样式无法与本页统一） -->
          <div v-if="suggestions.length" class="picker-suggest">
            <div v-for="(tip, idx) in suggestions" :key="tip.id || idx"
                 class="picker-suggest-item" @mousedown.prevent="chooseSuggestion(tip)">
              <span class="poi-name">{{ tip.name }}</span>
              <span class="poi-addr">{{ [tip.district, tip.address].filter(Boolean).join(' ') }}</span>
            </div>
          </div>
        </div>

        <!-- 加载失败如实告知并保留手动录入兜底，不假装地图可用 -->
        <el-alert v-if="mapError" type="error" :closable="false" show-icon
                  title="地图加载失败" :description="mapError"/>
        <div v-show="!mapError" v-loading="mapLoading" class="picker-map" id="place-map-container"/>

        <div v-if="searchResults.length" class="picker-results">
          <div v-for="(poi, idx) in searchResults" :key="idx" class="picker-result-item"
               @click="choosePoi(poi)">
            <span class="poi-name">{{ poi.name }}</span>
            <span class="poi-addr">{{ poi.address }}</span>
          </div>
        </div>

        <div class="picker-info">
          <template v-if="pickedGcj">
            <div class="picked-line">
              选中点（存库 WGS84）：<strong>{{ pickedWgs84.lng.toFixed(7) }}, {{ pickedWgs84.lat.toFixed(7) }}</strong>
            </div>
            <div class="text-muted picked-sub">
              地图取点为 GCJ-02，已自动换算为 WGS84 存储（与 H5 打卡上报坐标一致）
              <span v-if="pickedGcj.name">· 来自：{{ pickedGcj.name }}</span>
            </div>
          </template>
          <div v-else class="text-muted">点击地图任意位置或拖拽蓝色标记以选点</div>

          <div class="picker-actions">
            <span class="radius-label">打卡半径(米)</span>
            <el-input-number v-model="form.coverRadiusM" :min="10" :max="5000" :step="50"
                             controls-position="right" style="width: 140px"/>
            <el-button type="primary" :disabled="!pickedGcj" @click="confirmPick">使用该坐标</el-button>
          </div>
          <div class="text-muted picked-sub">
            蓝色圆即真实打卡范围（半径 {{ form.coverRadiusM || 200 }} 米，直径约
            {{ ((form.coverRadiusM || 200) * 2 / 1000).toFixed(1) }} 公里），
            距离以真实地面距离为准，可用左下角比例尺核对；圆顶部的文字标注随输入联动。
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 绑定员工：双面板，行内一键绑定/解绑，无需先勾选再点箭头 -->
    <el-dialog v-model="bindVisible" :title="`绑定员工 — ${current.placeName || ''}`" width="780px">
      <el-input v-model="bindKeyword" class="bind-search" placeholder="搜索姓名或工号，两个列表同时过滤"
                clearable :prefix-icon="Search"/>
      <div class="bind-layout">
        <!-- 待绑定 -->
        <div class="bind-panel">
          <div class="bind-panel-header">
            <span class="bind-panel-title">待绑定员工</span>
            <el-tag size="small" round type="info" effect="plain">{{ unboundList.length }}</el-tag>
          </div>
          <div class="bind-list">
            <div v-if="!unboundList.length" class="bind-empty">全部员工都已绑定</div>
            <div v-for="e in unboundList" :key="e.id" class="bind-item">
              <span class="bind-avatar">{{ e.name.charAt(0) }}</span>
              <span class="bind-name">{{ e.name }}<small>（{{ e.empNo }}）</small></span>
              <el-button size="small" type="primary" plain round :disabled="bindBusy"
                         @click="doBind(e)">绑定</el-button>
            </div>
          </div>
        </div>
        <!-- 已绑定 -->
        <div class="bind-panel is-bound">
          <div class="bind-panel-header">
            <span class="bind-panel-title">已绑定员工</span>
            <el-tag size="small" round type="success" effect="plain">{{ boundList.length }}</el-tag>
          </div>
          <div class="bind-list">
            <div v-if="!boundList.length" class="bind-empty">还没有绑定员工，从左侧一键绑定</div>
            <div v-for="e in boundList" :key="e.id" class="bind-item">
              <span class="bind-avatar is-bound">{{ e.name.charAt(0) }}</span>
              <span class="bind-name">{{ e.name }}<small>（{{ e.empNo }}）</small></span>
              <el-button size="small" type="danger" plain round :disabled="bindBusy"
                         @click="doUnbind(e)">解绑</el-button>
            </div>
          </div>
        </div>
      </div>
      <p v-if="!allEmployees.length" class="empty-tip">暂无可选员工，请先在「员工管理」建档。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MapLocation } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  pagePlaces, createPlace, updatePlace, changePlaceStatus, getMapStatus,
  listPlaceEmployees, bindEmployees, unbindEmployee
} from '@/api/place'
import { listCompanies } from '@/api/org'
import { pageEmployees } from '@/api/employee'
import { loadAMap } from '@/utils/amap'
import { gcj02ToWgs84, wgs84ToGcj02 } from '@/utils/coordTransform'

const userStore = useUserStore()
const canCreate = userStore.hasPermission('place:create')
const canUpdate = userStore.hasPermission('place:update')
const canBind = userStore.hasPermission('place:bind')

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const bindVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const rows = ref([])
const total = ref(0)
const companies = ref([])
const mapStatus = ref(null)

const current = reactive({ id: null, placeName: '' })
const allEmployees = ref([])
const boundIds = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, placeName: '', companyId: null, status: null })
const emptyForm = () => ({
  id: null, placeName: '', companyId: null, province: '', city: '', address: '',
  lng: null, lat: null, coverRadiusM: 200, remark: ''
})
const form = reactive(emptyForm())

const rules = { placeName: [{ required: true, message: '请输入场所名称', trigger: 'blur' }] }

const loadList = async () => {
  loading.value = true
  try {
    const data = await pagePlaces(query)
    rows.value = data.list || []
    total.value = data.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  try {
    companies.value = await listCompanies() || []
    mapStatus.value = await getMapStatus() || null
  } catch {
    // 状态提示加载失败不影响主流程
  }
}

const resetQuery = () => {
  Object.assign(query, { pageNum: 1, pageSize: 10, placeName: '', companyId: null, status: null })
  loadList()
}

const openCreate = () => {
  editing.value = false
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const openEdit = (row) => {
  editing.value = true
  Object.assign(form, {
    id: row.id, placeName: row.placeName, companyId: row.companyId,
    province: row.province || '', city: row.city || '', address: row.address || '',
    lng: row.lng, lat: row.lat, coverRadiusM: row.coverRadiusM || 200, remark: row.remark || ''
  })
  dialogVisible.value = true
}

const submit = async () => {
  try {
    await formRef.value.validate()
  } catch { return }
  submitting.value = true
  try {
    if (editing.value) {
      await updatePlace(form.id, form)
      ElMessage.success('场所已更新')
    } else {
      await createPlace(form)
      ElMessage.success('场所已创建，场所码由系统生成')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row) => {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${next === 0 ? '停用' : '启用'}场所「${row.placeName}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await changePlaceStatus(row.id, next)
    ElMessage.success('操作成功')
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ---- 绑定员工 ----
const bindKeyword = ref('')
const bindBusy = ref(false)

const openBind = async (row) => {
  Object.assign(current, { id: row.id, placeName: row.placeName })
  bindKeyword.value = ''
  boundIds.value = []
  try {
    const bound = await listPlaceEmployees(row.id) || []
    const all = await pageEmployees({ pageNum: 1, pageSize: 100, status: 1 }) || {}
    allEmployees.value = (all.list || []).map((e) => ({
      id: e.id,
      name: e.name,
      empNo: e.empNo
    }))
    boundIds.value = bound.map((e) => e.employeeId)
  } catch (e) {
    ElMessage.error(e?.message || '加载员工列表失败')
  }
  bindVisible.value = true
}

/** 待绑定 = 全部在职员工里去掉已绑定，再按关键字过滤 */
const unboundList = computed(() => {
  const kw = bindKeyword.value.trim().toLowerCase()
  const boundSet = new Set(boundIds.value)
  return allEmployees.value.filter((e) => {
    if (boundSet.has(e.id)) return false
    return !kw || e.name.toLowerCase().includes(kw) || (e.empNo || '').toLowerCase().includes(kw)
  })
})

const boundList = computed(() => {
  const kw = bindKeyword.value.trim().toLowerCase()
  const order = new Map(boundIds.value.map((id, i) => [id, i]))
  return allEmployees.value
    .filter((e) => order.has(e.id))
    .filter((e) => !kw || e.name.toLowerCase().includes(kw) || (e.empNo || '').toLowerCase().includes(kw))
    .sort((a, b) => order.get(a.id) - order.get(b.id))
})

/** 行内一键绑定：单条调用，成功即把该员工移入右栏 */
const doBind = async (e) => {
  bindBusy.value = true
  try {
    await bindEmployees(current.id, [e.id])
    boundIds.value = [...boundIds.value, e.id]
    ElMessage.success(`已绑定 ${e.name}`)
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '绑定失败')
  } finally {
    bindBusy.value = false
  }
}

const doUnbind = async (e) => {
  bindBusy.value = true
  try {
    await unbindEmployee(current.id, e.id)
    boundIds.value = boundIds.value.filter((id) => id !== e.id)
    ElMessage.success(`已解绑 ${e.name}`)
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '解绑失败')
  } finally {
    bindBusy.value = false
  }
}

// ---- 地图选址（高德 JS API 2.0）----
const mapVisible = ref(false)
const mapLoading = ref(false)
const mapError = ref('')
const searchKeyword = ref('')
const searchResults = ref([])
const suggestions = ref([])
/** 当前选中点，保持地图原始坐标系 GCJ-02；确认时才转 WGS84 存库 */
const pickedGcj = ref(null)

// 地图实例不放进 reactive：SDK 对象内部有循环引用，代理化会导致性能问题甚至报错
let mapInstance = null
let marker = null
let circle = null
/** 圆顶部边缘的半径标注：让"真实距离"一目了然（随半径/圆心联动） */
let radiusLabel = null
let placeSearch = null
/** 输入联想（高德 AutoComplete），与 PlaceSearch 分开实例：前者给候选词，后者给详细 POI */
let autoComplete = null
let suggestTimer = null
/** 请求序号：丢弃过期响应，避免快速输入时旧结果覆盖新结果 */
let suggestSeq = 0
let AMapRef = null

const pickedWgs84 = computed(() => {
  if (!pickedGcj.value) return null
  const w = gcj02ToWgs84(pickedGcj.value.lng, pickedGcj.value.lat)
  return { lng: Number(w.lng.toFixed(7)), lat: Number(w.lat.toFixed(7)) }
})

/** 半径标注跟随圆心 + 半径联动（纬度 1 度 ≈ 110997 米） */
const updateRadiusLabel = () => {
  if (!radiusLabel || !pickedGcj.value) return
  const r = form.coverRadiusM || 200
  radiusLabel.setText(`半径 ${r} 米`)
  radiusLabel.setPosition([pickedGcj.value.lng, pickedGcj.value.lat + r / 110997])
}

const setPick = (lng, lat, name) => {
  pickedGcj.value = { lng, lat, name: name || pickedGcj.value?.name || '' }
  marker.setPosition([lng, lat])
  circle.setCenter([lng, lat])
  updateRadiusLabel()
}

/** 编辑已有场所时，把库里的 WGS84 坐标换算到 GCJ-02 并显示到地图 */
const syncPickFromForm = () => {
  if (form.lng != null && form.lat != null) {
    const g = wgs84ToGcj02(form.lng, form.lat)
    setPick(g.lng, g.lat, '')
    mapInstance.setZoomAndCenter(16, [g.lng, g.lat])
  }
}

const initMap = () => {
  mapInstance = new AMapRef.Map('place-map-container', {
    zoom: 14,
    // 默认视野：北京；有搜索/已有坐标时会立即跳转
    center: [116.397428, 39.90923],
  })
  marker = new AMapRef.Marker({ draggable: true, cursor: 'move' })
  circle = new AMapRef.Circle({
    radius: form.coverRadiusM || 200,
    strokeColor: '#409eff',
    strokeOpacity: 0.7,
    strokeWeight: 2,
    fillColor: '#409eff',
    fillOpacity: 0.12,
  })
  marker.setMap(mapInstance)
  circle.setMap(mapInstance)
  // 比例尺：直接核对圆覆盖的真实地面距离（用户反馈"圆看起来比设置的大"，有比例尺可自证）
  mapInstance.addControl(new AMapRef.Scale({ position: { bottom: '28px', left: '12px' } }))
  // 圆顶部边缘的半径文字标注
  radiusLabel = new AMapRef.Text({
    text: `半径 ${form.coverRadiusM || 200} 米`,
    anchor: 'bottom-center',
    position: [0, 0],
    cursor: 'default',
    style: {
      'background-color': '#ffffff',
      'border': '1px solid #409eff',
      'border-radius': '4px',
      'padding': '1px 6px',
      'font-size': '12px',
      'color': '#1f2d3d',
      'white-space': 'nowrap'
    }
  })
  radiusLabel.setMap(mapInstance)
  // 调试句柄：便于无头浏览器测试与排查（不影响业务）
  if (typeof window !== 'undefined') window.__placePicker = { map: mapInstance, circle, marker }
  placeSearch = new AMapRef.PlaceSearch({ pageSize: 10, pageIndex: 1 })
  // 输入联想：citylimit=false 保证跨城市也能联想（选址不限于某一市）
  autoComplete = new AMapRef.AutoComplete({ city: '全国', citylimit: false })

  mapInstance.on('click', (e) => setPick(e.lnglat.getLng(), e.lnglat.getLat(), ''))
  marker.on('dragend', () => {
    const p = marker.getPosition()
    setPick(p.getLng(), p.getLat(), '')
  })
  syncPickFromForm()
}

const openMapPicker = async () => {
  mapVisible.value = true
  mapError.value = ''
  if (!mapInstance) {
    mapLoading.value = true
    try {
      AMapRef = await loadAMap({
        apiKey: mapStatus.value?.apiKey,
        securityJsCode: mapStatus.value?.securityJsCode,
      })
      await nextTick()
      initMap()
    } catch (e) {
      mapError.value = e?.message || '地图加载失败，请手动录入坐标'
    } finally {
      mapLoading.value = false
    }
  } else {
    syncPickFromForm()
  }
}

/** 输入时防抖 250ms 拉联想词（边打字边请求太密会被高德限流） */
const onKeywordInput = () => {
  const kw = searchKeyword.value.trim()
  clearTimeout(suggestTimer)
  if (!kw) {
    suggestions.value = []
    return
  }
  suggestTimer = setTimeout(() => fetchSuggestions(kw), 250)
}

const fetchSuggestions = (kw) => {
  if (!autoComplete) return
  const seq = ++suggestSeq
  // ⚠️ 与 PlaceSearch 相同：2.0 的 AutoComplete.search 也不返回 Promise，必须回调式；
  // 候选词在 result.tips，部分 tip 的 location 是空串（只精确到区县）
  autoComplete.search(kw, (status, result) => {
    if (seq !== suggestSeq) return // 丢弃过期响应，避免旧结果覆盖新输入
    suggestions.value = status === 'complete' && Array.isArray(result?.tips)
      ? result.tips.filter((t) => t && t.name)
      : []
  })
}

const hideSuggestLater = () => {
  // 延迟隐藏：让联想项的 mousedown 先于 blur 生效
  setTimeout(() => { suggestions.value = [] }, 120)
}

const clearSuggest = () => {
  clearTimeout(suggestTimer)
  suggestSeq++
  suggestions.value = []
}

/** 回车：有联想项时取第一条（更快），否则走全文 POI 搜索 */
const onSearchEnter = () => {
  if (suggestions.value.length) {
    chooseSuggestion(suggestions.value[0])
  } else {
    doSearch()
  }
}

const chooseSuggestion = (tip) => {
  const loc = tip?.location
  const lng = loc && (typeof loc.getLng === 'function' ? loc.getLng() : loc.lng)
  const lat = loc && (typeof loc.getLat === 'function' ? loc.getLat() : loc.lat)
  clearSuggest()
  if (lng && lat) {
    searchKeyword.value = tip.name
    searchResults.value = []
    setPick(lng, lat, tip.name)
    mapInstance?.setZoomAndCenter(16, [lng, lat])
  } else {
    // 联想项没有坐标（如只精确到区县）：退回关键词搜索拿详细 POI
    searchKeyword.value = tip.name
    doSearch()
  }
}

const doSearch = async () => {
  clearSuggest()
  const kw = searchKeyword.value.trim()
  if (!kw || !placeSearch) return
  try {
    // ⚠️ 高德 JS API 2.0 的 PlaceSearch.search() **不返回 Promise**（返回 undefined），
    // 必须用回调式，否则 await 拿到 undefined，永远提示"未搜索到相关地点"；
    // 且 2.0 的结果在 res.poiList.pois（1.4 才是顶层 res.pois）。
    // 2026-10-29 无头浏览器实测：await ps.search(kw) === undefined；
    // 回调式 status=complete，"郑州轻工业大学"返回 61 条在 poiList.pois。
    const result = await new Promise((resolve, reject) => {
      placeSearch.search(kw, (status, res) => {
        if (status === 'complete') {
          resolve(res)
        } else {
          // error/complete 之外的状态：info 携带高德侧真实错误码（如 INVALID_USER_SCODE）
          reject(new Error(res?.info || status || '搜索异常'))
        }
      })
    })
    const pois = result?.poiList?.pois || result?.pois || []
    if (pois.length) {
      searchResults.value = pois.map((p) => ({
        name: p.name,
        address: `${p.pname || ''}${p.cityname || ''}${p.adname || ''}${p.address || ''}`,
        location: p.location,
      }))
    } else {
      searchResults.value = []
      ElMessage.info('未搜索到相关地点')
    }
  } catch (e) {
    ElMessage.error('搜索失败：' + (e?.message || '请稍后重试'))
  }
}

const choosePoi = (poi) => {
  if (!poi?.location) return
  const lng = typeof poi.location.getLng === 'function' ? poi.location.getLng() : poi.location.lng
  const lat = typeof poi.location.getLat === 'function' ? poi.location.getLat() : poi.location.lat
  setPick(lng, lat, poi.name)
  mapInstance.setZoomAndCenter(16, [lng, lat])
  searchResults.value = []
}

const confirmPick = () => {
  const w = pickedWgs84.value
  if (!w) return
  form.lng = w.lng
  form.lat = w.lat
  mapVisible.value = false
  ElMessage.success('已回填坐标（WGS84，与 H5 打卡上报坐标系一致）')
}

// 抽屉里调整半径时实时同步到围栏圆
watch(() => form.coverRadiusM, (v) => {
  if (circle) circle.setRadius(v || 200)
  updateRadiusLabel()
})

onMounted(() => {
  loadList()
  loadOptions()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.map-note {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.code-text {
  font-family: Consolas, monospace;
  font-size: 12px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.dialog-tip {
  margin-bottom: 16px;
}

/* ---- 绑定员工双面板 ---- */
.bind-search {
  margin-bottom: 14px;
}

.bind-layout {
  display: flex;
  gap: 16px;
}

.bind-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.bind-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.bind-panel.is-bound .bind-panel-header {
  background: var(--el-color-success-light-9);
  border-bottom-color: var(--el-color-success-light-7);
}

.bind-panel-title {
  font-weight: 600;
  font-size: 14px;
}

.bind-list {
  height: 380px;
  overflow-y: auto;
  padding: 6px;
}

.bind-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 6px;
  transition: background-color 0.15s;
}

.bind-item + .bind-item {
  margin-top: 2px;
}

.bind-item:hover {
  background: var(--el-fill-color-light);
}

.bind-item .el-button {
  margin-left: auto;
}

.bind-avatar {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}

.bind-avatar.is-bound {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success);
}

.bind-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bind-name small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.bind-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.empty-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}

.map-pick-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ---- 地图选址抽屉 ---- */
.picker-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

/* 联想下拉以输入框为定位基准 */
.picker-search {
  position: relative;
}

.picker-suggest {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 30;
  margin-top: 4px;
  max-height: 240px;
  overflow-y: auto;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  box-shadow: var(--el-box-shadow-light);
}

.picker-suggest-item {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}

.picker-suggest-item:hover {
  background: var(--el-fill-color-light);
}

.picker-suggest-item + .picker-suggest-item {
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 地图容器必须有确定高度，否则 SDK 无法计算画布尺寸、地图不渲染 */
.picker-map {
  width: 100%;
  height: 420px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.picker-results {
  max-height: 160px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.picker-result-item {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}

.picker-result-item:hover {
  background: var(--el-fill-color-light);
}

.picker-result-item + .picker-result-item {
  border-top: 1px solid var(--el-border-color-lighter);
}

.poi-name {
  font-weight: 600;
  margin-right: 8px;
}

.poi-addr {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.picker-info {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.picked-line {
  font-size: 13px;
  margin-bottom: 4px;
}

.picked-sub {
  font-size: 12px;
  margin-bottom: 12px;
}

.picker-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.radius-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
