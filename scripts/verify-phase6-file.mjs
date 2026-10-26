/**
 * 阶段 6 剩余项验证：sys_file 受控存储 + 鉴权下载。
 *
 * 只验"安全边界"，不验"能不能存下文件"——后者几百行代码都能做到，
 * 真正会出事的是"谁都能下别人的文件"和"路径能穿越到存储目录之外"。
 *
 * 运行：node scripts/verify-phase6-file.mjs
 * 前提：后端已启动（默认 http://127.0.0.1:8080）
 */
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ACCOUNTS = {
  admin: { username: 'admin', password: 'Admin@123' },
  manager: { username: 'manager01', password: 'Admin@123' },
  empA: { username: 'emp01', password: 'Emp@123' },
  empB: { username: 'emp02', password: 'Emp@123' }
}

let passed = 0
let failed = 0
const failures = []

function check(name, ok, detail = '') {
  if (ok) {
    passed++
    console.log(`  PASS  ${name}${detail ? ' — ' + detail : ''}`)
  } else {
    failed++
    failures.push(name + (detail ? ' — ' + detail : ''))
    console.log(`  FAIL  ${name}${detail ? ' — ' + detail : ''}`)
  }
}

async function call(method, urlPath, { token, body, raw = false, form } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body && !form) headers['Content-Type'] = 'application/json'
  const res = await fetch(BASE + urlPath, {
    method,
    headers,
    body: form || (body ? JSON.stringify(body) : undefined)
  })
  if (raw) return res
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* 非 JSON 响应 */ }
  return { status: res.status, json, text, headers: res.headers }
}

async function login(account) {
  const r = await call('POST', '/api/auth/login', { body: ACCOUNTS[account] })
  if (!r.json || r.json.code !== 0) {
    throw new Error(`登录失败 ${account}: ${JSON.stringify(r.json || r.text)}`)
  }
  return r.json.data.token
}

function uploadForm(filePath, visibility, ownerDeptId = null) {
  const form = new FormData()
  const buf = fs.readFileSync(filePath)
  form.append('file', new Blob([buf]), path.basename(filePath))
  form.append('visibility', visibility)
  if (ownerDeptId) form.append('ownerDeptId', String(ownerDeptId))
  return form
}

async function main() {
  console.log('=== 阶段 6：文件受控存储与鉴权下载验证 ===\n')
  const token = {}
  for (const key of Object.keys(ACCOUNTS)) {
    token[key] = await login(key)
  }
  console.log('登录：admin / manager01 / emp01 / emp02 全部成功\n')

  // 准备两个测试文件
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'lz-file-'))
  const pub = path.join(tmp, '公开说明.txt')
  const priv = path.join(tmp, '私密名单.txt')
  fs.writeFileSync(pub, '这是公开文件内容，任何登录用户都能下载。')
  fs.writeFileSync(priv, '这是私密文件内容，只有上传人本人能下载。')

  // ---------- 1. 上传 ----------
  console.log('[1] 上传与记录')
  const upPub = await call('POST', '/api/files', { token: token.admin, form: uploadForm(pub, 'PUBLIC') })
  check('管理员上传 PUBLIC 文件成功', upPub.json?.code === 0, JSON.stringify(upPub.json?.data?.file || {}).slice(0, 120))
  const pubId = upPub.json?.data?.file?.id

  const upPriv = await call('POST', '/api/files', { token: token.empA, form: uploadForm(priv, 'PRIVATE') })
  check('员工上传 PRIVATE 文件成功', upPriv.json?.code === 0)
  const privId = upPriv.json?.data?.file?.id

  const upDeptNoDept = await call('POST', '/api/files', { token: token.admin, form: uploadForm(pub, 'DEPT') })
  check('DEPT 但不带部门 → 拒绝（不静默降级成公开）',
    upDeptNoDept.json?.code === 40001, `code=${upDeptNoDept.json?.code}`)

  const badVis = await call('POST', '/api/files', { token: token.admin, form: uploadForm(pub, 'EVERYONE') })
  check('非法可见范围 → 拒绝', badVis.json?.code === 40001, `code=${badVis.json?.code}`)

  // ---------- 2. 路径不外泄 ----------
  console.log('\n[2] 存储路径不外泄')
  const listRes = await call('GET', '/api/files', { token: token.admin })
  const fileList = listRes.json?.data?.files || []
  check('列表返回了文件', fileList.length > 0, `共 ${fileList.length} 个`)
  check('列表不含 storagePath（真实路径不下发）',
    fileList.every(f => f.storagePath === null || f.storagePath === undefined),
    'storagePath 均为空')

  // ---------- 3. 鉴权下载 ----------
  console.log('\n[3] 鉴权下载')
  const dlPubEmp = await call('GET', `/api/files/${pubId}/download`, { token: token.empB, raw: true })
  check('PUBLIC 文件：其他员工可下载（200）', dlPubEmp.status === 200, `HTTP ${dlPubEmp.status}`)
  if (dlPubEmp.status === 200) {
    const text = await dlPubEmp.text()
    check('下载内容与上传一致', text.includes('公开文件内容'), text.slice(0, 30))
    const cd = dlPubEmp.headers.get('content-disposition') || ''
    check('响应带 Content-Disposition 附件头', cd.includes('attachment'), cd.slice(0, 80))
  }

  const dlPrivOwner = await call('GET', `/api/files/${privId}/download`, { token: token.empA, raw: true })
  check('PRIVATE 文件：本人可下载（200）', dlPrivOwner.status === 200, `HTTP ${dlPrivOwner.status}`)

  const dlPrivOther = await call('GET', `/api/files/${privId}/download`, { token: token.empB })
  check('PRIVATE 文件：其他员工被拒（403）', dlPrivOther.json?.code === 40301, `code=${dlPrivOther.json?.code}`)

  const dlPrivManager = await call('GET', `/api/files/${privId}/download`, { token: token.manager })
  check('PRIVATE 文件：部门主管无 file:admin 也被拒（403）',
    dlPrivManager.json?.code === 40301, `code=${dlPrivManager.json?.code}`)

  const dlPrivAdmin = await call('GET', `/api/files/${privId}/download`, { token: token.admin, raw: true })
  check('PRIVATE 文件：管理员可下载（200）', dlPrivAdmin.status === 200, `HTTP ${dlPrivAdmin.status}`)

  const dlNoAuth = await call('GET', `/api/files/${pubId}/download`, {})
  check('未登录下载 → 401', dlNoAuth.status === 401 || dlNoAuth.json?.code === 40101,
    `HTTP ${dlNoAuth.status} code=${dlNoAuth.json?.code}`)

  const dlMissing = await call('GET', '/api/files/99999999/download', { token: token.admin })
  check('下载不存在的文件 → 404', dlMissing.json?.code === 40401, `code=${dlMissing.json?.code}`)

  // ---------- 4. 越权删除 ----------
  console.log('\n[4] 删除权限')
  const delOther = await call('DELETE', `/api/files/${privId}`, { token: token.empB })
  check('删除他人文件被拒（403）', delOther.json?.code === 40301, `code=${delOther.json?.code}`)

  // ---------- 5. 知识库原文下载（真实用例） ----------
  console.log('\n[5] 知识库文档原文下载')
  const docs = await call('GET', '/api/kb/documents?pageNum=1&pageSize=5', { token: token.admin })
  const docList = docs.json?.data?.list || []
  check('知识库有文档', docList.length > 0, `共 ${docList.length} 篇`)
  const withFile = docList.find(d => d.fileId)
  if (withFile) {
    const dlDoc = await call('GET', `/api/files/${withFile.fileId}/download`, { token: token.empA, raw: true })
    check('普通员工可下载 PUBLIC 文档原文（200）', dlDoc.status === 200,
      `文档《${withFile.title}》 HTTP ${dlDoc.status}`)
  } else {
    check('知识库文档带有 fileId（可下载原文）', false, '没有文档关联文件')
  }

  // ---------- 6. 清理 ----------
  console.log('\n[6] 清理测试数据')
  const delPub = await call('DELETE', `/api/files/${pubId}`, { token: token.admin })
  check('管理员可删除自己上传的文件', delPub.json?.code === 0, `code=${delPub.json?.code}`)
  const delPriv = await call('DELETE', `/api/files/${privId}`, { token: token.empA })
  check('员工可删除自己上传的文件', delPriv.json?.code === 0, `code=${delPriv.json?.code}`)

  console.log(`\n=== 结果：${passed} 通过 / ${failed} 失败 ===`)
  if (failed > 0) {
    console.log('失败项：')
    failures.forEach(f => console.log('  - ' + f))
    process.exit(1)
  }
}

main().catch(e => {
  console.error('验证脚本异常：', e.message)
  process.exit(1)
})
