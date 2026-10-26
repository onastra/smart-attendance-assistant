<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 170px"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1"/>
            <el-option label="停用" :value="0"/>
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.roleId" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id"/>
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
          <span>登录账号</span>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建账号</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" stripe border empty-text="暂无账号">
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="username" label="用户名" width="130"/>
        <el-table-column prop="nickname" label="昵称" min-width="140"/>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles || []" :key="r" size="small" class="role-tag">{{ r }}</el-tag>
            <span v-if="!row.roles?.length" class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="关联员工" width="100">
          <template #default="{ row }">{{ row.employeeId ? `#${row.employeeId}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="150">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openResetPwd(row)">重置密码</el-button>
            <el-button v-if="canUpdate" link :type="row.status === 1 ? 'danger' : 'success'"
                       @click="toggleStatus(row)">
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

    <!-- 新建 / 编辑账号 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑账号' : '新建账号'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="必填，唯一" :disabled="editing && !form.username"/>
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位"/>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname"/>
        </el-form-item>
        <el-form-item label="关联员工">
          <el-input-number v-model="form.employeeId" :min="1" controls-position="right"
                           placeholder="可留空"/>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="可多选" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName}（${r.roleCode}）`" :value="r.id"/>
          </el-select>
          <div class="form-hint">选择后即全量覆盖该账号原有角色</div>
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

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="目标账号">
          <span>{{ current.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { pageUsers, listRoles, createUser, updateUser, changeUserStatus, resetUserPassword } from '@/api/system'

const userStore = useUserStore()
const canCreate = userStore.hasPermission('system:user:create')
const canUpdate = userStore.hasPermission('system:user:update')

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const pwdVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const rows = ref([])
const total = ref(0)
const roles = ref([])

const current = reactive({ id: null, username: '' })
const newPassword = ref('')

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null, roleId: null })
const emptyForm = () => ({
  id: null, username: '', password: '', nickname: '', employeeId: null, roleIds: [], status: 1
})
const form = reactive(emptyForm())

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '初始密码至少 6 位', trigger: 'blur' }]
}

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '从未登录')

const loadList = async () => {
  loading.value = true
  try {
    const data = await pageUsers(query)
    rows.value = data.list || []
    total.value = data.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  try {
    roles.value = await listRoles() || []
  } catch { /* 下拉失败不阻塞 */ }
}

const resetQuery = () => {
  Object.assign(query, { pageNum: 1, pageSize: 10, keyword: '', status: null, roleId: null })
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
    id: row.id, username: row.username, password: '', nickname: row.nickname || '',
    employeeId: row.employeeId, roleIds: row.roleIds || [], status: row.status ?? 1
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
      await updateUser(form.id, {
        nickname: form.nickname, employeeId: form.employeeId,
        status: form.status, roleIds: form.roleIds
      })
      ElMessage.success('账号已更新')
    } else {
      await createUser(form)
      ElMessage.success('账号已创建')
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
    await ElMessageBox.confirm(`确认${next === 0 ? '停用' : '启用'}账号「${row.username}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await changeUserStatus(row.id, next)
    ElMessage.success('操作成功')
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const openResetPwd = (row) => {
  Object.assign(current, { id: row.id, username: row.username })
  newPassword.value = ''
  pwdVisible.value = true
}

const submitResetPwd = async () => {
  if (!newPassword.value || newPassword.value.length < 6) {
    return ElMessage.warning('新密码至少 6 位')
  }
  submitting.value = true
  try {
    await resetUserPassword(current.id, newPassword.value)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '重置失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadList()
  loadRoles()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.role-tag {
  margin-right: 4px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}
</style>
