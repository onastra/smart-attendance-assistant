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
    redirect: '/statistics/dashboard',
    children: [
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
        path: 'attendance/settings',
        name: 'attendance-settings',
        component: () => import('@/views/attendance/AttendanceSetting.vue'),
        meta: { title: '考勤设置', icon: 'AlarmClock', group: '考勤', permission: 'attendance:record:admin' }
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
        meta: { title: '我的考勤', icon: 'Clock', group: '考勤', requiresEmployee: true }
      },
      {
        // 与 H5 页共用同一组件：后台入口方便演示，手机端入口方便真机体验
        path: 'face/mock',
        name: 'face-mock',
        component: () => import('@/views/h5/FaceCheckin.vue'),
        meta: { title: '人脸打卡（模拟）', icon: 'Camera', group: '考勤', permission: 'face:verify', requiresEmployee: true }
      },
      {
        path: 'statistics/dashboard',
        name: 'statistics-dashboard',
        component: () => import('@/views/statistics/Dashboard.vue'),
        meta: { title: '数据看板', icon: 'DataLine', group: '统计', permission: 'statistics:view' }
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
      },
      {
        path: 'kb/documents',
        name: 'kb-documents',
        component: () => import('@/views/knowledge/DocumentManage.vue'),
        meta: { title: '知识库文档', icon: 'FolderOpened', group: '知识库', permission: 'kb:doc:list' }
      },
      {
        path: 'kb/ask',
        name: 'kb-ask',
        component: () => import('@/views/knowledge/Ask.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound', group: '知识库', permission: 'kb:ask' }
      },
      {
        path: 'applications/submit',
        name: 'applications-submit',
        component: () => import('@/views/approval/Submit.vue'),
        meta: { title: '我的申请', icon: 'Promotion', group: '审批', permission: 'application:submit' }
      },
      {
        path: 'applications/approve',
        name: 'applications-approve',
        component: () => import('@/views/approval/Approve.vue'),
        meta: { title: '审批中心', icon: 'Stamp', group: '审批', permission: 'application:approve' }
      }
    ]
  },
  // H5 移动端页面：独立布局，不做后台缩放适配
  {
    path: '/h5',
    children: [
      {
        path: 'checkin',
        name: 'h5-checkin',
        component: () => import('@/views/h5/Checkin.vue'),
        meta: { title: '移动打卡', public: true, hidden: true }
      },
      {
        path: 'face',
        name: 'h5-face',
        component: () => import('@/views/h5/FaceCheckin.vue'),
        meta: { title: '人脸打卡（模拟）', public: true, hidden: true }
      },
      {
        // 管理员/部门主管在手机上签发员工打卡二维码（页面内自带权限与登录兜底）
        path: 'qrcode',
        name: 'h5-qrcode',
        component: () => import('@/views/h5/QrcodeIssue.vue'),
        meta: { title: '签发考勤二维码', hidden: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 登录后的默认首页：按权限与档案绑定情况选择落脚页。
 *
 * - 有 statistics:view（管理员/经理）→ 数据看板
 * - 普通员工且已绑定员工档案 → 我的考勤
 * - 未绑定档案 → 数据看板（"我的考勤"对其无意义，管理员等账号不显示该入口）
 */
export const homePath = () => {
  const permissions = JSON.parse(localStorage.getItem('lz_permissions') || '[]')
  if (permissions.includes('statistics:view')) return '/statistics/dashboard'
  return localStorage.getItem('lz_employee_id') ? '/my/records' : '/statistics/dashboard'
}

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('lz_token')
  if (!to.meta.public && !token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  // 已登录用户访问登录页时直接回首页，避免登录后还能看到登录框的割裂感
  if (to.name === 'login' && token) {
    next(homePath())
    return
  }
  // 前端侧的权限展示控制；后端仍会在接口层再做一次校验
  if (to.meta.permission && token) {
    const permissions = JSON.parse(localStorage.getItem('lz_permissions') || '[]')
    if (!permissions.includes(to.meta.permission)) {
      ElMessage.warning('当前账号没有该页面的访问权限')
      next(homePath())
      return
    }
  }
  // 个人考勤类页面需要员工档案：管理员等未绑定档案的账号直接访问 URL 时拦回首页
  if (to.meta.requiresEmployee && !localStorage.getItem('lz_employee_id')) {
    ElMessage.warning('当前账号未绑定员工档案，无个人考勤')
    next(homePath())
    return
  }
  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 联智考勤` : '联智考勤'
})

/**
 * 路由级错误兜底：懒加载页面模块失败时不再"整页空白"。
 *
 * 典型场景：前端 dev 服务（或静态部署的旧版本）已下线/更新，
 * 浏览器里还是缓存的旧页面，点击菜单时新页面的模块拉不下来，
 * vue-router 会静默失败，router-view 渲染为空——用户看到的就是白屏。
 * 这里识别这类失败，提示后自动整页刷新一次（带防循环标记），
 * 刷新后若模块仍不可用则停在当前页并给出明确指引。
 */
router.onError((error, to) => {
  const message = String(error?.message || error)
  const isChunkLoadFailure =
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed') ||
    message.includes('error loading dynamically imported module') ||
    message.includes('Loading chunk') ||
    message.includes('Loading CSS chunk')
  if (!isChunkLoadFailure) {
    console.error('[router]', error)
    return
  }
  const RELOAD_FLAG = 'lz_chunk_reload'
  if (!sessionStorage.getItem(RELOAD_FLAG)) {
    // 第一次失败：尝试带目标路由整页刷新（服务恢复后即可正常进入）
    sessionStorage.setItem(RELOAD_FLAG, to.fullPath)
    ElMessage.warning('页面资源加载失败，正在重新加载…')
    window.location.href = to.fullPath
    return
  }
  // 刷新过还是失败：大概率是前端服务没启动，给出可操作的提示
  sessionStorage.removeItem(RELOAD_FLAG)
  ElMessage.error('页面资源加载失败：请确认前端服务正在运行（web 目录执行 npm run dev），然后刷新重试')
})

// 正常导航成功后清除防循环标记
router.afterEach(() => {
  sessionStorage.removeItem('lz_chunk_reload')
})

export default router
