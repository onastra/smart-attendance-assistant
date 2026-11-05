<template>
  <el-container class="layout">
    <el-aside width="236px" class="layout-aside">
      <div class="layout-logo">
        <img class="logo-img" src="/logo.jpg" alt="联智"/>
        <div class="logo-text">
          <div class="logo-title">联智考勤</div>
          <div class="logo-subtitle">AI AGENT 中台</div>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="layout-menu">
        <el-sub-menu v-for="group in menuGroups" :key="group.title" :index="group.title">
          <template #title>
            <el-icon><component :is="group.icon"/></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item v-for="item in group.children" :key="item.path" :index="item.fullPath">
            <el-icon><component :is="item.meta.icon"/></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="layout-aside-footer">v1.1.0 · Enterprise</div>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <span class="layout-title">{{ route.meta.title }}</span>
        <div class="layout-header-right">
          <el-tag v-if="userStore.roles.length" size="small" type="success" effect="light" round>
            {{ userStore.roles.join(' / ') }}
          </el-tag>
          <el-tag size="small" effect="plain" round class="agent-tag">AI Agent</el-tag>
          <el-divider direction="vertical" />
          <div class="layout-user">
            <div class="user-avatar">{{ (userStore.displayName || '用')[0] }}</div>
            <span class="user-name">{{ userStore.displayName }}</span>
          </div>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component"/>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const route = useRoute()
const vueRouter = useRouter()
const userStore = useUserStore()

/**
 * 菜单由路由推导，并按权限码过滤。
 *
 * <p>这里只是展示层控制——把按钮藏起来是为了不给用户看到点不动的入口，
 * 不是安全手段。用户直接改 localStorage 或直接调接口，
 * 仍会被后端拦截器挡下（实测返回 40301）。</p>
 *
 * <p>⚠️ 路径拼接的原因（2026-10-27 修复的 bug）：
 * <code>router.getRoutes()</code> 返回的规范化记录里，<code>children</code>
 * 是<b>原始配置对象</b>，其 path 是相对路径（如 <code>attendance/qrcodes</code>，无前导斜杠）。
 * el-menu 的 router 模式会把 index 原样传给 <code>router.push()</code>，
 * vue-router 对无斜杠路径按「当前路由」做相对解析——从统计看板点过去碰巧解析正确，
 * 但从 /kb/documents 点就成了 /kb/attendance/qrcodes，
 * 白屏 + "No match found" 警告。这里统一补成绝对路径，同时修复菜单高亮失效问题。</p>
 */
const menuGroups = computed(() => {
  const mainRoute = router.getRoutes().find((r) => r.path === '/')
  const children = (mainRoute?.children || []).filter((r) => !r.meta?.hidden)
  const visible = children.filter(
    (r) => !r.meta?.permission || userStore.hasPermission(r.meta.permission)
  ).filter(
    // 个人考勤类菜单（我的考勤/人脸打卡）仅对绑定了员工档案的账号展示，
    // 管理员等未绑定档案的账号看不到也进不去
    (r) => !r.meta?.requiresEmployee || !!userStore.employeeId
  )
  // 原始 child.path 是相对路径，菜单跳转必须用绝对路径
  const toFullPath = (child) =>
    child.path.startsWith('/') ? child.path : mainRoute.path.replace(/\/$/, '') + '/' + child.path

  const groups = []
  visible.forEach((item) => {
    const groupTitle = item.meta?.group || '其他'
    let group = groups.find((g) => g.title === groupTitle)
    if (!group) {
      group = { title: groupTitle, icon: item.meta?.icon || 'Menu', children: [] }
      groups.push(group)
    }
    group.children.push({ ...item, fullPath: toFullPath(item) })
  })
  return groups
})

const handleLogout = () => {
  userStore.logout()
  vueRouter.push({ name: 'login' })
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ---- 侧边栏：浅色，logo 品牌区 ---- */
.layout-aside {
  background: #ffffff;
  border-right: 1px solid #eef2f8;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.layout-logo {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 18px;
  height: 66px;
  flex-shrink: 0;
  border-bottom: 1px solid #f0f4fa;
}

.logo-img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  box-shadow: 0 3px 10px rgba(26, 102, 217, 0.25);
}

.logo-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1.2;
  background: linear-gradient(135deg, #1a66d9 0%, #16a7e0 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.logo-subtitle {
  color: #9aa8c0;
  font-size: 10px;
  letter-spacing: 1.8px;
  margin-top: 2px;
}

.layout-menu {
  border-right: none;
  padding: 12px 0;
  flex: 1;
}

/* 菜单胶囊选中态：品牌渐变 + 浮起阴影 */
.layout-aside :deep(.el-sub-menu__title),
.layout-aside :deep(.el-menu-item) {
  color: #5b6b87;
  height: 44px;
  line-height: 44px;
  margin: 3px 12px;
  border-radius: 10px;
  transition: all 0.18s ease;
}

.layout-aside :deep(.el-sub-menu__title:hover),
.layout-aside :deep(.el-menu-item:hover) {
  background: #f0f6ff;
  color: #1a66d9;
}

.layout-aside :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #1a66d9;
  font-weight: 600;
}

.layout-aside :deep(.el-menu-item.is-active) {
  color: #ffffff;
  font-weight: 600;
  background: linear-gradient(135deg, #1a66d9 0%, #16a7e0 100%);
  box-shadow: 0 4px 12px rgba(26, 102, 217, 0.35);
}

.layout-aside :deep(.el-sub-menu .el-menu-item) {
  padding-left: 48px !important;
}

.layout-aside :deep(.el-sub-menu__title .el-sub-menu__icon-arrow) {
  color: #a6b3c9;
}

.layout-aside-footer {
  flex-shrink: 0;
  padding: 14px 18px;
  font-size: 11px;
  color: #b3bfd2;
  letter-spacing: 0.5px;
  border-top: 1px solid #f0f4fa;
}

/* ---- 顶栏：毛玻璃 ---- */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 1px 4px rgba(23, 45, 90, 0.05);
  position: relative;
  z-index: 5;
}

.layout-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-tag {
  color: #16a7e0;
  border-color: rgba(22, 167, 224, 0.4);
}

.layout-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1a66d9 0%, #16a7e0 100%);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(26, 102, 217, 0.3);
}

.user-name {
  font-size: 14px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

/* ---- 主区域：浅灰底 + 品牌色点阵纹理 ---- */
.layout-main {
  background-color: #f3f6fb;
  background-image: radial-gradient(rgba(26, 102, 217, 0.06) 1px, transparent 1px);
  background-size: 22px 22px;
  padding: 18px;
  overflow-y: auto;
}

/* ---- 页面切换过渡 ---- */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
