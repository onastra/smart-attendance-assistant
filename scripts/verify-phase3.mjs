/**
 * 阶段 3 端到端验证脚本。
 *
 * 覆盖：正常打卡闭环 + 四类失败（重复/过期/伪造签名/非本场所）+ 越权查询。
 * 运行（需后端已在 8080 启动）：
 *   LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe" LZ_MYSQL_PWD=<口令> node scripts/verify-phase3.mjs
 *
 * 不提供 LZ_MYSQL_PWD 时脚本仍可运行，只是不会清理上一轮数据，
 * 可能因为 5 分钟重复打卡窗口而让个别用例失败。
 */
import { execSync } from 'node:child_process'

const BASE = process.env.LZ_API_BASE || 'http://localhost:8080/api'

/**
 * 清理上一轮验证产生的打卡记录。
 *
 * 原因：打卡有 5 分钟重复窗口，脚本连跑两次时第二次会命中 DUPLICATE_CHECKIN，
 * 看起来像功能坏了，实际是数据没清。这里只删服务端签发的 request_id（CK 前缀），
 * 演示数据（DEMO-SEED 前缀）不受影响。
 */
/**
 * 删除指定 requestId 的打卡记录。
 *
 * 用途：上一条用例刚打过同类型卡，紧接着要验证围栏时会被 5 分钟重复窗口提前拦下
 * （校验顺序里去重在围栏之前，这是合理的业务顺序）。为了让后续用例走到目标分支，
 * 这里精确删掉自己刚造的那条记录，不影响演示数据。
 */
function deleteRecordByRequestId(requestId) {
  if (!requestId || !/^[A-Za-z0-9]+$/.test(requestId)) {
    return
  }
  try {
    mysqlExec(`DELETE FROM att_record WHERE request_id='${requestId}';`)
  } catch {
    /* MySQL 通道不可用时回退 PostgreSQL（Supabase）；都失败只是该用例可能被重复窗口拦下 */
    try {
      pgExec(`DELETE FROM att_record WHERE request_id='${requestId}'`)
    } catch {
      /* 清理失败不影响后续断言 */
    }
  }
}

/**
 * 执行一条 MySQL 语句。
 *
 * 凭据一律走环境变量，不写进脚本——脚本是要进版本库的，
 * 把口令提交上去属于真实的安全事故。
 * 运行示例：LZ_MYSQL_PWD=你的口令 node scripts/verify-phase3.mjs
 */
function mysqlExec(sql) {
  const bin = process.env.LZ_MYSQL_BIN || 'mysql'
  const user = process.env.LZ_MYSQL_USER || 'root'
  const database = process.env.LZ_MYSQL_DB || 'lianzhi'
  const password = process.env.LZ_MYSQL_PWD
  const auth = password ? `-u${user} -p${password}` : `-u${user}`
  execSync(`"${bin}" ${auth} ${database} -e "${sql}"`, { stdio: 'ignore' })
}

/** 执行一条 PostgreSQL 语句（Supabase 回退通道，凭据全部走环境变量）。
 *  用法：LZ_PG_HOST=aws-0-xxx.pooler.supabase.com LZ_PG_USER=postgres.xxx LZ_PG_PWD=口令 node scripts/verify-phase3.mjs
 */
function pgExec(sql) {
  const host = process.env.LZ_PG_HOST
  if (!host) {
    throw new Error('LZ_PG_HOST 未设置')
  }
  const code = [
    'import psycopg2',
    // sslmode 默认 require（Supabase 强制 SSL）；本地 Docker 的 PG 默认不开 SSL，
    // 用 LZ_PG_SSLMODE=disable 覆盖，否则整条清理通道会静默失效
    `conn = psycopg2.connect(host=${JSON.stringify(host)}, port=${JSON.stringify(process.env.LZ_PG_PORT || '5432')}, dbname=${JSON.stringify(process.env.LZ_PG_DB || 'postgres')}, user=${JSON.stringify(process.env.LZ_PG_USER || '')}, password=${JSON.stringify(process.env.LZ_PG_PWD || '')}, sslmode=${JSON.stringify(process.env.LZ_PG_SSLMODE || 'require')})`,
    'cur = conn.cursor()',
    `cur.execute(${JSON.stringify(sql)})`,
    'conn.commit()',
  ].join('\n')
  execSync('python -', { input: code, stdio: ['pipe', 'ignore', 'ignore'] })
}


function cleanPreviousRuns() {
  if (process.env.LZ_MYSQL_PWD) {
    try {
      mysqlExec("DELETE FROM att_record WHERE request_id LIKE 'CK%';")
      console.log('  已清理上一轮验证产生的打卡记录（MySQL）')
      return
    } catch {
      console.log('  [提示] mysql 客户端不可用，尝试 PostgreSQL 清理通道')
    }
  }
  if (process.env.LZ_PG_HOST) {
    try {
      pgExec("DELETE FROM att_record WHERE request_id LIKE 'CK%'")
      console.log('  已清理上一轮验证产生的打卡记录（PostgreSQL）')
      return
    } catch {
      console.log('  [提示] PostgreSQL 清理通道不可用')
    }
  }
  console.log('  [提示] 未配置 LZ_MYSQL_PWD 或 LZ_PG_HOST，跳过历史记录清理；若上一轮刚跑过，重复打卡用例可能失败')
}

let passed = 0
let failed = 0

async function call(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'application/json'
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json = null
  try {
    json = JSON.parse(text)
  } catch {
    /* 非 JSON 不解析 */
  }
  return { status: res.status, json, text }
}

function check(name, condition, detail) {
  if (condition) {
    passed++
    console.log(`  [PASS] ${name}`)
  } else {
    failed++
    console.log(`  [FAIL] ${name} -> ${JSON.stringify(detail)}`)
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function login(username, password) {
  const res = await call('POST', '/auth/login', null, { username, password })
  if (!res.json || res.json.code !== 0) {
    throw new Error(`登录失败 ${username}: ${JSON.stringify(res.json)}`)
  }
  return res.json.data
}

async function main() {
  console.log('=== 阶段 3 核心考勤闭环验证 ===\n')
  cleanPreviousRuns()

  // ---------- 1. 登录 ----------
  const admin = await login('admin', 'Admin@123')
  const emp = await login('LZ0001', 'Emp@123')
  console.log(`1) 登录：admin 权限 ${admin.permissions.length} 项，LZ0001 权限 ${emp.permissions.length} 项，员工 ID=${emp.employeeId}`)
  check('admin 具备二维码签发权限', admin.permissions.includes('attendance:qrcode:create'))
  check('LZ0001 具备打卡权限', emp.permissions.includes('attendance:checkin'))
  check('admin 具备失效权限（阶段3新增）', admin.permissions.includes('attendance:qrcode:invalidate'))

  // ---------- 2. 签发二维码 ----------
  const issue = await call('POST', '/qrcodes', admin.token, { placeId: 1, qrType: 'DYNAMIC', ttlSeconds: 300 })
  const qr = issue.json?.data
  console.log(`\n2) 签发二维码 id=${qr?.id} 场所=${qr?.placeName} payload=${qr?.payload}`)
  check('签发成功', issue.json?.code === 0 && qr?.id > 0)
  check('payload 含签名段', /\|s=[0-9a-f]{64}$/.test(qr?.payload || ''))
  check('返回图片地址', String(qr?.imageUrl || '').includes('/image'))

  const png = await fetch(BASE + '/qrcodes/' + qr.id + '/image', {
    headers: { Authorization: `Bearer ${admin.token}` }
  })
  const pngBuffer = Buffer.from(await png.arrayBuffer())
  check('PNG 可获取且是图片', png.status === 200 && pngBuffer[1] === 0x50 && pngBuffer[2] === 0x4e && pngBuffer[3] === 0x47,
    { status: png.status, head: pngBuffer.subarray(0, 4).toString('hex') })

  // ---------- 3. 正常打卡 ----------
  const rid = await call('POST', '/checkin/request-id', emp.token)
  const requestId = rid.json?.data?.requestId
  check('幂等请求号已签发', !!requestId)

  // 固定用外勤类型：上下班打卡在重复窗口内只能成功一次，外勤便于脚本重复运行
  const ok = await call('POST', '/checkin', emp.token, { content: qr.payload, requestId, checkinType: 'FIELD' })
  console.log(`\n3) 打卡成功：${JSON.stringify(ok.json?.data)}`)
  check('打卡成功', ok.json?.code === 0)
  check('落库返回结果', ['NORMAL', 'LATE', 'EARLY'].includes(ok.json?.data?.result), ok.json?.data?.result)
  check('回显员工姓名', ok.json?.data?.employeeName === '张明')
  check('未上报坐标时如实标注', String(ok.json?.data?.remark || '').includes('未做围栏校验'))

  // ---------- 4. 四类失败 ----------
  console.log('\n4) 失败场景')

  // 4.1 同一 requestId 重复提交 → 幂等（不产生新记录）
  const dup = await call('POST', '/checkin', emp.token, { content: qr.payload, requestId })
  check('同一 requestId 命中幂等', dup.json?.code === 0 && dup.json?.data?.idempotent === true,
    dup.json)

  // 4.2 新 requestId 在重复窗口内再打 → DUPLICATE_CHECKIN
  const rid2 = await call('POST', '/checkin/request-id', emp.token)
  const dup2 = await call('POST', '/checkin', emp.token,
    { content: qr.payload, requestId: rid2.json?.data?.requestId, checkinType: 'FIELD' })
  check('窗口内重复打卡被拒', dup2.json?.code === 40901 && dup2.json?.data?.reason === 'DUPLICATE_CHECKIN', dup2.json)

  // 4.3 篡改签名 → QR_SIGN_INVALID
  const tampered = qr.payload.replace(/\|s=[0-9a-f]{64}$/, '|s=' + '0'.repeat(64))
  const rid3 = await call('POST', '/checkin/request-id', emp.token)
  const fake = await call('POST', '/checkin', emp.token, { content: tampered, requestId: rid3.json?.data?.requestId })
  check('伪造签名被拒', fake.json?.code === 40901 && fake.json?.data?.reason === 'QR_SIGN_INVALID', fake.json)

  // 4.4 过期二维码 → QR_EXPIRED
  const shortLived = await call('POST', '/qrcodes', admin.token, { placeId: 1, qrType: 'DYNAMIC', ttlSeconds: 2 })
  await sleep(3000)
  const rid4 = await call('POST', '/checkin/request-id', emp.token)
  const expired = await call('POST', '/checkin', emp.token, {
    content: shortLived.json?.data?.payload,
    requestId: rid4.json?.data?.requestId
  })
  check('过期二维码被拒', expired.json?.code === 40901 && expired.json?.data?.reason === 'QR_EXPIRED', expired.json)

  // 4.5 非本场所（张明绑定场所1，用场所2 的码）→ PLACE_NOT_BOUND
  const place2Qr = await call('POST', '/qrcodes', admin.token, { placeId: 2, qrType: 'FIXED' })
  const rid5 = await call('POST', '/checkin/request-id', emp.token)
  const notBound = await call('POST', '/checkin', emp.token, {
    content: place2Qr.json?.data?.payload,
    requestId: rid5.json?.data?.requestId
  })
  check('非本场所打卡被拒', notBound.json?.code === 40901 && notBound.json?.data?.reason === 'PLACE_NOT_BOUND', notBound.json)

  // 4.6 手动失效后打卡 → QR_INVALID
  const toInvalidate = await call('POST', '/qrcodes', admin.token, { placeId: 1, qrType: 'FIXED' })
  await call('POST', `/qrcodes/${toInvalidate.json?.data?.id}/invalidate`, admin.token)
  const rid6 = await call('POST', '/checkin/request-id', emp.token)
  const invalid = await call('POST', '/checkin', emp.token, {
    content: toInvalidate.json?.data?.payload,
    requestId: rid6.json?.data?.requestId
  })
  check('已作废二维码被拒', invalid.json?.code === 40901 && invalid.json?.data?.reason === 'QR_INVALID', invalid.json)

  // ---------- 5. 查询与越权 ----------
  console.log('\n5) 查询与权限')
  const my = await call('GET', '/my/attendance/records?pageNum=1&pageSize=5', emp.token)
  check('本人考勤可查询', my.json?.code === 0 && my.json?.data?.page?.total >= 1, my.json?.data?.page?.total)
  check('本人查询强制 SELF 范围', my.json?.data?.dataScope === 'SELF')

  const forbidden = await call('GET', '/admin/attendance/records?pageNum=1&pageSize=5', emp.token)
  check('普通员工调管理员接口返回 40301', forbidden.json?.code === 40301, forbidden.json)

  const adminQuery = await call('GET', '/admin/attendance/records?pageNum=1&pageSize=5', admin.token)
  check('管理员可查询', adminQuery.json?.code === 0 && adminQuery.json?.data?.page?.total >= 1, adminQuery.json?.data?.page?.total)
  check('管理员数据范围为 ALL', adminQuery.json?.data?.dataScope === 'ALL')

  const filtered = await call('GET', '/admin/attendance/records?pageNum=1&pageSize=5&keyword=张明', admin.token)
  check('按姓名筛选生效', filtered.json?.data?.page?.list?.every((r) => r.employeeName === '张明'),
    filtered.json?.data?.page?.list?.map((r) => r.employeeName))

  const list = await call('GET', '/qrcodes?pageNum=1&pageSize=10', admin.token)
  check('二维码列表可查', list.json?.code === 0 && list.json?.data?.total >= 4, list.json?.data?.total)

  // ---------- 6. 围栏校验（坐标来自 biz_place：总部 A 座 118.7416, 31.9963，半径 300 米）----------
  console.log('\n6) 围栏校验与一次性码')
  const nearQr = await call('POST', '/qrcodes', admin.token, { placeId: 1, qrType: 'FIXED' })
  const rid7 = await call('POST', '/checkin/request-id', emp.token)
  const near = await call('POST', '/checkin', emp.token, {
    content: nearQr.json?.data?.payload,
    requestId: rid7.json?.data?.requestId,
    checkinType: 'ON_DUTY',
    lng: 118.7420,
    lat: 31.9965
  })
  check('场所附近坐标打卡成功', near.json?.code === 0, near.json)
  check('围栏校验已执行并回显距离', near.json?.data?.locationChecked === true && near.json?.data?.distanceMeters >= 0,
    { checked: near.json?.data?.locationChecked, distance: near.json?.data?.distanceMeters })

  // 释放刚占用的重复窗口，让围栏分支成为本条用例的唯一判定点
  deleteRecordByRequestId(rid7.json?.data?.requestId)
  const rid8 = await call('POST', '/checkin/request-id', emp.token)
  const far = await call('POST', '/checkin', emp.token, {
    content: nearQr.json?.data?.payload,
    requestId: rid8.json?.data?.requestId,
    checkinType: 'ON_DUTY',
    lng: 119.5,
    lat: 32.5
  })
  check('超出半径被拒', far.json?.code === 40901 && far.json?.data?.reason === 'OUT_OF_RANGE', far.json)

  // 一次性码：第一次成功，第二次 QR_USED
  const onceQr = await call('POST', '/qrcodes', admin.token, { placeId: 1, qrType: 'FIXED', onceOnly: 1 })
  const rid9 = await call('POST', '/checkin/request-id', emp.token)
  const firstUse = await call('POST', '/checkin', emp.token, {
    content: onceQr.json?.data?.payload,
    requestId: rid9.json?.data?.requestId,
    checkinType: 'OFF_DUTY'
  })
  check('一次性码首次可用', firstUse.json?.code === 0, firstUse.json)

  // 用另一个账号（manager01 关联同一名员工张明）再次提交，验证"一次性"生效
  const emp2 = await login('manager01', 'Admin@123')
  const rid10 = await call('POST', '/checkin/request-id', emp2.token)
  const secondUse = await call('POST', '/checkin', emp2.token, {
    content: onceQr.json?.data?.payload,
    requestId: rid10.json?.data?.requestId,
    checkinType: 'OFF_DUTY'
  })
  check('一次性码二次使用被拒', secondUse.json?.code === 40901 && secondUse.json?.data?.reason === 'QR_USED', secondUse.json)

  console.log(`\n=== 结果：通过 ${passed} 项，失败 ${failed} 项 ===`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('验证脚本异常：', e)
  process.exit(1)
})
