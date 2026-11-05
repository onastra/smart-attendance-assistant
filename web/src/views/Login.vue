<template>
  <div class="login-wrapper">
    <div class="login-decor decor-1"></div>
    <div class="login-decor decor-2"></div>
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <div class="login-brand">
            <div class="brand-mark">联</div>
          </div>
          <div class="login-title">联智 · 智能考勤与业务助手</div>
          <div class="login-subtitle">企业级 AI Agent 中台，请使用账号登录</div>
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

      <div v-if="lastError" class="login-error">{{ lastError }}</div>
    </el-card>
    <div class="login-footer">Enterprise AI Agent Attendance Platform</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login as loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { homePath } from '@/router'

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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(76, 110, 245, 0.35) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 85%, rgba(37, 67, 188, 0.4) 0%, transparent 55%),
    linear-gradient(135deg, #1c2749 0%, #16203e 100%);
}

/* 背景装饰光斑 */
.login-decor {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  pointer-events: none;
}

.decor-1 {
  width: 420px;
  height: 420px;
  top: -120px;
  right: -80px;
  background: rgba(76, 110, 245, 0.28);
}

.decor-2 {
  width: 360px;
  height: 360px;
  bottom: -120px;
  left: -60px;
  background: rgba(47, 84, 235, 0.22);
}

.login-card {
  width: 400px;
  border: none;
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(6, 12, 34, 0.45);
  z-index: 1;
}

.login-header {
  text-align: center;
}

.login-brand {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4c6ef5 0%, #2f54eb 100%);
  color: #ffffff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(47, 84, 235, 0.4);
}

.login-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.login-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.login-card :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.login-btn {
  width: 100%;
  border-radius: 8px;
  letter-spacing: 4px;
}

.login-error {
  margin-top: 12px;
  color: var(--el-color-danger);
  font-size: 13px;
  text-align: center;
}

.login-footer {
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.35);
  font-size: 12px;
  letter-spacing: 2px;
  z-index: 1;
}
</style>
