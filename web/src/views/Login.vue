<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-title">联智 · 企业级 AI Agent 智能考勤与业务助手</div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" autocomplete="username"/>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password autocomplete="current-password"
                    @keyup.enter="handleLogin"/>
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <el-alert class="login-note" type="info" :closable="false" show-icon
                title="阶段 1 骨架说明"
                description="登录接口将在阶段 2 实现。当前阶段可先通过「概要看板」检查后端健康状态，验证前后端链路是否打通（路由守卫会在未登录时跳回本页，调试时可先用任意字符写入本地 token 绕过）。"/>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/system'
import { useUserStore } from '@/stores/user'

const formRef = ref()
const loading = ref(false)
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
  try {
    const data = await login(form)
    userStore.setLogin({
      token: data.token,
      username: data.user?.username || form.username,
      permissions: data.permissions || []
    })
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 请求拦截器已做统一提示；阶段 2 之前后端尚无该接口，这里补充明确说明
    if (e?.response?.status === 404) {
      ElMessage.warning('登录接口尚未实现（阶段 2 交付）')
    }
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

.login-title {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.login-btn {
  width: 100%;
}

.login-note {
  margin-top: 16px;
}
</style>
