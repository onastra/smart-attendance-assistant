import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '概要看板', icon: 'DataLine' }
      },
      {
        path: 'system/health',
        name: 'health',
        component: () => import('@/views/system/Health.vue'),
        meta: { title: '系统与依赖状态', icon: 'Monitor' }
      }
    ]
  },
  // H5 移动端打卡页：独立布局，不做后台缩放适配
  {
    path: '/h5',
    children: [
      {
        path: 'checkin',
        name: 'h5-checkin',
        component: () => import('@/views/h5/CheckinPlaceholder.vue'),
        meta: { title: '移动打卡', public: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('lz_token')
  if (!to.meta.public && !token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 联智考勤` : '联智考勤'
})

export default router
