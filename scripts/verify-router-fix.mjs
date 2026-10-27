/**
 * 路由修复回归验证（2026-10-27）
 *
 * 背景 bug：AdminLayout 菜单把 vue-router getRoutes() 返回记录的 children[].path
 * （相对路径，无前导斜杠）直接当 el-menu 的 index 用。el-menu router 模式下
 * 会把 index 原样传给 router.push()，vue-router 按「当前路由」做相对解析——
 * 从 /dashboard 点过去碰巧正确，从 /kb/documents 点就变成 /kb/attendance/qrcodes，
 * 白屏 + [Vue Router warn] No match found。
 *
 * 本脚本用项目真实安装的 vue-router 库（web/node_modules）验证：
 *   1. 先复现旧 bug（相对路径从非 / 路由解析会得到错误地址、无匹配）
 *   2. 再验证修复后的菜单 index（绝对路径）从任意路由解析都命中正确命名路由
 *   3. 验证菜单高亮 key（default-active = route.path）与 index 一致
 *
 * 运行：node scripts/verify-router-fix.mjs
 */
import { createRouter, createMemoryHistory } from '../web/node_modules/vue-router/dist/vue-router.esm-bundler.js'

let passed = 0
let failed = 0
const check = (name, cond, detail = '') => {
  if (cond) {
    passed++
    console.log(`  ✅ ${name}`)
  } else {
    failed++
    console.log(`  ❌ ${name}${detail ? ' —— ' + detail : ''}`)
  }
}

// ---- 与 web/src/router/index.js 保持同构的路由表（组件用占位对象）----
const Dummy = { render: () => null }
const routes = [
  { path: '/login', name: 'login', component: Dummy, meta: { public: true, hidden: true } },
  {
    path: '/',
    component: Dummy,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: Dummy, meta: { title: '概要看板', group: '概况' } },
      { path: 'kb/documents', name: 'kb-documents', component: Dummy, meta: { title: '知识库文档', group: '知识库' } },
      { path: 'kb/ask', name: 'kb-ask', component: Dummy, meta: { title: '智能问答', group: '知识库' } },
      { path: 'attendance/qrcodes', name: 'qrcodes', component: Dummy, meta: { title: '考勤二维码', group: '考勤' } },
      { path: 'statistics/dashboard', name: 'statistics-dashboard', component: Dummy, meta: { title: '数据看板', group: '统计' } },
      { path: 'applications/approve', name: 'applications-approve', component: Dummy, meta: { title: '审批中心', group: '审批' } },
      { path: 'applications/submit', name: 'applications-submit', component: Dummy, meta: { title: '我的申请', group: '审批' } }
    ]
  }
]

const router = createRouter({ history: createMemoryHistory(), routes })
await router.push('/dashboard')
await router.isReady()

console.log('\n【1】复现旧 bug：getRoutes() 返回记录的 children[].path 是相对路径')
const mainRoute = router.getRoutes().find((r) => r.path === '/')
const childPaths = (mainRoute?.children || []).map((c) => c.path)
check('children[].path 无前导斜杠（这正是 el-menu 拿到的 index）',
  childPaths.every((p) => !p.startsWith('/')), JSON.stringify(childPaths))

console.log('\n【2】旧 index 的相对解析行为（vue-router 真实语义）')
// 场景与用户报告一致：当前在 /kb/documents，点"考勤二维码"
await router.push('/kb/documents')
const fromKb = router.resolve('attendance/qrcodes')
check('从 /kb/documents 用相对路径解析 → /kb/attendance/qrcodes（复现用户看到的错误地址）',
  fromKb.fullPath === '/kb/attendance/qrcodes', `实际 ${fromKb.fullPath}`)
check('该地址无匹配路由（matched 为空 → 白屏 + No match found 警告）',
  fromKb.matched.length === 0)
await router.push('/statistics/dashboard')
const fromStat = router.resolve('kb/ask')
check('从 /statistics/dashboard 点"智能问答" → /statistics/kb/ask（复现第二条错误地址）',
  fromStat.fullPath === '/statistics/kb/ask', `实际 ${fromStat.fullPath}`)
check('同样无匹配', fromStat.matched.length === 0)

console.log('\n【3】修复后的逻辑：菜单 index 全部为绝对路径')
// —— 与 AdminLayout.vue 修复后的 toFullPath 完全一致 ——
const toFullPath = (child) =>
  child.path.startsWith('/') ? child.path : mainRoute.path.replace(/\/$/, '') + '/' + child.path
const absoluteIndices = (mainRoute?.children || [])
  .filter((c) => !c.meta?.hidden)
  .map((c) => toFullPath(c))
check('所有菜单 index 均以 / 开头', absoluteIndices.every((p) => p.startsWith('/')), JSON.stringify(absoluteIndices))

console.log('\n【4】绝对 index 从任意当前路由解析都命中正确命名路由')
// 遍历所有"当前页 × 所有菜单项"的组合，模拟用户在任意页面间任意跳转
const allPages = ['/dashboard', '/kb/documents', '/kb/ask', '/attendance/qrcodes', '/statistics/dashboard', '/applications/approve', '/applications/submit']
const nameByPath = Object.fromEntries(
  (mainRoute?.children || []).filter((c) => !c.meta?.hidden).map((c) => [toFullPath(c), c.name])
)
let combos = 0
let wrong = []
for (const current of allPages) {
  await router.push(current)
  for (const index of absoluteIndices) {
    combos++
    const resolved = router.resolve(index)
    if (resolved.matched.length === 0 || resolved.name !== nameByPath[index]) {
      wrong.push(`${current} → ${index} 得到 ${resolved.fullPath} (name=${resolved.name})`)
    }
  }
}
check(`全部 ${combos} 个跳转组合都命中正确路由`, wrong.length === 0, wrong.slice(0, 3).join('; '))

console.log('\n【5】菜单高亮：default-active(route.path) 与修复后的 index 同为绝对路径，可匹配')
await router.push('/attendance/qrcodes')
check('当前 route.path 与对应菜单 index 相等（此前相对 index 永远高亮不上）',
  router.currentRoute.value.path === toFullPath(mainRoute.children.find((c) => c.name === 'qrcodes')))

console.log(`\n========== 结果：通过 ${passed} 项，失败 ${failed} 项 ==========`)
process.exit(failed === 0 ? 0 : 1)
