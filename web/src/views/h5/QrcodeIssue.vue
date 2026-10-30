<template>
  <div class="h5-qrcode">
    <!-- 未登录：与移动打卡页同一策略，就地登录后继续操作 -->
    <el-card v-if="!userStore.isLogin" shadow="never" class="panel">
      <template #header><span>管理员登录</span></template>
      <el-form :model="loginForm" label-width="0" size="large">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username"/>
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码"
                    autocomplete="current-password" show-password @keyup.enter="handleLogin"/>
        </el-form-item>
        <el-button type="primary" size="large" class="block-btn" :loading="loginLoading" @click="handleLogin">
          登录并签发二维码
        </el-button>
      </el-form>
      <div class="tip">需要「生成二维码」权限：管理员或部门主管账号均可。</div>
    </el-card>

    <template v-else>
      <div class="profile">
        <div>
          <div class="profile-name">{{ userStore.displayName }}</div>
          <div class="profile-sub">移动端 · 签发考勤二维码</div>
        </div>
        <el-button link type="primary" size="small" @click="handleLogout">退出</el-button>
      </div>

      <!-- 无权限：主管/管理员以外账号打开时给出明确提示 -->
      <el-card v-if="!userStore.hasPermission('attendance:qrcode:create')" shadow="never" class="panel">
        <el-alert type="warning" :closable="false" show-icon
                  title="当前账号没有「生成二维码」权限"
                  description="该页面供管理员 / 部门主管在手机上签发员工打卡二维码使用。"/>
      </el-card>

      <template v-else>
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>签发二维码</span>
              <el-tag size="small" type="info">服务端签名 · 篡改即失效</el-tag>
            </div>
          </template>

          <div class="field-label">办公场所</div>
          <el-select v-model="form.placeId" placeholder="请选择场所" size="large" class="block-select">
            <el-option v-for="p in places" :key="p.id" :label="p.placeName" :value="p.id"/>
          </el-select>

          <div class="field-label">类型</div>
          <div class="type-row">
            <div class="type-chip" :class="{ active: form.qrType === 'DYNAMIC' }" @click="setType('DYNAMIC')">
              <div class="type-name">动态码</div>
              <div class="type-desc">120 秒轮换</div>
            </div>
            <div class="type-chip" :class="{ active: form.qrType === 'FIXED' }" @click="setType('FIXED')">
              <div class="type-name">固定码</div>
              <div class="type-desc">24 小时张贴</div>
            </div>
          </div>

          <div class="field-label">打卡类型</div>
          <el-select v-model="form.checkinType" size="large" class="block-select">
            <el-option label="自动判定（按打卡时间）" value="AUTO"/>
            <el-option label="上班" value="ON_DUTY"/>
            <el-option label="下班" value="OFF_DUTY"/>
            <el-option label="外勤" value="FIELD"/>
          </el-select>

          <div class="switch-row">
            <span>一次性（打卡一次即失效）</span>
            <el-switch v-model="form.onceOnly" :active-value="1" :inactive-value="0"/>
          </div>

          <el-button type="primary" size="large" class="block-btn" :loading="issuing" @click="handleIssue">
            {{ current.id ? '重新签发' : '生成二维码' }}
          </el-button>
          <div class="tip">动态码到期后会自动续发，防止截图被转发后长期使用。</div>
        </el-card>

        <el-card v-if="current.id" shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>当前二维码</span>
              <el-tag size="small" :type="remainSeconds > 0 ? 'success' : 'danger'">
                {{ remainSeconds > 0 ? `剩余 ${remainSeconds} 秒` : '已过期' }}
              </el-tag>
            </div>
          </template>
          <div class="qr-box">
            <img v-if="imageUrl" :src="imageUrl" alt="考勤二维码" class="qr-image"/>
            <el-skeleton v-else style="width: 240px" animated/>
          </div>
          <div class="qr-meta">
            <div class="meta-line"><span class="meta-label">场所</span>{{ current.placeName }}</div>
            <div class="meta-line">
              <span class="meta-label">类型</span>
              {{ current.qrType === 'FIXED' ? '长期固定码' : '动态码' }}
              <el-tag size="small" :type="current.onceOnly === 1 ? 'warning' : 'info'">
                {{ current.onceOnly === 1 ? '一次性' : '可重复' }}
              </el-tag>
            </div>
            <div class="meta-line">
              <span class="meta-label">打卡</span>
              <el-tag size="small" :type="current.checkinType === 'AUTO' ? 'info' : 'success'">
                {{ checkinTypeText(current.checkinType) }}
              </el-tag>
            </div>
            <div class="meta-line"><span class="meta-label">过期</span>{{ formatTime(current.expireTime) }}</div>
          </div>
          <el-button size="large" class="block-btn copy-btn" @click="copyPayload">复制二维码原文（备用）</el-button>
          <el-button size="large" class="block-btn" type="danger" plain @click="handleInvalidate(current.id)">
            作废该码
          </el-button>
          <div class="tip">让员工打开「移动打卡」页扫码即可；摄像头不可用时可把原文粘贴到打卡页手动提交。</div>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header><span>最近签发</span></template>
          <div v-if="list.length === 0" class="tip">暂无签发记录</div>
          <div v-for="item in list" :key="item.id" class="recent-item">
            <div>
              <div class="recent-place">{{ item.placeName || '场所 ' + item.placeId }}</div>
              <div class="recent-time">{{ formatTime(item.issueTime) }}</div>
            </div>
            <div class="recent-op">
              <el-tag size="small" :type="statusType(item.status)">{{ statusText(item.status) }}</el-tag>
              <el-button link type="primary" size="small" @click="viewImage(item)">查看</el-button>
            </div>
          </div>
        </el-card>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { login as loginApi } from '@/api/auth'
import { pagePlaces } from '@/api/place'
import { fetchQrcodeImage, invalidateQrcode, issueQrcode, pageQrcodes } from '@/api/qrcode'

const DEFAULT_TTL = { DYNAMIC: 120, FIXED: 86400 }

const userStore = useUserStore()
const loginForm = ref({ username: '', password: '' })
const loginLoading = ref(false)

const places = ref([])
const form = ref({ placeId: null, qrType: 'DYNAMIC', checkinType: 'AUTO', ttlSeconds: DEFAULT_TTL.DYNAMIC, onceOnly: 0 })
const current = ref({})
const imageUrl = ref('')
const remainSeconds = ref(0)
const issuing = ref(false)
const list = ref([])

let timer = null
let objectUrl = null

const canIssue = computed(() => userStore.hasPermission('attendance:qrcode:create'))

function setType(type) {
  form.value.qrType = type
  form.value.ttlSeconds = DEFAULT_TTL[type] || 120
}

async function handleLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loginLoading.value = true
  try {
    const data = await loginApi(loginForm.value)
    userStore.setLogin(data)
    ElMessage.success('登录成功')
    await init()
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  userStore.logout()
}

async function loadPlaces() {
  const data = await pagePlaces({ pageNum: 1, pageSize: 100 })
  places.value = data.list || []
  if (!form.value.placeId && places.value.length) {
    form.value.placeId = places.value[0].id
  }
}

async function handleIssue() {
  if (!form.value.placeId) {
    ElMessage.warning('请先选择办公场所')
    return
  }
  issuing.value = true
  try {
    const result = await issueQrcode({ ...form.value })
    current.value = result
    remainSeconds.value = Number(result.ttlSeconds || 0)
    await renderImage(result.id)
    startCountdown()
    await loadList()
    ElMessage.success('二维码已签发')
  } finally {
    issuing.value = false
  }
}

async function renderImage(id) {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
  objectUrl = await fetchQrcodeImage(id)
  imageUrl.value = objectUrl
}

function startCountdown() {
  stopCountdown()
  timer = setInterval(async () => {
    if (remainSeconds.value > 0) {
      remainSeconds.value -= 1
      // 动态码到期自动续发，保证员工扫到的码始终有效
      if (remainSeconds.value === 0 && current.value.qrType === 'DYNAMIC') {
        await handleIssue()
      }
    }
  }, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function viewImage(row) {
  current.value = row
  remainSeconds.value = Number(row.remainSeconds || 0)
  await renderImage(row.id)
}

async function handleInvalidate(id) {
  await ElMessageBox.confirm('作废后该二维码立即不可用于打卡，确认继续？', '提示', { type: 'warning' })
  await invalidateQrcode(id)
  ElMessage.success('二维码已作废')
  if (current.value.id === id) {
    remainSeconds.value = 0
  }
  loadList()
}

async function copyPayload() {
  try {
    await navigator.clipboard.writeText(current.value.payload)
    ElMessage.success('已复制二维码原文')
  } catch (e) {
    ElMessage.warning('当前浏览器不允许自动复制，请手动截图二维码')
  }
}

async function loadList() {
  const data = await pageQrcodes({ pageNum: 1, pageSize: 5 })
  list.value = data.list || []
}

function statusText(status) {
  return { VALID: '有效', INVALID: '已失效', EXPIRED: '已过期', USED: '已使用' }[status] || status
}

function checkinTypeText(type) {
  return { AUTO: '自动判定', ON_DUTY: '上班', OFF_DUTY: '下班', FIELD: '外勤' }[type] || '自动判定'
}

function statusType(status) {
  return { VALID: 'success', INVALID: 'danger', EXPIRED: 'info', USED: 'warning' }[status] || 'info'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

async function init() {
  if (!userStore.isLogin || !canIssue.value) return
  await loadPlaces()
  await loadList()
}

onMounted(init)

onBeforeUnmount(() => {
  stopCountdown()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
})
</script>

<style scoped>
.h5-qrcode {
  max-width: 480px;
  margin: 0 auto;
  padding: 12px 12px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  padding: 4px 2px;
}

.profile-name {
  font-size: 16px;
  font-weight: 600;
}

.profile-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.block-btn {
  width: 100%;
  margin-top: 12px;
  margin-left: 0;
}

.block-btn + .block-btn {
  margin-left: 0;
}

.block-select {
  width: 100%;
}

.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
  line-height: 1.6;
}

.field-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 14px 0 6px;
}

.field-label:first-of-type {
  margin-top: 4px;
}

.type-row {
  display: flex;
  gap: 10px;
}

.type-chip {
  flex: 1;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 10px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.type-chip.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.type-name {
  font-size: 14px;
  font-weight: 600;
}

.type-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 4px;
  font-size: 14px;
}

.qr-box {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.qr-image {
  width: 240px;
  height: 240px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
}

.qr-meta {
  margin-top: 8px;
}

.meta-line {
  font-size: 13px;
  line-height: 2;
}

.meta-label {
  display: inline-block;
  width: 44px;
  color: var(--el-text-color-secondary);
}

.copy-btn {
  margin-top: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-place {
  font-size: 14px;
  font-weight: 500;
}

.recent-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.recent-op {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
