import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

/**
 * 路由定义。
 *
 * meta 约定：
 * - title：菜单/页头标题
 * - public：无需登录即可访问
 * - permission：进入该页面所需权限码（仅做菜单与入口的展示控制，
 *   真正的安全边界在后端拦截器，前端这里即使绕过也会被返回 40301）
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true, hidden: true }
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
        meta: { title: '概要看板', icon: 'DataLine', group: '概况' }
      },
      {
        path: 'system/health',
        name: 'health',
        component: () => import('@/views/system/Health.vue'),
        meta: { title: '系统与依赖状态', icon: 'Monitor', group: '系统' }
      },
      {
        path: 'employees',
        name: 'employees',
        component: () => import('@/views/hr/EmployeeManage.vue'),
        meta: { title: '员工管理', icon: 'User', group: '人事', permission: 'hr:employee:list' }
      },
      {
        path: 'org',
        name: 'org',
        component: () => import('@/views/org/OrgManage.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', group: '人事', permission: 'system:company:list' }
      },
      {
        path: 'places',
        name: 'places',
        component: () => import('@/views/place/PlaceManage.vue'),
        meta: { title: '办公场所', icon: 'Location', group: '考勤设施', permission: 'place:list' }
      },
      {
        path: 'attendance/qrcodes',
        name: 'qrcodes',
        component: () => import('@/views/attendance/QrcodeManage.vue'),
        meta: { title: '考勤二维码', icon: 'QrCode', group: '考勤', permission: 'attendance:qrcode:list' }
      },
      {
        path: 'attendance/records',
        name: 'attendance-records',
        component: () => import('@/views/attendance/AdminRecords.vue'),
        meta: { title: '考勤记录查询', icon: 'Document', group: '考勤', permission: 'attendance:record:admin' }
      },
      {
        path: 'my/records',
        name: 'my-records',
        component: () => import('@/views/attendance/MyRecords.vue'),
        meta: { title: '我的考勤', icon: 'Clock', group: '考勤' }
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '账号管理', icon: 'UserFilled', group: '系统', permission: 'system:user:list' }
      },
      {
        path: 'system/roles',
        name: 'roles',
        component: () => import('@/views/system/RoleManage.vue'),
        meta: { title: '角色权限', icon: 'Key', group: '系统', permission: 'system:role:list' }
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
        component: () => import('@/views/h5/Checkin.vue'),
        meta: { title: '移动打卡', public: true, hidden: true }
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
    return
  }
  // 已登录用户访问登录页时直接回首页，避免登录后还能看到登录框的割裂感
  if (to.name === 'login' && token) {
    next('/dashboard')
    return
  }
  // 前端侧的权限展示控制；后端仍会在接口层再做一次校验
  if (to.meta.permission && token) {
    const permissions = JSON.parse(localStorage.getItem('lz_permissions') || '[]')
    if (!permissions.includes(to.meta.permission)) {
      ElMessage.warning('当前账号没有该页面的访问权限')
      next('/dashboard')
      return
    }
  }
  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 联智考勤` : '联智考勤'
})

export default router
