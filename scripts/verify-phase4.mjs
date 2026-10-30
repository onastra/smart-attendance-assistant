/**
 * 阶段 4 端到端验证脚本：统计与可视化。
 *
 * 覆盖：日汇总重算（幂等）、summary / trend / department / place-heat 四个接口、
 * 数据范围收敛（越权）、空数据与非法参数容错、与数据库手工核对。
 *
 * 运行（需后端已在 8080 启动）：
 *   LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe" LZ_MYSQL_PWD=<口令> node scripts/verify-phase4.mjs
 *
 * 不提供 LZ_MYSQL_PWD 时跳过"与数据库核对"相关的几条断言，其余照常执行。
 */
import { execSync } from 'node:child_process'

const BASE = process.env.LZ_API_BASE || 'http://localhost:8080/api'
const RANGE = { from: '2026-10-12', to: '2026-10-25' }

let passed = 0
let failed = 0

function check(name, ok, detail = '') {
  if (ok) {
    passed++
    console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`)
  } else {
    failed++
    console.log(`  ✗ ${name}${detail ? ' — ' + detail : ''}`)
  }
}

function mysqlQuery(sql) {
  if (!process.env.LZ_MYSQL_PWD) {
    return null
  }
  const bin = process.env.LZ_MYSQL_BIN || 'mysql'
  const user = process.env.LZ_MYSQL_USER || 'root'
  const database = process.env.LZ_MYSQL_DB || 'lianzhi'
  return execSync(`"${bin}" -u${user} -p${process.env.LZ_MYSQL_PWD} ${database} -N -e "${sql}"`, {
    encoding: 'utf8'
  }).trim()
}

/**
 * PostgreSQL 查询通道（2026-10-27 数据库迁到 PG 后补，与 phase3/phase5 同款写法）。
 * 没有它的话，本套件在纯 PG 环境下会静默跳过"与数据库逐行核对"，
 * 看起来全绿实则少验证了一层。
 */
function pgQuery(sql) {
  if (!process.env.LZ_PG_HOST) {
    return null
  }
  const code = [
    'import psycopg2',
    `conn = psycopg2.connect(host=${JSON.stringify(process.env.LZ_PG_HOST)}, port=${JSON.stringify(process.env.LZ_PG_PORT || '5432')}, dbname=${JSON.stringify(process.env.LZ_PG_DB || 'postgres')}, user=${JSON.stringify(process.env.LZ_PG_USER || '')}, password=${JSON.stringify(process.env.LZ_PG_PWD || '')}, sslmode=${JSON.stringify(process.env.LZ_PG_SSLMODE || 'require')})`,
    'cur = conn.cursor()',
    `cur.execute(${JSON.stringify(sql)})`,
    'rows = cur.fetchall()',
    'print("\\n".join("|".join("" if v is None else str(v) for v in r) for r in rows))',
  ].join('\n')
  return execSync('python -', { input: code, encoding: 'utf8' }).trim()
}

/** 库内查询：MySQL 与 PostgreSQL 双通道，都没有就返回 null（调用方会提示跳过） */
function dbQuery(mysqlSql, pgSql) {
  const viaMysql = mysqlQuery(mysqlSql)
  if (viaMysql !== null) {
    return viaMysql
  }
  return pgQuery(pgSql || mysqlSql)
}

async function call(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })
  return { status: res.status, json: await res.json().catch(() => null) }
}

async function login(username, password) {
  const res = await call('POST', '/auth/login', null, { username, password })
  return { token: res.json?.data?.token, json: res.json }
}

async function main() {
  console.log('=== 阶段 4 统计与可视化验证 ===\n')

  const admin = await login('admin', 'Admin@123')
  const manager = await login('manager01', 'Admin@123')
  const emp = await login('LZ0001', 'Emp@123')
  check('三个演示账号可登录', !!admin.token && !!manager.token && !!emp.token)

  // ---------- 1. 日汇总重算与幂等 ----------
  const r1 = await call('POST', `/statistics/daily-rebuild?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const rows1 = r1.json?.data?.affectedRows
  const days1 = r1.json?.data?.days
  check('区间重算成功', r1.json?.code === 0 && rows1 > 0, `${days1} 天 / ${rows1} 行`)

  const r2 = await call('POST', `/statistics/daily-rebuild?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  check('重复重算行数一致（幂等）', r2.json?.data?.affectedRows === rows1, `${r2.json?.data?.affectedRows} 行`)

  const statRows = dbQuery(`SELECT COUNT(*) FROM att_daily_stat WHERE stat_date BETWEEN '${RANGE.from}' AND '${RANGE.to}';`)
  if (statRows !== null) {
    check('汇总行数与重算结果一致', Number(statRows) === rows1, `库内 ${statRows} 行`)

    // 抽一天跟库核对：重算两次后，同一天的判定结果必须逐字一致（SQL 必须写成单行，
    // Windows 下 execSync 走 cmd.exe，多行 SQL 会被截断）。
    // MySQL 用 GROUP_CONCAT，PG 用 string_agg —— 两边拼出来的串必须完全一致才能比对
    const snapshotMysql =
      "SELECT GROUP_CONCAT(CONCAT(employee_id,'=',status,'/',work_minutes,'/',checkin_count) ORDER BY employee_id) FROM att_daily_stat WHERE stat_date='2026-10-13';"
    const snapshotPg =
      "SELECT string_agg(employee_id||'='||status||'/'||work_minutes||'/'||checkin_count, ',' ORDER BY employee_id) FROM att_daily_stat WHERE stat_date='2026-10-13'"
    const snap1 = dbQuery(snapshotMysql, snapshotPg)
    await call('POST', '/statistics/daily-rebuild?date=2026-10-13', admin.token)
    const snap2 = dbQuery(snapshotMysql, snapshotPg)
    check('单日重算内容逐行一致（真幂等）', snap1 === snap2 && !!snap1)
  } else {
    console.log('  [提示] 未设置 LZ_MYSQL_PWD / LZ_PG_HOST，跳过与数据库的逐行核对')
  }

  // ---------- 2. summary ----------
  const sum = await call('GET', `/statistics/summary?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const s = sum.json?.data?.summary || {}
  check('summary 返回成功', sum.json?.code === 0)
  check('人天数 = 员工数 × 天数', s.personDays === s.employeeCount * s.statDays,
    `${s.employeeCount} 人 × ${s.statDays} 天 = ${s.personDays}`)
  check('应出勤人天已扣除休息日', s.workdayPersonDays === s.personDays - s.restDays,
    `${s.personDays} - ${s.restDays} = ${s.workdayPersonDays}`)
  check('出勤率口径正确（出勤 / 应出勤）',
    Math.abs(s.attendanceRate - ((s.normalDays + s.lateDays + s.earlyDays) * 100 / s.workdayPersonDays)) < 0.1,
    `${s.attendanceRate}%`)
  check('演示数据被如实标注', s.dataSource === 'mixed' && /演示/.test(s.dataSourceNote || ''),
    `${s.dataSource}：${s.dataSourceNote}`)
  check('平均工时为正', s.avgWorkMinutes > 0, `${s.avgWorkMinutes} 分钟`)

  // ---------- 3. trend ----------
  const day = await call('GET', `/statistics/trend?type=day&dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const dayPoints = day.json?.data?.points || []
  check('按日趋势点数 = 统计天数', dayPoints.length === s.statDays, `${dayPoints.length} 点`)
  check('趋势点字段完整', dayPoints.every((p) => p.period && typeof p.checkinCount === 'number' && typeof p.attendanceRate === 'number'))

  const week = await call('GET', `/statistics/trend?type=week&dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const weekPoints = week.json?.data?.points || []
  check('按周趋势可聚合', weekPoints.length >= 2 && /^\d{4}-W\d{2}$/.test(weekPoints[0].period),
    weekPoints.map((p) => p.period).join(', '))

  const month = await call('GET', `/statistics/trend?type=month&dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const monthPoints = month.json?.data?.points || []
  check('按月趋势格式正确', monthPoints.length >= 1 && /^\d{4}-\d{2}$/.test(monthPoints[0].period),
    monthPoints.map((p) => p.period).join(', '))

  // ---------- 4. department ----------
  const dept = await call('GET', `/statistics/department?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const items = dept.json?.data?.items || []
  check('部门对比有数据', items.length >= 2, items.map((i) => `${i.deptName}(${i.checkinCount})`).join(', '))
  check('部门出勤率在 0~100 之间', items.every((i) => i.attendanceRate >= 0 && i.attendanceRate <= 100))
  const deptSum = items.reduce((acc, i) => acc + i.checkinCount, 0)
  check('部门打卡次数之和 = 总打卡次数', deptSum === s.checkinCount, `${deptSum} vs ${s.checkinCount}`)

  // ---------- 5. place-heat ----------
  const heat = await call('GET', `/statistics/place-heat?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  const hours = heat.json?.data?.hours || []
  check('场所时段分布有数据', hours.length > 0, `${hours.length} 个时段点`)
  check('小时落在 0~23', hours.every((h) => h.hour >= 0 && h.hour <= 23))
  check('无地图 Key 时如实声明', heat.json?.data?.mapEnabled === false && !!heat.json?.data?.note,
    heat.json?.data?.note)

  // ---------- 6. 数据范围与越权 ----------
  check('管理员数据范围为 ALL', sum.json?.data?.dataScope === 'ALL')

  const mgrSum = await call('GET', `/statistics/summary?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, manager.token)
  check('部门主管可看看板', mgrSum.json?.code === 0 && mgrSum.json?.data?.dataScope === 'DEPT',
    `dataScope=${mgrSum.json?.data?.dataScope}`)
  const mgrDept = await call('GET', `/statistics/department?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, manager.token)
  const mgrItems = mgrDept.json?.data?.items || []
  check('主管的部门对比只剩本部门', mgrItems.length === 1, mgrItems.map((i) => i.deptName).join(', '))

  const empSum = await call('GET', `/statistics/summary?dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, emp.token)
  check('普通员工看统计被拒（40301）', empSum.json?.code === 40301, `code=${empSum.json?.code}`)

  const mgrRebuild = await call('POST', '/statistics/daily-rebuild?date=2026-10-13', manager.token)
  check('主管不能触发重算（40301）', mgrRebuild.json?.code === 40301, `code=${mgrRebuild.json?.code}`)

  // ---------- 7. 容错 ----------
  const empty = await call('GET', '/statistics/summary?dateFrom=2020-01-01&dateTo=2020-01-31', admin.token)
  check('空数据返回全零而非报错', empty.json?.code === 0 && empty.json?.data?.summary?.personDays === 0)
  const emptyTrend = await call('GET', '/statistics/trend?type=day&dateFrom=2020-01-01&dateTo=2020-01-31', admin.token)
  check('空数据趋势返回空数组', emptyTrend.json?.code === 0 && (emptyTrend.json?.data?.points || []).length === 0)

  const badRange = await call('GET', '/statistics/summary?dateFrom=2026-10-25&dateTo=2026-10-12', admin.token)
  check('日期倒序返回 40001', badRange.json?.code === 40001, `code=${badRange.json?.code}`)

  const badType = await call('GET', `/statistics/trend?type=year&dateFrom=${RANGE.from}&dateTo=${RANGE.to}`, admin.token)
  check('非法趋势粒度返回 40001', badType.json?.code === 40001, `code=${badType.json?.code}`)

  const badRebuild = await call('POST', '/statistics/daily-rebuild?dateFrom=2026-10-25&dateTo=2026-10-12', admin.token)
  check('重算区间倒序返回 40001', badRebuild.json?.code === 40001, `code=${badRebuild.json?.code}`)

  // ---------- 8. 任务日志 ----------
  const jobLogs = mysqlQuery("SELECT COUNT(*) FROM job_exec_log WHERE job_name LIKE 'DAILY_STAT%';")
  if (jobLogs !== null) {
    check('重算写入任务执行日志', Number(jobLogs) > 0, `${jobLogs} 条`)
  }

  console.log(`\n=== 通过 ${passed} 项，失败 ${failed} 项 ===`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('验证脚本异常：', e)
  process.exit(1)
})
