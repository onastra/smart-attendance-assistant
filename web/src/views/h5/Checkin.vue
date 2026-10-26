<template>
  <div class="h5-checkin">
    <!-- 未登录：H5 页在手机上独立打开时通常没有后台的登录态，必须能就地登录，否则演示链路走不通 -->
    <el-card v-if="!userStore.isLogin" shadow="never" class="panel">
      <template #header><span>员工登录</span></template>
      <el-form :model="loginForm" label-width="0" size="large">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username"/>
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码"
                    autocomplete="current-password" show-password @keyup.enter="handleLogin"/>
        </el-form-item>
        <el-button type="primary" size="large" class="block-btn" :loading="loginLoading" @click="handleLogin">
          登录并打卡
        </el-button>
      </el-form>
      <div class="tip">演示账号：<code>emp01</code> / <code>Emp@123</code>（员工）、<code>admin</code> / <code>Admin@123</code>（管理员）</div>
    </el-card>

    <template v-else>
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
            <span>扫码打卡</span>
            <el-tag size="small" :type="locationReady ? 'success' : 'info'">
              {{ locationText }}
            </el-tag>
          </div>
        </template>

        <!-- 摄像头不可用（非 HTTPS / 无权限 / PC 无摄像头）时给出明确说明，而不是静默失败 -->
        <div v-if="showScanner" class="scanner-box">
          <div id="qr-reader" class="qr-reader"/>
          <el-button size="large" class="block-btn" @click="stopScanner">关闭摄像头</el-button>
        </div>
        <div v-else class="scanner-actions">
          <el-button type="primary" size="large" class="block-btn" @click="startScanner">打开摄像头扫码</el-button>
          <div v-if="cameraError" class="tip warn">{{ cameraError }}</div>
        </div>
        <!-- 定位失败后允许重试：手机上一次拒绝授权，不重新点就永远拿不到坐标 -->
        <div v-if="!locationReady" class="retry-row">
          <el-button link type="primary" size="small" :loading="locating" @click="retryLocation">
            重新获取定位
          </el-button>
          <span class="tip">{{ locationText }}</span>
        </div>

        <el-divider content-position="center">或</el-divider>

        <div class="manual-box">
          <div class="manual-label">手动输入二维码原文</div>
          <el-input v-model="content" type="textarea" :rows="3" placeholder="粘贴管理员提供的二维码内容，形如 LZ1|p=1|b=..."/>
          <el-select v-model="checkinType" class="type-select" size="small" placeholder="打卡类型">
            <el-option label="自动判定（按时间）" value=""/>
            <el-option label="上班" value="ON_DUTY"/>
            <el-option label="下班" value="OFF_DUTY"/>
            <el-option label="外勤" value="FIELD"/>
          </el-select>
          <el-button type="primary" class="block-btn submit-btn" :loading="submitting" @click="handleSubmit">
            提交打卡
          </el-button>
          <div class="tip">幂等请求号：<code>{{ requestId || '获取中…' }}</code>（同一页面重复提交只记一次）</div>
        </div>
      </el-card>

      <el-card v-if="result" shadow="never" class="panel">
        <div class="result" :class="result.result === 'NORMAL' ? 'ok' : 'warn-result'">
          <div class="result-title">{{ result.idempotent ? '已打卡（重复提交已去重）' : '打卡成功' }}</div>
          <div class="result-line">姓名：{{ result.employeeName }}（{{ result.empNo }}）</div>
          <div class="result-line">场所：{{ result.placeName }}</div>
          <div class="result-line">类型：{{ result.checkinTypeText }} · 结果：{{ result.resultText }}</div>
          <div class="result-line">时间：{{ formatTime(result.checkinTime) }}</div>
          <div class="result-line tip">
            围栏校验：{{ result.locationChecked ? `已校验（距场所 ${result.distanceMeters} 米，允许 ${result.radiusMeters} 米）` : '客户端未上报坐标，本次未做围栏校验' }}
          </div>
        </div>
      </el-card>

      <el-card v-if="failure" shadow="never" class="panel">
        <el-alert type="error" :closable="false" show-icon :title="failure">
          <template #default>
            <div class="tip">原因码：{{ failureReason || '-' }}。服务端已记录该次失败，可在操作日志中查到。</div>
          </template>
        </el-alert>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header><span>我的最近打卡</span></template>
        <div v-if="recent.length === 0" class="tip">暂无记录</div>
        <div v-for="item in recent" :key="item.id" class="recent-item">
          <div>
            <div class="recent-place">{{ item.placeName || '场所 ' + item.placeId }}</div>
            <div class="recent-time">{{ formatTime(item.checkinTime) }}</div>
          </div>
          <el-tag size="small" :type="item.result === 'NORMAL' ? 'success' : 'warning'">
            {{ item.checkinTypeText }} · {{ item.resultText }}
          </el-tag>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { useUserStore } from '@/stores/user'
import { login as loginApi } from '@/api/auth'
import { describeCheckinFailure, fetchMyRecords, requestCheckinId, submitCheckin } from '@/api/attendance'

const userStore = useUserStore()

const loginForm = ref({ username: '', password: '' })
const loginLoading = ref(false)

const content = ref('')
const checkinType = ref('')
const requestId = ref('')
const submitting = ref(false)
const result = ref(null)
const failure = ref('')
const failureReason = ref('')
const recent = ref([])

const showScanner = ref(false)
const cameraError = ref('')
let scanner = null

const locationReady = ref(false)
const locationText = ref('未获取定位')
const locating = ref(false)
/** 定位成功后保存坐标，供提交打卡时上报 */
const currentCoords = { lng: null, lat: null }

const isSecure = computed(() => window.isSecureContext)

async function handleLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loginLoading.value = true
  try {
    const data = await loginApi(loginForm.value)
    userStore.setLogin(data)
    ElMessage.success(`欢迎，${data.nickname || data.username}`)
    await initAfterLogin()
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  userStore.logout()
  result.value = null
  failure.value = ''
  recent.value = []
}

async function initAfterLogin() {
  await Promise.all([refreshRequestId(), requestLocation(), loadRecent()])
}

async function refreshRequestId() {
  try {
    const data = await requestCheckinId()
    requestId.value = data.requestId
  } catch (e) {
    requestId.value = ''
  }
}

/**
 * 定位只做"尽力而为"。
 *
 * <p>拿不到坐标时如实说明，并且不参与判定——
 * 宁可少一道校验，也不能拿一个编造的坐标假装做过围栏检查。</p>
 */
function requestLocation() {
  return new Promise((resolve) => {
    if (!navigator.geolocation || !isSecure.value) {
      locationText.value = isSecure.value ? '设备不支持定位' : '非安全环境，无法定位'
      resolve()
      return
    }
    locating.value = true
    navigator.geolocation.getCurrentPosition(
      (position) => {
        currentCoords.lng = position.coords.longitude
        currentCoords.lat = position.coords.latitude
        locationReady.value = true
        locationText.value = `已定位（${position.coords.longitude.toFixed(5)}, ${position.coords.latitude.toFixed(5)}）`
        locating.value = false
        resolve()
      },
      () => {
        locationText.value = '未授权定位，将跳过围栏校验'
        locating.value = false
        resolve()
      },
      { timeout: 8000, maximumAge: 60000 }
    )
  })
}

async function retryLocation() {
  if (locating.value) return
  locationText.value = '正在获取定位…'
  await requestLocation()
  if (locationReady.value) {
    ElMessage.success('定位成功，提交打卡时将参与围栏校验')
  }
}

async function startScanner() {
  cameraError.value = ''
  if (!isSecure.value) {
    cameraError.value = '当前地址不是 HTTPS 或 localhost，浏览器禁止调用摄像头，请使用下方的手动输入方式'
    return
  }
  showScanner.value = true
  try {
    scanner = new Html5Qrcode('qr-reader')
    // 取景框按视口自适应：写死 240px 时，窄屏（360px 以下）会被裁切，
    // 用户看到的取景框不完整，会以为"扫不出来"其实是取景框出界了。
    const box = Math.max(160, Math.min(240, Math.floor(window.innerWidth * 0.72)))
    await scanner.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: box, height: box } },
      (decodedText) => {
        content.value = decodedText
        stopScanner()
        ElMessage.success('扫码成功，正在提交')
        handleSubmit()
      },
      () => {}
    )
  } catch (e) {
    showScanner.value = false
    cameraError.value = `摄像头启动失败：${e?.message || e}。请使用下方的手动输入方式`
  }
}

function stopScanner() {
  if (scanner) {
    scanner.stop().catch(() => {})
    scanner.clear()
    scanner = null
  }
  showScanner.value = false
}

async function handleSubmit() {
  failure.value = ''
  failureReason.value = ''
  if (!content.value.trim()) {
    ElMessage.warning('请先扫码或粘贴二维码内容')
    return
  }
  if (!requestId.value) {
    await refreshRequestId()
  }
  submitting.value = true
  try {
    result.value = await submitCheckin({
      content: content.value.trim(),
      requestId: requestId.value,
      checkinType: checkinType.value || undefined,
      lng: locationReady.value ? currentCoords.lng : undefined,
      lat: locationReady.value ? currentCoords.lat : undefined
    })
    content.value = ''
    await refreshRequestId()
    await loadRecent()
  } catch (error) {
    result.value = null
    failure.value = describeCheckinFailure(error)
    failureReason.value = error?.data?.reason || ''
    // 失败后必须换新 requestId：旧号可能已经被服务端判定为占用
    await refreshRequestId()
  } finally {
    submitting.value = false
  }
}

async function loadRecent() {
  try {
    const data = await fetchMyRecords({ pageNum: 1, pageSize: 5 })
    recent.value = data.page?.list || []
  } catch (e) {
    recent.value = []
  }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

onBeforeUnmount(stopScanner)

// 已登录（例如从后台跳转过来）时直接初始化
if (userStore.isLogin) {
  initAfterLogin()
}
</script>

<style scoped>
.h5-checkin {
  max-width: 480px;
  margin: 0 auto;
  /* 安全区：顶部避开刘海，底部避开 Home 指示条（配合 viewport-fit=cover 生效） */
  padding: calc(12px + env(safe-area-inset-top)) calc(12px + env(safe-area-inset-right))
           calc(12px + env(safe-area-inset-bottom)) calc(12px + env(safe-area-inset-left));
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  border-radius: 12px;
}

/* 触控目标：手机上小于 44px 的按钮很难点准 */
.h5-checkin :deep(.el-button) {
  min-height: 44px;
}

/* 输入控件字号 >= 16px：否则 iOS 聚焦时会强制放大整个页面 */
.h5-checkin :deep(.el-input__inner),
.h5-checkin :deep(.el-textarea__inner) {
  font-size: 16px;
}

.retry-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
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
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
}

.profile-name {
  font-size: 16px;
  font-weight: 600;
}

.profile-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.block-btn {
  width: 100%;
}

.qr-reader {
  width: 100%;
}

.scanner-actions,
.manual-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-select {
  width: 100%;
}

.submit-btn {
  margin-top: 4px;
}

.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.tip.warn {
  color: #e6a23c;
}

.result {
  padding: 8px 0;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.ok .result-title {
  color: #67c23a;
}

.warn-result .result-title {
  color: #e6a23c;
}

.result-line {
  font-size: 14px;
  line-height: 1.9;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.recent-place {
  font-size: 14px;
}

.recent-time {
  font-size: 12px;
  color: #909399;
}

/* 窄屏（360px 及以下）：压缩留白，保证首屏能看到"提交打卡" */
@media (max-width: 360px) {
  .h5-checkin {
    gap: 8px;
    padding: calc(8px + env(safe-area-inset-top)) 8px calc(8px + env(safe-area-inset-bottom)) 8px;
  }
  .result-line {
    font-size: 13px;
  }
}

/* 横屏：高度紧张时取消最大宽度限制，压缩纵向间距 */
@media (orientation: landscape) and (max-height: 480px) {
  .h5-checkin {
    max-width: none;
    gap: 8px;
  }
  .qr-reader {
    max-width: 60vw;
    margin: 0 auto;
  }
}

/* 宽屏（平板/桌面预览）：不让内容被拉成一条，保持手机形态居中 */
@media (min-width: 768px) {
  .h5-checkin {
    padding-top: calc(24px + env(safe-area-inset-top));
  }
}
</style>
