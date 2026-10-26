<template>
  <div class="page">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ============ 公司 ============ -->
        <el-tab-pane label="公司" name="company">
          <div class="tab-bar">
            <el-button v-if="can('system:company:create')" type="primary" :icon="Plus" size="small"
                       @click="openCompanyCreate">新建公司</el-button>
          </div>
          <el-table :data="companies" v-loading="loading" stripe border empty-text="暂无公司">
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column prop="companyName" label="公司名称" min-width="180"/>
            <el-table-column prop="companyCode" label="编码" width="150"/>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip/>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="can('system:company:update')" link type="primary"
                           @click="openCompanyEdit(row)">编辑</el-button>
                <el-button v-if="can('system:company:delete')" link type="danger"
                           @click="removeCompany(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 部门 ============ -->
        <el-tab-pane label="部门" name="dept">
          <div class="tab-bar">
            <el-select v-model="filterCompanyId" placeholder="全部公司" clearable size="small"
                       style="width: 180px" @change="loadDepartments">
              <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
            </el-select>
            <el-button v-if="can('system:dept:create')" type="primary" :icon="Plus" size="small"
                       @click="openDeptCreate">新建部门</el-button>
          </div>
          <el-table :data="deptTree" v-loading="deptLoading" row-key="id" border stripe
                    default-expand-all empty-text="暂无部门">
            <el-table-column prop="deptName" label="部门名称" min-width="200"/>
            <el-table-column prop="companyName" label="所属公司" min-width="160"/>
            <el-table-column prop="leaderName" label="负责人" width="110"/>
            <el-table-column prop="sortNo" label="排序" width="80"/>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="can('system:dept:update')" link type="primary"
                           @click="openDeptEdit(row)">编辑</el-button>
                <el-button v-if="can('system:dept:delete')" link type="danger"
                           @click="removeDept(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 岗位 ============ -->
        <el-tab-pane label="岗位" name="position">
          <div class="tab-bar">
            <el-button v-if="can('system:position:create')" type="primary" :icon="Plus" size="small"
                       @click="openPosCreate">新建岗位</el-button>
          </div>
          <el-table :data="positions" v-loading="posLoading" stripe border empty-text="暂无岗位">
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column prop="positionName" label="岗位名称" min-width="180"/>
            <el-table-column prop="companyName" label="所属公司" min-width="180"/>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="can('system:position:update')" link type="primary"
                           @click="openPosEdit(row)">编辑</el-button>
                <el-button v-if="can('system:position:delete')" link type="danger"
                           @click="removePos(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 公司表单 -->
    <el-dialog v-model="companyDialog" :title="editingCompany ? '编辑公司' : '新建公司'" width="480px">
      <el-form :model="companyForm" label-width="90px">
        <el-form-item label="公司名称" required>
          <el-input v-model="companyForm.companyName" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="公司编码" required>
          <el-input v-model="companyForm.companyCode" :disabled="editingCompany" placeholder="必填，唯一"/>
        </el-form-item>
        <el-form-item label="上级公司">
          <el-input-number v-model="companyForm.parentId" :min="0" controls-position="right"/>
          <span class="form-hint">0 表示总公司</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="companyForm.remark" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="companyDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCompany">保存</el-button>
      </template>
    </el-dialog>

    <!-- 部门表单 -->
    <el-dialog v-model="deptDialog" :title="editingDept ? '编辑部门' : '新建部门'" width="480px">
      <el-form :model="deptForm" label-width="90px">
        <el-form-item label="部门名称" required>
          <el-input v-model="deptForm.deptName" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="所属公司" required>
          <el-select v-model="deptForm.companyId" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" placeholder="顶级部门填 0" style="width: 100%">
            <el-option :label="'顶级部门（0）'" :value="0"/>
            <el-option v-for="d in flatDepartments" :key="d.id" :label="d.deptName" :value="d.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="deptForm.sortNo" :min="0" controls-position="right"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDept">保存</el-button>
      </template>
    </el-dialog>

    <!-- 岗位表单 -->
    <el-dialog v-model="posDialog" :title="editingPos ? '编辑岗位' : '新建岗位'" width="450px">
      <el-form :model="posForm" label-width="90px">
        <el-form-item label="岗位名称" required>
          <el-input v-model="posForm.positionName" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="所属公司">
          <el-select v-model="posForm.companyId" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="posDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPos">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  listCompanies, createCompany, updateCompany, deleteCompany,
  listDepartments, departmentTree, createDepartment, updateDepartment, deleteDepartment,
  listPositions, createPosition, updatePosition, deletePosition
} from '@/api/org'

const userStore = useUserStore()
const can = (code) => userStore.hasPermission(code)

const activeTab = ref('company')
const loading = ref(false)
const deptLoading = ref(false)
const posLoading = ref(false)
const submitting = ref(false)

const companies = ref([])
const departments = ref([])
const deptTree = ref([])
const flatDepartments = ref([])
const positions = ref([])
const filterCompanyId = ref(null)

// 公司表单
const companyDialog = ref(false)
const editingCompany = ref(false)
const companyForm = reactive({ id: null, companyName: '', companyCode: '', parentId: 0, remark: '' })

// 部门表单
const deptDialog = ref(false)
const editingDept = ref(false)
const deptForm = reactive({ id: null, deptName: '', companyId: null, parentId: 0, sortNo: 0 })

// 岗位表单
const posDialog = ref(false)
const editingPos = ref(false)
const posForm = reactive({ id: null, positionName: '', companyId: null })

const loadCompanies = async () => {
  loading.value = true
  try {
    companies.value = await listCompanies() || []
  } finally {
    loading.value = false
  }
}

const loadDepartments = async () => {
  deptLoading.value = true
  try {
    flatDepartments.value = await listDepartments(filterCompanyId.value) || []
    deptTree.value = await departmentTree(filterCompanyId.value) || []
  } finally {
    deptLoading.value = false
  }
}

const loadPositions = async () => {
  posLoading.value = true
  try {
    positions.value = await listPositions() || []
  } finally {
    posLoading.value = false
  }
}

// ---- 公司操作 ----
const openCompanyCreate = () => {
  editingCompany.value = false
  Object.assign(companyForm, { id: null, companyName: '', companyCode: '', parentId: 0, remark: '' })
  companyDialog.value = true
}
const openCompanyEdit = (row) => {
  editingCompany.value = true
  Object.assign(companyForm, {
    id: row.id, companyName: row.companyName, companyCode: row.companyCode,
    parentId: row.parentId, remark: row.remark
  })
  companyDialog.value = true
}
const submitCompany = async () => {
  if (!companyForm.companyName) return ElMessage.warning('请填写公司名称')
  if (!companyForm.companyCode) return ElMessage.warning('请填写公司编码')
  submitting.value = true
  try {
    if (editingCompany.value) {
      await updateCompany(companyForm.id, { companyName: companyForm.companyName, remark: companyForm.remark })
    } else {
      await createCompany(companyForm)
    }
    ElMessage.success('保存成功')
    companyDialog.value = false
    loadCompanies()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
const removeCompany = async (row) => {
  try {
    await ElMessageBox.confirm(`确认停用公司「${row.companyName}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteCompany(row.id)
    ElMessage.success('已停用')
    loadCompanies()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ---- 部门操作 ----
const openDeptCreate = () => {
  editingDept.value = false
  Object.assign(deptForm, { id: null, deptName: '', companyId: null, parentId: 0, sortNo: 0 })
  deptDialog.value = true
}
const openDeptEdit = (row) => {
  editingDept.value = true
  Object.assign(deptForm, {
    id: row.id, deptName: row.deptName, companyId: row.companyId,
    parentId: row.parentId, sortNo: row.sortNo
  })
  deptDialog.value = true
}
const submitDept = async () => {
  if (!deptForm.deptName) return ElMessage.warning('请填写部门名称')
  if (!deptForm.companyId) return ElMessage.warning('请选择所属公司')
  submitting.value = true
  try {
    if (editingDept.value) {
      await updateDepartment(deptForm.id, deptForm)
    } else {
      await createDepartment(deptForm)
    }
    ElMessage.success('保存成功')
    deptDialog.value = false
    loadDepartments()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
const removeDept = async (row) => {
  try {
    await ElMessageBox.confirm(`确认停用部门「${row.deptName}」？有子部门或仍在职员工时会被拒绝。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteDepartment(row.id)
    ElMessage.success('已停用')
    loadDepartments()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ---- 岗位操作 ----
const openPosCreate = () => {
  editingPos.value = false
  Object.assign(posForm, { id: null, positionName: '', companyId: null })
  posDialog.value = true
}
const openPosEdit = (row) => {
  editingPos.value = true
  Object.assign(posForm, { id: row.id, positionName: row.positionName, companyId: row.companyId })
  posDialog.value = true
}
const submitPos = async () => {
  if (!posForm.positionName) return ElMessage.warning('请填写岗位名称')
  submitting.value = true
  try {
    if (editingPos.value) {
      await updatePosition(posForm.id, posForm)
    } else {
      await createPosition(posForm)
    }
    ElMessage.success('保存成功')
    posDialog.value = false
    loadPositions()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
const removePos = async (row) => {
  try {
    await ElMessageBox.confirm(`确认停用岗位「${row.positionName}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deletePosition(row.id)
    ElMessage.success('已停用')
    loadPositions()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(() => {
  loadCompanies()
  loadDepartments()
  loadPositions()
})
</script>

<style scoped>
.tab-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
