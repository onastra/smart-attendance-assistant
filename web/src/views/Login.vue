<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <div class="login-title">联智 · 企业级 AI Agent 智能考勤与业务助手</div>
          <div class="login-subtitle">请使用账号登录管理后台</div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username"/>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock"
                    autocomplete="current-password" show-password @keyup.enter="handleLogin"/>
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <el-alert class="login-note" type="info" :closable="false" show-icon title="演示账号">
        <template #default>
          <div class="login-accounts">
            <div><code>admin</code> / <code>Admin@123</code> — 系统管理员（全部权限）</div>
            <div><code>manager01</code> / <code>Admin@123</code> — 部门主管</div>
            <div><code>emp01</code> / <code>Emp@123</code> — 普通员工（可见菜单更少）</div>
          </div>
        </template>
      </el-alert>

      <div v-if="lastError" class="login-error">{{ lastError }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login as loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const formRef = ref()
const loading = ref(false)
const lastError = ref('')
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  lastError.value = ''
  try {
    const data = await loginApi(form)
    userStore.setLogin({
      token: data.token,
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      employeeId: data.employeeId,
      roles: data.roles,
      permissions: data.permissions
    })
    ElMessage.success(`欢迎回来，${data.nickname || data.username}`)
    const redirect = route.query.redirect
    router.push(redirect ? String(redirect) : homePath())
  } catch (e) {
    // request 拦截器已做统一提示；这里把后端原始原因再展示一次，
    // 便于演示时看清是"口令错误"还是"服务未启动"
    lastError.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 100%);
}

.login-card {
  width: 420px;
}

.login-header {
  text-align: center;
}

.login-title {
  font-size: 16px;
  font-weight: 600;
}

.login-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.login-btn {
  width: 100%;
}

.login-note {
  margin-top: 16px;
}

.login-accounts code {
  background: var(--el-fill-color-light);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: Consolas, monospace;
}

.login-accounts div {
  line-height: 22px;
  font-size: 12px;
}

.login-error {
  margin-top: 12px;
  color: var(--el-color-danger);
  font-size: 13px;
  text-align: center;
}
</style>
