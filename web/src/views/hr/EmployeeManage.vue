<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="模糊匹配" clearable style="width: 150px"/>
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="query.empNo" placeholder="精确匹配" clearable style="width: 150px"/>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.deptId" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>员工档案</span>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建员工</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" stripe border empty-text="暂无数据">
        <el-table-column prop="empNo" label="工号" width="130"/>
        <el-table-column prop="name" label="姓名" width="110"/>
        <el-table-column label="性别" width="80">
          <template #default="{ row }">{{ genderText(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="140"/>
        <el-table-column prop="positionName" label="岗位" min-width="130"/>
        <el-table-column prop="phone" label="手机号" width="130"/>
        <el-table-column label="入职日期" width="120">
          <template #default="{ row }">{{ row.hireDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canUpdate" link type="danger" @click="handleDismiss(row)">离职</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :page-sizes="[10, 20, 50]" :total="total" @size-change="loadList"
                     @current-change="loadList"/>
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑员工' : '新建员工'" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="工号" prop="empNo">
          <el-input v-model="form.empNo" :placeholder="editing ? '留空表示不修改' : '留空由系统自动生成'"/>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填，需为 11 位手机号"/>
        </el-form-item>
        <el-form-item label="所属公司">
          <el-select v-model="form.companyId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.positionId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="p in positions" :key="p.id" :label="p.positionName" :value="p.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { pageEmployees, createEmployee, updateEmployee, deleteEmployee } from '@/api/employee'
import { listCompanies, listDepartments, listPositions } from '@/api/org'

const userStore = useUserStore()
const canCreate = userStore.hasPermission('hr:employee:create')
const canUpdate = userStore.hasPermission('hr:employee:update')

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const rows = ref([])
const total = ref(0)
const companies = ref([])
const departments = ref([])
const positions = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, name: '', empNo: '', deptId: null })
const emptyForm = () => ({
  id: null, empNo: '', name: '', gender: 0, phone: '',
  companyId: null, deptId: null, positionId: null, hireDate: ''
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

const genderText = (g) => ({ 1: '男', 2: '女' }[g] || '未知')

const loadList = async () => {
  loading.value = true
  try {
    const data = await pageEmployees(query)
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
    departments.value = await listDepartments() || []
    positions.value = await listPositions() || []
  } catch {
    // 下拉项加载失败不阻塞主流程，表格仍可展示
  }
}

const resetQuery = () => {
  Object.assign(query, { pageNum: 1, pageSize: 10, name: '', empNo: '', deptId: null })
  loadList()
}

const resetForm = () => Object.assign(form, emptyForm())

const openCreate = () => {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  editing.value = true
  Object.assign(form, {
    id: row.id, empNo: row.empNo || '', name: row.name, gender: row.gender ?? 0, phone: row.phone || '',
    companyId: row.companyId, deptId: row.deptId, positionId: row.positionId, hireDate: row.hireDate || ''
  })
  dialogVisible.value = true
}

const submit = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (editing.value) {
      await updateEmployee(form.id, form)
      ElMessage.success('员工信息已更新')
    } else {
      await createEmployee(form)
      ElMessage.success('员工已建档')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleDismiss = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.name}（${row.empNo}）」离职删除？其打卡流水、日统计、申请单等关联数据将一并删除，且不可恢复、无法复职。`,
      '离职删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteEmployee(row.id)
    ElMessage.success(`${row.name} 已离职删除`)
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

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

.search-form {
  margin-bottom: 0;
}

.table-card .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
