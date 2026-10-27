/**
 * 阶段 5 端到端验证：知识库（模块 F）+ 人脸识别适配层（模块 G）。
 *
 * 覆盖两类东西：
 *   1) 能力是否真的работает —— 用真实生成的 PDF/DOCX/XLSX/PPTX/MD 跑通解析→切片→检索→问答；
 *   2) 诚实性是否成立 —— 未命中不许编造、无 Key 必须降级、模拟人脸必须标 mock、越权必须被拒。
 *
 * 已知现象（不是缺陷，但值得知道）：
 *   MySQL 5.7 的 InnoDB 全文索引对刚插入的行存在极短的缓存合并时延，
 *   上传完立刻提问偶发"这一段还没被索引到"。生产上应等索引刷新或重试；
 *   本脚本连续上传 5 篇后立刻检索，若偶发单条不命中，重跑一次即为全绿。
 *
 * 运行：
 *   node scripts/verify-phase5.mjs            （需后端已在 8080 启动）
 * 可选环境变量：
 *   LZ_MYSQL_BIN   mysql 客户端绝对路径（用于清理上一轮数据，保证可重复运行）
 *   LZ_MYSQL_PWD   数据库口令（不设则跳过清理，去重用例可能被上一轮数据影响）
 */
import { readFileSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const BASE = process.env.LZ_API_BASE || 'http://localhost:8080/api'
const HERE = dirname(fileURLToPath(import.meta.url))
const SAMPLE_DIR = join(HERE, '..', 'docs', 'samples', 'kb')

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
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(BASE + path, { method, headers, body: payload })
  let json = null
  try {
    json = await res.json()
  } catch {
    /* 非 JSON 响应（如二进制）时保持 null */
  }
  return { status: res.status, json }
}

async function login(username, password) {
  const res = await call('POST', '/auth/login', null, { username, password })
  return { token: res.json?.data?.token, permissions: res.json?.data?.permissions || [] }
}

async function upload(token, fileName, visibility = 'PUBLIC') {
  const fd = new FormData()
  const buf = readFileSync(join(SAMPLE_DIR, fileName))
  fd.append('file', new Blob([buf]), fileName)
  fd.append('visibility', visibility)
  return call('POST', '/kb/documents', token, fd)
}

/** 执行一条 PostgreSQL 语句（Supabase 回退通道，凭据全部走环境变量） */
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
  // 口令不写进文件：凭据一律走环境变量，没给就跳过清理，只影响可重复性，不影响被测能力
  if (process.env.LZ_MYSQL_BIN && process.env.LZ_MYSQL_PWD) {
    try {
      execSync(
        `"${process.env.LZ_MYSQL_BIN}" -uroot -p${process.env.LZ_MYSQL_PWD} lianzhi -e ` +
          '"DELETE FROM kb_qa_log; DELETE FROM kb_chunk; DELETE FROM kb_document; DELETE FROM face_feature;"',
        { stdio: 'ignore' }
      )
      console.log('  已清理上一轮的知识库与人脸数据（MySQL）')
      return
    } catch {
      console.log('  [提示] mysql 清理失败，尝试 PostgreSQL 清理通道')
    }
  }
  if (process.env.LZ_PG_HOST) {
    try {
      for (const sql of [
        'DELETE FROM kb_qa_log',
        'DELETE FROM kb_chunk',
        'DELETE FROM kb_message',
        'DELETE FROM kb_conversation',
        'DELETE FROM kb_document',
        'DELETE FROM face_feature',
        'DELETE FROM sys_file',
      ]) {
        pgExec(sql)
      }
      console.log('  已清理上一轮的知识库与人脸数据（PostgreSQL）')
      return
    } catch {
      console.log('  [提示] PostgreSQL 清理通道不可用')
    }
  }
  console.log('  [提示] 未配置 LZ_MYSQL_BIN/LZ_MYSQL_PWD 或 LZ_PG_HOST，跳过清理；'
    + '若上一轮已上传过同名文档，去重用例会报 409')
}

/** 生成一张最小合法 PNG（1x1），尾部附加随机字节以区分两张"不同照片" */
function pngBytes(seed) {
  const base = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+7QAAAABJRU5ErkJggg==',
    'base64'
  )
  return Buffer.concat([base, Buffer.from(`mock-face-${seed}`)])
}

function uploadImage(token, bytes, name = 'face.png') {
  const fd = new FormData()
  fd.append('image', new Blob([bytes]), name)
  return fd
}

async function main() {
  console.log('=== 阶段 5：知识库（模块 F）与人脸识别适配层（模块 G）验证 ===\n')
  cleanPreviousRuns()

  const admin = await login('admin', 'Admin@123')
  const emp = await login('emp01', 'Emp@123')
  const manager = await login('manager01', 'Admin@123')
  check('三个演示账号可登录', !!admin.token && !!emp.token && !!manager.token)

  // ---------- 1. 文档解析与索引 ----------
  console.log('\n[1] 文档解析 → 切片 → 索引')
  const files = [
    '考勤管理制度.md',
    '员工请假与加班管理办法.docx',
    '考勤参数速查表.xlsx',
    '新员工考勤系统使用培训.pptx',
    '办公场所与打卡点说明.pdf'
  ]
  const uploaded = {}
  for (const file of files) {
    const res = await upload(admin.token, file)
    const doc = res.json?.data?.document
    uploaded[file] = doc
    check(`${file} 上传并解析成功`,
      res.json?.code === 0 && doc?.status === 'READY' && doc?.chunkCount > 0,
      `status=${doc?.status} chunks=${doc?.chunkCount}${doc?.parseMessage ? ' msg=' + doc.parseMessage : ''}`)
  }

  check('至少一篇文档切出多个切片（切片策略真的生效）',
    files.some((f) => (uploaded[f]?.chunkCount || 0) > 1),
    files.map((f) => `${f.slice(0, 6)}=${uploaded[f]?.chunkCount}`).join(' '))

  // ---------- 2. 重复与非法上传 ----------
  console.log('\n[2] 上传边界')
  const dup = await upload(admin.token, '考勤管理制度.md')
  check('重复内容上传被拒（40901）', dup.json?.code === 40901, dup.json?.message)

  const badFd = new FormData()
  badFd.append('file', new Blob([Buffer.from('hello')]), 'virus.exe')
  const bad = await call('POST', '/kb/documents', admin.token, badFd)
  check('不支持的扩展名被拒（40001）', bad.json?.code === 40001, bad.json?.message)

  const noPerm = await upload(emp.token, '考勤管理制度.md', 'PUBLIC')
  check('普通员工上传被拒（40301）', noPerm.json?.code === 40301, noPerm.json?.message)

  // ---------- 3. 列表与可见范围 ----------
  console.log('\n[3] 列表与可见范围')
  const list = await call('GET', '/kb/documents?pageNum=1&pageSize=20', admin.token)
  check('文档列表可查', list.json?.code === 0 && list.json?.data?.total === 5,
    `total=${list.json?.data?.total}`)

  const mdDoc = uploaded['考勤管理制度.md']
  const priv = await call(
    'PUT', `/kb/documents/${mdDoc.id}/visibility?visibility=PRIVATE`, admin.token)
  check('文档可见范围改为 PRIVATE', priv.json?.code === 0
    && priv.json?.data?.document?.visibility === 'PRIVATE')

  // admin 是自己上传的，仍可读；emp01 不该读到
  const adminHit = await call('POST', '/kb/ask', admin.token, { question: '迟到宽限是多少分钟' })
  const empHit = await call('POST', '/kb/ask', emp.token, { question: '迟到宽限是多少分钟' })
  const adminIds = (adminHit.json?.data?.sources || []).map((s) => s.documentId)
  check('PRIVATE 文档对上传者仍可检索',
    adminIds.includes(mdDoc.id),
    `code=${adminHit.json?.code} 期望含 ${mdDoc.id}，实际 [${adminIds}]`)
  check('PRIVATE 文档对他人不可检索（越权过滤生效）',
    !(empHit.json?.data?.sources || []).some((s) => s.documentId === mdDoc.id),
    `员工命中 ${(empHit.json?.data?.sources || []).length} 段`)

  // ---------- 4. 问答：命中 / 来源 / 模式 ----------
  console.log('\n[4] 问答链路')
  // 问题刻意选短句：ngram 检索下长句里的无关二字词会摊薄相关度，
  // 这是检索实现的真实特性，不是缺陷——但验证要用能体现"命中"的问法。
  const ask = await call('POST', '/kb/ask', emp.token, { question: '请假需要提前申请吗' })
  const askData = ask.json?.data
  check('提问返回成功', ask.json?.code === 0)
  check('命中切片非空', (askData?.sources || []).length > 0, `命中 ${(askData?.sources || []).length} 段`)
  check('来源含文档名与位置',
    (askData?.sources || []).every((s) => s.documentTitle && s.section),
    askData?.sources?.[0] ? `${askData.sources[0].documentTitle} · ${askData.sources[0].section}` : '')
  check('答案基于检索依据 grounded=true', askData?.grounded === true)

  // —— LLM 能力断言按环境分叉（2026-10-27 改）——
  // 脚本不再预设「一定没配 Key」：本机已配置 SiliconFlow Key（真实生成模式），
  // 无 Key 环境（retrieval-only）也仍需支持。两种环境下的正确行为分别断言。
  const llm = await call('GET', '/kb/llm-status', emp.token)
  const llmData = llm.json?.data
  const llmAvailable = llmData?.available === true
  check('能力状态接口如实返回 available（布尔）',
    llm.json?.code === 0 && typeof llmData?.available === 'boolean',
    `available=${llmData?.available} mode=${llmData?.mode}`)
  if (llmAvailable) {
    check('已配置 Key 时能力状态 mode=llm-augmented',
      llmData?.mode === 'llm-augmented' && !!llmData?.model, `mode=${llmData?.mode}`)
    const askMode = askData?.mode
    if (askMode === 'llm-augmented') {
      check('生成模式答案非空且有模型名', typeof askData?.answer === 'string'
        && askData.answer.length > 0 && !!askData?.model, `model=${askData?.model}`)
    } else {
      // 生成调用瞬时失败（限流/超时）时必须走设计好的降级路径，不能 5xx
      check('生成失败时降级为检索模式并带明确提示',
        askMode === 'retrieval-only' && !!askData?.llmNotice,
        `mode=${askMode} notice=${askData?.llmNotice?.slice(0, 24)}…`)
    }
  } else {
    check('未配置 Key 时 available=false 且 mode=retrieval-only',
      llmData?.mode === 'retrieval-only', `mode=${llmData?.mode}`)
    check('无 Key 时降级为检索模式 mode=retrieval-only',
      askData?.mode === 'retrieval-only', `mode=${askData?.mode}`)
    check('检索模式带明确提示文案', !!askData?.llmNotice, askData?.llmNotice?.slice(0, 30) + '…')
  }

  // ---------- 5. 不编造 ----------
  console.log('\n[5] 检索不足时不编造')
  const nonsense = await call('POST', '/kb/ask', emp.token,
    { question: '量子纠缠超光速通信卫星轨道设计方案' })
  const nsData = nonsense.json?.data
  check('无命中时 grounded=false', nsData?.grounded === false, `reason=${nsData?.reason}`)
  check('无命中时明确告知未找到依据',
    typeof nsData?.answer === 'string' && nsData.answer.includes('未找到足够依据'),
    nsData?.answer?.slice(0, 24) + '…')
  check('无命中时返回空来源', (nsData?.sources || []).length === 0)

  // ---------- 6. 重建与删除 ----------
  console.log('\n[6] 重建索引与删除')
  const before = uploaded['考勤管理制度.md'].chunkCount
  const rebuild = await call('POST', `/kb/documents/${mdDoc.id}/rebuild`, admin.token)
  check('重建索引成功且切片数一致',
    rebuild.json?.code === 0 && rebuild.json?.data?.document?.chunkCount === before,
    `before=${before} after=${rebuild.json?.data?.document?.chunkCount}`)

  const doc2 = uploaded['考勤参数速查表.xlsx']
  const del = await call('DELETE', `/kb/documents/${doc2.id}`, admin.token)
  check('删除文档成功', del.json?.code === 0)
  const afterDel = await call('POST', '/kb/ask', admin.token, { question: '重复打卡窗口是几分钟' })
  check('删除后检索不再命中该文档',
    !(afterDel.json?.data?.sources || []).some((s) => s.documentId === doc2.id))

  const delByEmp = await call('DELETE', `/kb/documents/${mdDoc.id}`, emp.token)
  check('普通员工删除被拒（40301）', delByEmp.json?.code === 40301)

  // ---------- 7. 问答日志 ----------
  console.log('\n[7] 问答日志（脱敏）')
  const logs = await call('GET', '/kb/qa-logs?limit=10', admin.token)
  check('管理员可查问答日志', logs.json?.code === 0 && (logs.json?.data?.list || []).length > 0)
  check('日志不含答案正文字段',
    (logs.json?.data?.list || []).every((l) => !('answer' in l) && !('apiKey' in l)))
  const empLogs = await call('GET', '/kb/qa-logs?limit=10', emp.token)
  check('普通员工查日志被拒（40301）', empLogs.json?.code === 40301)

  // ---------- 8. 模块 G：人脸识别适配层 ----------
  console.log('\n[8] 人脸识别适配层（模拟实现）')
  const status = await call('GET', '/face/status', emp.token)
  const st = status.json?.data
  check('状态返回 provider=noop 且 mock=true',
    st?.provider === 'noop' && st?.mock === true, `provider=${st?.provider} mock=${st?.mock}`)
  check('默认不保存原始人脸图片', st?.storeImage === false)
  check('隐私说明非空', !!st?.privacyNote)

  const photo = pngBytes('photo-a')
  const other = pngBytes('photo-b')

  const enroll = await call('POST', '/face/enroll', emp.token, uploadImage(emp.token, photo))
  const en = enroll.json?.data
  check('员工录入本人人脸成功', enroll.json?.code === 0 && en?.employeeId, `employeeId=${en?.employeeId}`)
  check('录入结果标记 mock=true', en?.mock === true)
  check('录入结果不保存原图', en?.storedImage === false)

  const verify = await call('POST', '/face/verify', emp.token, uploadImage(emp.token, photo))
  check('同一张图校验通过', verify.json?.code === 0 && verify.json?.data?.passed === true,
    `similarity=${verify.json?.data?.similarity}`)
  check('校验结果标记 mock=true', verify.json?.data?.mock === true)

  const verify2 = await call('POST', '/face/verify', emp.token, uploadImage(emp.token, other))
  check('换一张图校验不通过', verify2.json?.data?.passed === false,
    `similarity=${verify2.json?.data?.similarity} threshold=${verify2.json?.data?.threshold}`)

  const cross = await call('POST', '/face/enroll', emp.token, uploadImage(emp.token, photo))
  check('重复录入同一人不报错（覆盖更新）', cross.json?.code === 0)

  // emp01 本人的 employeeId 就是 1，这里必须换一个别人的 ID 才构成越权
  const otherEmpFd = uploadImage(emp.token, photo)
  otherEmpFd.append('employeeId', '2')
  const forbidden = await call('POST', '/face/enroll', emp.token, otherEmpFd)
  check('替他人录入人脸被拒（40301）', forbidden.json?.code === 40301, forbidden.json?.message)

  const adminEnrollFd = uploadImage(admin.token, photo)
  adminEnrollFd.append('employeeId', '2')
  const adminEnroll = await call('POST', '/face/enroll', admin.token, adminEnrollFd)
  check('管理员可代他人录入', adminEnroll.json?.code === 0)

  const emptyFd = new FormData()
  emptyFd.append('image', new Blob([Buffer.from('')]), 'empty.png')
  const emptyFace = await call('POST', '/face/verify', emp.token, emptyFd)
  check('空图片被拒（40001）', emptyFace.json?.code === 40001, emptyFace.json?.message)

  const features = await call('GET', '/face/features?pageNum=1&pageSize=10', admin.token)
  check('管理员可查人脸录入情况', features.json?.code === 0 && features.json?.data?.total >= 1,
    `total=${features.json?.data?.total}`)
  const empFeatures = await call('GET', '/face/features?pageNum=1&pageSize=10', emp.token)
  check('普通员工查全员人脸被拒（40301）', empFeatures.json?.code === 40301)

  console.log(`\n=== 结果：${pass} 项通过，${fail} 项失败 ===`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('验证脚本异常：', e)
  process.exit(1)
})
