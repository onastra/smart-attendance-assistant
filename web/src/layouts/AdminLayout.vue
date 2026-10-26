<template>
  <el-container class="layout">
    <el-aside width="230px" class="layout-aside">
      <div class="layout-logo">联智 · 考勤中台</div>
      <el-menu :default-active="route.path" router class="layout-menu">
        <el-sub-menu v-for="group in menuGroups" :key="group.title" :index="group.title">
          <template #title>
            <el-icon><component :is="group.icon"/></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
            <el-icon><component :is="item.meta.icon"/></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <span class="layout-title">{{ route.meta.title }}</span>
        <div class="layout-header-right">
          <el-tag v-if="userStore.roles.length" size="small" type="success">
            {{ userStore.roles.join(' / ') }}
          </el-tag>
          <el-tag size="small" type="info">模块化单体</el-tag>
          <span class="layout-user">{{ userStore.displayName }}</span>
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
 */
const menuGroups = computed(() => {
  const mainRoute = router.getRoutes().find((r) => r.path === '/')
  const children = (mainRoute?.children || []).filter((r) => !r.meta?.hidden)
  const visible = children.filter(
    (r) => !r.meta?.permission || userStore.hasPermission(r.meta.permission)
  )

  const groups = []
  visible.forEach((item) => {
    const groupTitle = item.meta?.group || '其他'
    let group = groups.find((g) => g.title === groupTitle)
    if (!group) {
      group = { title: groupTitle, icon: item.meta?.icon || 'Menu', children: [] }
      groups.push(group)
    }
    group.children.push(item)
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

.layout-aside {
  background: #ffffff;
  border-right: 1px solid var(--el-border-color-lighter);
  overflow-y: auto;
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
