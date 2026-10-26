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

    <!-- 绑定员工 -->
    <el-dialog v-model="bindVisible" :title="`绑定员工 — ${current.placeName || ''}`" width="700px">
      <el-alert type="warning" :closable="false" class="dialog-tip"
                title="未接入地图服务"
                description="因此无法按地理位置自动推荐员工，请从下方名单手动勾选。这是真实能力边界，不做假装。"/>
      <el-transfer v-model="boundIds" :data="allEmployees" :titles="['未绑定员工', '已绑定员工']"
                   filterable :filter-method="filterEmployee" filter-placeholder="输入姓名或工号"
                   :props="{ key: 'id', label: 'label' }" @change="handleTransferChange"/>
      <p v-if="!allEmployees.length" class="empty-tip">暂无可选员工，请先在「员工管理」建档。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  pagePlaces, createPlace, updatePlace, changePlaceStatus, getMapStatus,
  listPlaceEmployees, bindEmployees, unbindEmployee
} from '@/api/place'
import { listCompanies } from '@/api/org'
import { pageEmployees } from '@/api/employee'

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
const openBind = async (row) => {
  Object.assign(current, { id: row.id, placeName: row.placeName })
  boundIds.value = []
  try {
    const bound = await listPlaceEmployees(row.id) || []
    const all = await pageEmployees({ pageNum: 1, pageSize: 100, status: 1 }) || {}
    allEmployees.value = (all.list || []).map((e) => ({
      id: e.id,
      label: `${e.name}（${e.empNo}）`,
      disabled: false
    }))
    boundIds.value = bound.map((e) => e.employeeId)
  } catch (e) {
    ElMessage.error(e?.message || '加载员工列表失败')
  }
  bindVisible.value = true
}

const handleTransferChange = async (newTargetKeys, direction, movedKeys) => {
  try {
    if (direction === 'right') {
      await bindEmployees(current.id, movedKeys)
      ElMessage.success('已绑定')
    } else {
      for (const id of movedKeys) {
        await unbindEmployee(current.id, id)
      }
      ElMessage.success('已解绑')
    }
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
    openBind(current)
  }
}

const filterEmployee = (queryStr, item) => item.label.includes(queryStr)

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

.empty-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}
</style>
