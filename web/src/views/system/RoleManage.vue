<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>角色与权限</span>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建角色</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" stripe border empty-text="暂无角色">
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="roleCode" label="角色编码" width="140"/>
        <el-table-column prop="roleName" label="角色名称" min-width="150"/>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip/>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canGrant" link type="primary" @click="openGrant(row)">分配权限</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="removeRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑角色 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新建角色'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" :disabled="editing" placeholder="如 MANAGER"/>
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="如 部门主管"/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配 -->
    <el-dialog v-model="grantVisible" :title="`分配权限 — ${current.roleName}`" width="720px">
      <el-alert type="info" :closable="false" class="dialog-tip"
                title="保存时会用勾选结果全量覆盖该角色的原有权限"
                description="因此取消勾选后立即保存即等于回收权限。"/>
      <el-tabs>
        <el-tab-pane v-for="(list, module) in groupedPermissions" :key="module" :label="`${module}（${list.length}）`">
          <el-checkbox v-model="checkAll[module]" :indeterminate="isIndeterminate(module, list)"
                       @change="(val) => handleCheckAll(val, list)">全选</el-checkbox>
          <el-checkbox-group v-model="checkedIds" class="perm-group">
            <el-checkbox v-for="p in list" :key="p.id" :value="p.id" class="perm-item">
              <span class="perm-code">{{ p.permCode }}</span>
              <span class="perm-name">{{ p.permName }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <span class="checked-count">已选 {{ checkedIds.length }} 项</span>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitGrant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  pageRoles, listPermissions, listRolePermissionIds,
  createRole, updateRole, grantRolePermissions, deleteRole
} from '@/api/system'

const userStore = useUserStore()
const canCreate = userStore.hasPermission('system:role:create')
const canUpdate = userStore.hasPermission('system:role:update')
const canDelete = userStore.hasPermission('system:role:delete')
const canGrant = userStore.hasPermission('system:role:grant')

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const grantVisible = ref(false)
const editing = ref(false)
const rows = ref([])
const permissions = ref([])
const checkedIds = ref([])
const checkAll = reactive({})

const current = reactive({ id: null, roleName: '' })
const emptyForm = () => ({ id: null, roleCode: '', roleName: '', description: '', status: 1 })
const form = reactive(emptyForm())

/** 按 module 分组，方便按模块勾选 */
const groupedPermissions = computed(() => {
  const groups = {}
  ;(permissions.value || []).forEach((p) => {
    const m = p.module || 'other'
    if (!groups[m]) groups[m] = []
    groups[m].push(p)
  })
  return groups
})

const isIndeterminate = (module, list) => {
  const selected = list.filter((p) => checkedIds.value.includes(p.id)).length
  return selected > 0 && selected < list.length
}

const handleCheckAll = (val, list) => {
  if (val) {
    list.forEach((p) => {
      if (!checkedIds.value.includes(p.id)) checkedIds.value.push(p.id)
    })
  } else {
    checkedIds.value = checkedIds.value.filter((id) => !list.some((p) => p.id === id))
  }
}

const loadList = async () => {
  loading.value = true
  try {
    const data = await pageRoles({ pageNum: 1, pageSize: 100 })
    rows.value = data.list || []
  } finally {
    loading.value = false
  }
}

const loadPermissions = async () => {
  try {
    permissions.value = await listPermissions() || []
  } catch { /* 不阻塞 */ }
}

const openCreate = () => {
  editing.value = false
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const openEdit = (row) => {
  editing.value = true
  Object.assign(form, {
    id: row.id, roleCode: row.roleCode, roleName: row.roleName,
    description: row.description, status: row.status ?? 1
  })
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.roleCode) return ElMessage.warning('请填写角色编码')
  if (!form.roleName) return ElMessage.warning('请填写角色名称')
  submitting.value = true
  try {
    if (editing.value) {
      await updateRole(form.id, { roleName: form.roleName, description: form.description, status: form.status })
    } else {
      await createRole(form, [])
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const openGrant = async (row) => {
  Object.assign(current, { id: row.id, roleName: row.roleName })
  if (!permissions.value.length) {
    await loadPermissions()
  }
  try {
    checkedIds.value = await listRolePermissionIds(row.id) || []
  } catch {
    checkedIds.value = []
  }
  grantVisible.value = true
}

const submitGrant = async () => {
  submitting.value = true
  try {
    await grantRolePermissions(current.id, checkedIds.value)
    ElMessage.success(`已授权 ${checkedIds.value.length} 项权限`)
    grantVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const removeRole = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.roleName}」？仍有关联账号时会被拒绝。`,
      '提示', { type: 'warning' }
    )
  } catch { return }
  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  loadList()
  loadPermissions()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.dialog-tip {
  margin-bottom: 14px;
}

.perm-group {
  display: flex;
  flex-direction: column;
  margin-top: 10px;
}

.perm-item {
  height: 30px;
}

.perm-code {
  font-family: Consolas, monospace;
  font-size: 12px;
  margin-right: 8px;
}

.perm-name {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.checked-count {
  margin-right: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
