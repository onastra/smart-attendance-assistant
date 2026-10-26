<template>
  <div class="h5-face">
    <!-- 未登录：H5 页在手机上独立打开时没有后台登录态，必须能就地登录 -->
    <el-card v-if="!userStore.isLogin" shadow="never" class="panel">
      <template #header><span>员工登录</span></template>
      <el-form :model="loginForm" label-width="0" size="large">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码"
                    autocomplete="current-password" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" size="large" class="block-btn" :loading="loginLoading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
      <div class="tip">演示账号：<code>emp01</code> / <code>Emp@123</code></div>
    </el-card>

    <template v-else>
      <!-- 模拟徽章放在页面最顶部：任何人打开这一页，第一眼就该看到它是模拟的 -->
      <el-alert type="warning" :closable="false" class="mock-banner">
        <template #title>
          <strong>模拟实现</strong>：当前人脸识别由模拟适配层提供（provider={{ status?.provider || 'noop' }}），
          只比对图片指纹的哈希，<strong>不是真实人脸检测，不具有任何身份凭证效力</strong>。
        </template>
      </el-alert>

      <div class="profile">
        <div>
          <div class="profile-name">{{ userStore.displayName }}</div>
          <div class="profile-sub">员工 ID：{{ userStore.employeeId || '未关联档案' }}</div>
        </div>
        <el-button link type="primary" size="small" @click="handleLogout">退出</el-button>
      </div>

      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-header">
            <span>人脸录入</span>
            <el-tag size="small" :type="status?.enrolled ? 'success' : 'info'">
              {{ status?.enrolled ? '已录入' : '未录入' }}
            </el-tag>
          </div>
        </template>
        <input ref="enrollInput" type="file" accept="image/*" class="file-input" @change="onEnrollFile" />
        <el-button type="primary" class="block-btn" :loading="enrolling" @click="pickEnroll">
          选择照片并录入
        </el-button>
        <div v-if="enrollResult" class="result-box ok">
          <div class="result-title">录入完成（mock={{ enrollResult.mock }}）</div>
          <div class="result-line">特征值预览：{{ enrollResult.featureHashPreview }}</div>
          <div class="result-line">是否保存原图：{{ enrollResult.storedImage ? '是' : '否（默认不保存）' }}</div>
          <div class="result-line tip">{{ enrollResult.notice }}</div>
        </div>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-header">
            <span>人脸核验</span>
            <el-tag size="small" type="warning">模拟</el-tag>
          </div>
        </template>
        <input ref="verifyInput" type="file" accept="image/*" class="file-input" @change="onVerifyFile" />
        <el-button type="primary" class="block-btn" :loading="verifying" @click="pickVerify">
          选择照片并核验
        </el-button>
        <div v-if="verifyResult" class="result-box" :class="verifyResult.passed ? 'ok' : 'warn-result'">
          <div class="result-title">
            {{ verifyResult.passed ? '核验通过（模拟）' : '核验不通过（模拟）' }}
          </div>
          <div class="result-line">相似度：{{ verifyResult.similarity }}（阈值 {{ verifyResult.threshold }}）</div>
          <div class="result-line">mock={{ verifyResult.mock }} · 原因码：{{ verifyResult.reason }}</div>
          <div class="result-line tip">{{ verifyResult.notice }}</div>
        </div>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header><span>隐私与边界说明</span></template>
        <div class="tip block">
          {{ status?.privacyNote || '默认只保存特征值的哈希，不保存原始人脸图片。' }}
        </div>
        <div class="tip block">
          考勤记录仍以<strong>场所二维码打卡</strong>为准：无虹软 SDK 授权的条件下，
          人脸只能作为流程演示，不能作为唯一考勤凭证。
        </div>
        <div class="tip block">
          真实 SDK 接入方式见 <code>docs/face-sdk-integration.md</code>；
          将 <code>lz.face.provider</code> 改为 <code>arcsoft</code> 后，
          本页会转为提示"缺少 SDK 与授权"而不会崩溃。
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { login as loginApi } from '@/api/auth'
import { fetchFaceStatus, enrollFace, verifyFace } from '@/api/face'

const userStore = useUserStore()

const loginForm = reactive({ username: '', password: '' })
const loginLoading = ref(false)

const status = ref(null)
const enrollResult = ref(null)
const verifyResult = ref(null)
const enrolling = ref(false)
const verifying = ref(false)
const enrollInput = ref(null)
const verifyInput = ref(null)

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loginLoading.value = true
  try {
    // login() 收的是整个表单对象；之前这里传的是两个字符串，
    // 后端拿到的 body 是一个裸字符串，登录必然失败且看不出原因。
    const data = await loginApi({ username: loginForm.username, password: loginForm.password })
    userStore.setLogin(data)
    ElMessage.success(`欢迎，${data.nickname || data.username}`)
    await loadStatus()
  } catch { /* 登录失败提示已在拦截器处理 */ } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  userStore.logout()
  enrollResult.value = null
  verifyResult.value = null
  status.value = null
}

async function loadStatus() {
  try {
    status.value = await fetchFaceStatus()
  } catch {
    status.value = null
  }
}

function pickEnroll() {
  enrollInput.value?.click()
}

function pickVerify() {
  verifyInput.value?.click()
}

async function onEnrollFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  enrolling.value = true
  try {
    enrollResult.value = await enrollFace(file)
    ElMessage.success('录入完成（模拟实现）')
    await loadStatus()
  } catch { /* 已提示 */ } finally {
    enrolling.value = false
    event.target.value = ''
  }
}

async function onVerifyFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  verifying.value = true
  try {
    verifyResult.value = await verifyFace(file)
  } catch { /* 已提示 */ } finally {
    verifying.value = false
    event.target.value = ''
  }
}

onMounted(() => {
  if (userStore.isLogin) {
    loadStatus()
  }
})
</script>

<style scoped>
.h5-face {
  max-width: 480px;
  margin: 0 auto;
  /* 安全区：顶部避开刘海/状态栏，底部避开 Home 指示条。
     配合 index.html 的 viewport-fit=cover 才生效。 */
  padding: calc(12px + env(safe-area-inset-top)) calc(12px + env(safe-area-inset-right))
           calc(12px + env(safe-area-inset-bottom)) calc(12px + env(safe-area-inset-left));
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel {
  border-radius: 8px;
}
/* 移动端触控目标：低于 44px 手指点不准，Element Plus 的 small 按钮在手机上偏小 */
.h5-face :deep(.el-button) {
  min-height: 44px;
}
/* 输入控件字号 >= 16px：iOS 在字号小于 16px 时聚焦会自动放大整个页面 */
.h5-face :deep(.el-input__inner),
.h5-face :deep(.el-textarea__inner) {
  font-size: 16px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.profile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
}
.profile-name {
  font-size: 16px;
  font-weight: 600;
}
.profile-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mock-banner :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.6;
  white-space: normal;
}
.block-btn {
  width: 100%;
  margin-top: 8px;
}
.file-input {
  display: none;
}
.result-box {
  margin-top: 10px;
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.result-box.ok {
  border-left: 3px solid var(--el-color-success);
}
.result-box.warn-result {
  border-left: 3px solid var(--el-color-warning);
}
.result-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.result-line {
  font-size: 13px;
  line-height: 1.7;
}
.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tip.block {
  margin-top: 6px;
  line-height: 1.7;
}

/* 窄屏（iPhone SE 等 320~360px）：压缩间距，避免内容被挤出视口 */
@media (max-width: 360px) {
  .h5-face {
    gap: 8px;
    padding: calc(8px + env(safe-area-inset-top)) 8px calc(8px + env(safe-area-inset-bottom)) 8px;
  }
  .result-line {
    font-size: 12px;
  }
}

/* 横屏：手机横过来时高度很紧，压掉纵向留白，让首屏能看到录入按钮 */
@media (orientation: landscape) and (max-height: 480px) {
  .h5-face {
    max-width: none;
    gap: 8px;
  }
  .mock-banner {
    padding: 4px 8px;
  }
}
</style>
