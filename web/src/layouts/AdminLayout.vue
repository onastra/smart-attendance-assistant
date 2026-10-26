<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="layout-logo">联智 · 考勤中台</div>
      <el-menu :default-active="route.path" router class="layout-menu">
        <el-sub-menu index="overview">
          <template #title>
            <el-icon><DataLine/></el-icon>
            <span>概况</span>
          </template>
          <el-menu-item index="/dashboard">看板</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="system">
          <template #title>
            <el-icon><Monitor/></el-icon>
            <span>系统</span>
          </template>
          <el-menu-item index="/system/health">依赖状态</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <span class="layout-title">{{ route.meta.title }}</span>
        <div class="layout-header-right">
          <el-tag size="small" type="info">模块化单体 / 阶段 1 骨架</el-tag>
          <span class="layout-user">{{ userStore.username || '未登录' }}</span>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view/>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const handleLogout = () => {
  userStore.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout-aside {
  background: #ffffff;
  border-right: 1px solid var(--el-border-color-lighter);
}

.layout-logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-weight: 600;
  font-size: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.layout-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #ffffff;
}

.layout-title {
  font-size: 16px;
  font-weight: 600;
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-user {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.layout-main {
  background: #f5f7fa;
}
</style>
