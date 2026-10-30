/**
 * 阶段 E 端到端验证：请假/加班审批（模块 E，REQ-E-01 ~ REQ-E-07）。
 *
 * 覆盖：
 *   REQ-E-01 提交申请（合法 / 非法入参拦截）
 *   REQ-E-02 查询本人申请（越权隔离：只能看自己的）
 *   REQ-E-03 审批人待办队列 + 审批动作
 *   REQ-E-04 同意 / 驳回带意见（驳回必须填意见）
 *   REQ-E-05 状态字段（SUBMITTED → APPROVED / REJECTED / CANCELLED）
 *   REQ-E-06 状态机防重复审批 / 撤回守卫（重复审批、撤回已审批均被拒）
 *   REQ-E-07 统计联动（后端已就绪：已通过单据的时长/状态数据可被统计读取；
 *             当前统计重算尚未消费该接入点，本脚本验证"数据已就位"而非"已联动"）
 *
 * 诚实性边界（与项目红线一致）：
 *   - 越权（无 application:approve 审批、无 application:list 查全部）必须被后端 40301 拒绝；
 *   - 参数非法必须被后端 40001 拒绝；
 *   - 状态机冲突必须被后端 40901 拒绝。
 *   注意：本项目的全局异常处理器把业务码放在 JSON body 的 code 字段、HTTP 状态恒为 200，
 *   因此断言一律读取 body.code，而不是 HTTP 状态码。
 *
 * 运行：
 *   node scripts/verify-phaseE.mjs                       （需后端已在 8080 启动）
 *   LZ_API_BASE=http://localhost:8089/api node scripts/verify-phaseE.mjs
 */
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

const BASE = process.env.LZ_API_BASE || 'http://localhost:8080/api'
const HERE = dirname(fileURLToPath(import.meta.url))

let pass = 0
let fail = 0

function check(name, ok, detail) {
  if (ok) {
    pass++
    console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`)
  } else {
    fail++
    console.log(`  ✗ ${name}${detail ? ' — ' + detail : ''}`)
  }
}

async function call(method, path, token, body) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(BASE + path, { method, headers, body: payload })
  let json = null
  try {
    json = await res.json()
  } catch {
    /* 非 JSON */
  }
  return { status: res.status, json }
}

async function login(username, password) {
  const res = await call('POST', '/auth/login', null, { username, password })
  return { token: res.json?.data?.token, permissions: res.json?.data?.permissions || [] }
}

// 用固定近未来日期，避免与任何 E-07 统计区间偶发重叠
const START = '2026-06-15T09:00:00'
const END = '2026-06-15T18:00:00' // 9 小时

async function main() {
  console.log(`=== 阶段 E：请假/加班审批（模块 E）验证 ===\nBASE=${BASE}\n`)

  const admin = await login('admin', 'Admin@123')
  const manager = await login('manager01', 'Admin@123')
  const emp = await login('LZ0001', 'Emp@123')
  const emp2 = await login('LZ0002', 'Emp@123')

  check('三个演示账号可登录',
    !!admin.token && !!manager.token && !!emp.token,
    `admin perm=${admin.permissions.length} mgr perm=${manager.permissions.length} emp perm=${emp.permissions.length}`)

  check('权限集符合预期（admin 全有 / manager 有 submit+approve 无 list / emp 仅 submit）',
    admin.permissions.includes('application:submit')
      && admin.permissions.includes('application:approve')
      && admin.permissions.includes('application:list')
      && manager.permissions.includes('application:approve')
      && !manager.permissions.includes('application:list')
      && emp.permissions.includes('application:submit')
      && !emp.permissions.includes('application:approve'),
    `admin=[${admin.permissions}] mgr=[${manager.permissions}] emp=[${emp.permissions}]`)

  // ---------- REQ-E-01 提交申请 ----------
  console.log('\n[REQ-E-01] 提交申请')
  const submitRes = await call('POST', '/applications', emp.token, {
    type: 'LEAVE', subType: '事假', startTime: START, endTime: END, reason: '家中急事需要请一天假'
  })
  const appA = submitRes.json?.data
  check('LZ0001 提交合法申请成功（code=0）',
    submitRes.json?.code === 0 && appA?.id,
    `id=${appA?.id} no=${appA?.applicationNo} status=${appA?.status} dur=${appA?.durationHours}`)
  check('提交后状态为 SUBMITTED', appA?.status === 'SUBMITTED')
  check('时长按小时计算正确（9 小时）', appA?.durationHours === 9.00 || appA?.durationHours === '9.00',
    `durationHours=${appA?.durationHours}`)
  check('员工姓名被关联带出', !!appA?.employeeName, `employeeName=${appA?.employeeName}`)

  const badOrder = await call('POST', '/applications', emp.token, {
    type: 'LEAVE', startTime: END, endTime: START, reason: '结束早于开始'
  })
  check('结束早于开始被拒（40001）', badOrder.json?.code === 40001, badOrder.json?.message)

  const badReason = await call('POST', '/applications', emp.token, {
    type: 'LEAVE', startTime: START, endTime: END, reason: 'x'
  })
  check('事由过短被拒（40001）', badReason.json?.code === 40001, badReason.json?.message)

  const badType = await call('POST', '/applications', emp.token, {
    type: 'NOPE', startTime: START, endTime: END, reason: '类型非法'
  })
  check('非法类型被拒（40001）', badType.json?.code === 40001, badType.json?.message)

  // ---------- REQ-E-02 查询本人申请 ----------
  console.log('\n[REQ-E-02] 我的申请（本人隔离）')
  const mine = await call('GET', '/applications/mine?pageNum=1&pageSize=20', emp.token)
  const mineList = mine.json?.data?.list || []
  check('LZ0001 能看到自己刚提交的申请',
    mine.json?.code === 0 && mineList.some((a) => a.id === appA?.id),
    `total=${mine.json?.data?.total}`)

  // ---------- REQ-E-03 待审批队列 + 审批动作 ----------
  console.log('\n[REQ-E-03] 待审批队列 + 审批')
  const todo = await call('GET', '/applications/todo?pageNum=1&pageSize=20', manager.token)
  const todoList = todo.json?.data?.list || []
  check('manager01 待办队列能看到 SUBMITTED 单据',
    todo.json?.code === 0 && todoList.some((a) => a.id === appA?.id && a.status === 'SUBMITTED'),
    `total=${todo.json?.data?.total}`)

  // ---------- REQ-E-06 越权守卫（无审批权） ----------
  console.log('\n[REQ-E-06] 越权守卫（权限码后端强制）')
  const empApprove = await call('POST', `/applications/${appA.id}/approve`, emp.token,
    { action: 'APPROVE', comment: '' })
  check('LZ0001（无 approve 权）审批被拒（40301）', empApprove.json?.code === 40301, empApprove.json?.message)

  const empTodo = await call('GET', '/applications/todo?pageNum=1&pageSize=20', emp.token)
  check('LZ0001（无 approve 权）访问待办被拒（40301）', empTodo.json?.code === 40301, empTodo.json?.message)

  const mgrList = await call('GET', '/applications?pageNum=1&pageSize=20', manager.token)
  check('manager01（无 list 权）查全部被拒（40301）', mgrList.json?.code === 40301, mgrList.json?.message)

  // ---------- REQ-E-04 驳回必须填意见 ----------
  console.log('\n[REQ-E-04] 同意 / 驳回带意见')
  const rejectEmpty = await call('POST', `/applications/${appA.id}/approve`, manager.token,
    { action: 'REJECT', comment: '' })
  check('驳回未填意见被拒（40001）', rejectEmpty.json?.code === 40001, rejectEmpty.json?.message)

  // ---------- REQ-E-05 / REQ-E-03 正常审批通过 ----------
  const approve = await call('POST', `/applications/${appA.id}/approve`, manager.token,
    { action: 'APPROVE', comment: '情况属实，准予请假' })
  check('manager01 审批通过成功（code=0）', approve.json?.code === 0, approve.json?.message)
  const allAfterApprove = await call('GET', '/applications?pageNum=1&pageSize=20', admin.token)
  const appAAfter = (allAfterApprove.json?.data?.list || []).find((a) => a.id === appA.id)
  check('审批后状态变为 APPROVED', appAAfter?.status === 'APPROVED',
    `status=${appAAfter?.status} approverName=${appAAfter?.approverName}`)
  check('审批意见与审批人姓名被带出',
    appAAfter?.lastAction === 'APPROVE' && !!appAAfter?.approverName,
    `lastAction=${appAAfter?.lastAction} approverName=${appAAfter?.approverName}`)

  // ---------- REQ-E-06 状态机防重复审批 ----------
  const reApprove = await call('POST', `/applications/${appA.id}/approve`, manager.token,
    { action: 'APPROVE', comment: '重复审批' })
  check('对已审批单据再次审批被拒（40901 状态机守卫）', reApprove.json?.code === 40901, reApprove.json?.message)

  const empCancelApproved = await call('POST', `/applications/${appA.id}/cancel`, emp.token)
  check('撤回已审批单据被拒（40901 状态守卫）', empCancelApproved.json?.code === 40901, empCancelApproved.json?.message)

  // ---------- REQ-E-04 另一条：正常驳回带意见 ----------
  const submitB = await call('POST', '/applications', emp.token, {
    type: 'OVERTIME', subType: '周末加班', startTime: '2026-06-20T10:00:00', endTime: '2026-06-20T15:00:00',
    reason: '项目上线需要周末加班支持'
  })
  const appB = submitB.json?.data
  check('LZ0001 提交加班申请成功', submitB.json?.code === 0 && appB?.id, `id=${appB?.id}`)
  const reject = await call('POST', `/applications/${appB.id}/approve`, manager.token,
    { action: 'REJECT', comment: '本月加班额度已用尽' })
  check('manager01 驳回成功（code=0）', reject.json?.code === 0, reject.json?.message)
  const allAfterReject = await call('GET', '/applications?pageNum=1&pageSize=20', admin.token)
  const appBAfter = (allAfterReject.json?.data?.list || []).find((a) => a.id === appB.id)
  check('驳回后状态变为 REJECTED 且意见被记录',
    appBAfter?.status === 'REJECTED' && appBAfter?.lastAction === 'REJECT' && appBAfter?.lastComment === '本月加班额度已用尽',
    `status=${appBAfter?.status} comment=${appBAfter?.lastComment}`)

  // ---------- REQ-E-02 撤回守卫（本人 + SUBMITTED 才可撤） ----------
  const submitC = await call('POST', '/applications', emp.token, {
    type: 'LEAVE', subType: '病假', startTime: '2026-07-01T09:00:00', endTime: '2026-07-01T18:00:00',
    reason: '感冒发烧需要休息一天'
  })
  const appC = submitC.json?.data
  check('LZ0001 提交第三条申请待撤回测试', submitC.json?.code === 0 && appC?.id, `id=${appC?.id}`)
  const cancelC = await call('POST', `/applications/${appC.id}/cancel`, emp.token)
  check('LZ0001 撤回本人 SUBMITTED 单据成功（code=0）', cancelC.json?.code === 0, cancelC.json?.message)
  const allAfterCancel = await call('GET', '/applications?pageNum=1&pageSize=20', admin.token)
  const appCAfter = (allAfterCancel.json?.data?.list || []).find((a) => a.id === appC.id)
  check('撤回后状态变为 CANCELLED', appCAfter?.status === 'CANCELLED', `status=${appCAfter?.status}`)

  // 用 LZ0002（employeeId=2）提交一条，LZ0001（employeeId=1）试图撤回 —— 构成真正的跨人越权
  const submitOther = await call('POST', '/applications', emp2.token, {
    type: 'LEAVE', subType: '年假', startTime: '2026-07-05T09:00:00', endTime: '2026-07-05T18:00:00',
    reason: 'LZ0002 的年假申请'
  })
  const appOther = submitOther.json?.data
  check('LZ0002 提交申请成功', submitOther.json?.code === 0 && appOther?.id, `id=${appOther?.id}`)
  const empCancelOther = await call('POST', `/applications/${appOther.id}/cancel`, emp.token)
  check('撤回他人单据被拒（40301 只能撤本人）', empCancelOther.json?.code === 40301, empCancelOther.json?.message)

  // ---------- REQ-E-07 统计联动（数据已就位，等待统计模块消费） ----------
  console.log('\n[REQ-E-07] 统计联动接入点（后端数据就绪）')
  const approvedInView = (allAfterReject.json?.data?.list || []).filter((a) => a.status === 'APPROVED')
  check('已通过单据携带可被统计消费的字段（时长/状态/审批人）',
    approvedInView.length >= 1
      && approvedInView.every((a) => typeof a.durationHours === 'number' && a.durationHours > 0 && !!a.approverName),
    `approved=${approvedInView.length} sampleDur=${approvedInView[0]?.durationHours} approver=${approvedInView[0]?.approverName}`)
  console.log('  [说明] listApprovedInRange(LocalDateTime, LocalDateTime) 已就绪并返回上述数据；'
    + '当前统计重算尚未调用它，E-07 属"后端接入点完成、前端/统计消费待接"。')

  console.log(`\n=== 结果：${pass} 项通过，${fail} 项失败 ===`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('验证脚本异常：', e)
  process.exit(1)
})
