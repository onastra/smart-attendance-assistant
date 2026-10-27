#!/usr/bin/env node
/**
 * 阶段 F 端到端验证：地图选址（高德 JS API 接入，功能编号 REQ-F-01）。
 *
 * 覆盖：
 *   REQ-F-01 地图选址链路
 *     a. 坐标转换纯函数正确性（WGS84↔GCJ-02，围栏校验一致性的数学基础）
 *     b. map-status 接口诚实上报（未配置 Key 时 enabled=false 且不下发 apiKey；
 *        配置后 enabled=true 并下发 Key/安全密钥）
 *     c. 前端源码零硬编码 Key（公开仓库防泄漏红线）
 *     d. 配置模板完整（照模板重建 local.yml 不会丢地图段——Redis 段丢失教训的防复发）
 *
 * 环境自适应（与 verify-phase5.mjs 同一原则）：
 *   - 坐标转换断言与静态断言不依赖任何外部服务，永远执行；
 *   - 接口组需要后端：连不上则如实跳过并标注，不算失败也不算通过；
 *   - map-status 断言按"当前是否配置 Key"分叉，两种形态都必须诚实。
 *
 * 运行：
 *   node scripts/verify-phaseF.mjs                       （后端未启动时仅跑本地断言）
 *   LZ_API_BASE=http://localhost:8080/api node scripts/verify-phaseF.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { fileURLToPath } from 'node:url'

const BASE = process.env.LZ_API_BASE || 'http://localhost:8080/api'
const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..')

let pass = 0
let fail = 0
let skipped = 0

function check(name, ok, detail) {
  if (ok) {
    pass++
    console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`)
  } else {
    fail++
    console.log(`  ✗ ${name}${detail ? ' — ' + detail : ''}`)
  }
}

function skip(name, reason) {
  skipped++
  console.log(`  - ${name}（跳过：${reason}）`)
}

async function call(method, apiPath, token, body) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(BASE + apiPath, { method, headers, body: payload })
  let json = null
  try {
    json = await res.json()
  } catch {
    /* 非 JSON */
  }
  return { status: res.status, json }
}

function readIfExists(p) {
  try {
    return fs.readFileSync(p, 'utf-8')
  } catch {
    return null
  }
}

async function main() {
  console.log(`=== 阶段 F：地图选址（高德 JS API）验证 ===\nBASE=${BASE}\n`)

  // ---------- a. 坐标转换纯函数（不依赖后端，永远执行） ----------
  console.log('[a] 坐标转换纯函数（WGS84↔GCJ-02）')
  const transformUrl = pathToFileURL(path.join(ROOT, 'web/src/utils/coordTransform.js')).href
  const ct = await import(transformUrl)

  // 已知参考值：天安门 WGS84 (116.397428, 39.90923) 的 GCJ-02 偏移约 +0.0062/+0.0014 度
  const tianGcj = ct.wgs84ToGcj02(116.397428, 39.90923)
  check('天安门 WGS84→GCJ-02 偏移方向正确（东移约 500 米量级）',
    tianGcj.lng > 116.397428 && tianGcj.lng - 116.397428 < 0.02
      && Math.abs(tianGcj.lat - 39.90923) < 0.02,
    `gcj=(${tianGcj.lng.toFixed(7)}, ${tianGcj.lat.toFixed(7)})`)

  const round = ct.gcj02ToWgs84(tianGcj.lng, tianGcj.lat)
  const roundErrLng = Math.abs(round.lng - 116.397428)
  const roundErrLat = Math.abs(round.lat - 39.90923)
  check('GCJ-02→WGS84 往返误差 < 1e-5 度（约 1 米，远小于 10 米最小打卡半径）',
    roundErrLng < 1e-5 && roundErrLat < 1e-5,
    `errLng=${roundErrLng.toExponential(2)} errLat=${roundErrLat.toExponential(2)}`)

  // 第二组样本：深圳（不同纬度验证公式普适性）
  const szGcj = ct.wgs84ToGcj02(114.057868, 22.543099)
  const szRound = ct.gcj02ToWgs84(szGcj.lng, szGcj.lat)
  check('深圳坐标往返误差 < 1e-5 度（纬度普适性）',
    Math.abs(szRound.lng - 114.057868) < 1e-5 && Math.abs(szRound.lat - 22.543099) < 1e-5,
    `errLng=${Math.abs(szRound.lng - 114.057868).toExponential(2)}`)

  const overseas = ct.wgs84ToGcj02(-122.4194, 37.7749)
  check('境外坐标不做偏移（GCJ-02 与 WGS84 相同）',
    overseas.lng === -122.4194 && overseas.lat === 37.7749)

  // ---------- b. map-status 接口（环境自适应） ----------
  console.log('\n[b] map-status 诚实上报（需后端）')
  let backendUp = false
  try {
    const probe = await fetch(`${BASE}/system/health`, { signal: AbortSignal.timeout(5000) })
    backendUp = probe.ok
  } catch {
    backendUp = false
  }

  let statusData = null
  if (!backendUp) {
    skip('map-status 全部接口断言', '后端未启动，接口组不判定')
  } else {
    const loginRes = await call('POST', '/auth/login', null, { username: 'admin', password: 'Admin@123' })
    const token = loginRes.json?.data?.token
    check('admin 可登录', !!token)

    const anon = await call('GET', '/places/map-status', null)
    check('未登录访问 map-status 被拒（code != 0）', anon.json?.code !== 0, `code=${anon.json?.code}`)

    const res = await call('GET', '/places/map-status', token)
    statusData = res.json?.data
    const enabled = statusData?.enabled === true
    if (enabled) {
      check('已配置 Key：provider 上报为具体厂商', !!statusData?.provider && statusData.provider !== 'none',
        `provider=${statusData?.provider}`)
      check('已配置 Key：coordinateSource=map-picker', statusData?.coordinateSource === 'map-picker')
      check('已配置 Key：下发 apiKey（前端动态加载 SDK 的唯一来源）', typeof statusData?.apiKey === 'string'
        && statusData.apiKey.length > 0)
      check('已配置 Key：note 明确提示已接入', String(statusData?.note || '').includes('已接入'),
        `note=${statusData?.note}`)
    } else {
      check('未配置 Key：enabled=false（诚实上报，不假装能自动定位）', statusData?.enabled === false)
      check('未配置 Key：coordinateSource=manual-input', statusData?.coordinateSource === 'manual-input')
      check('未配置 Key：不下发 apiKey（杜绝凭空下发空 Key）', statusData?.apiKey === undefined,
        `keys=${Object.keys(statusData || {}).join(',')}`)
      check('未配置 Key：note 如实说明手动录入', String(statusData?.note || '').includes('手动录入'),
        `note=${statusData?.note}`)
    }
  }

  // ---------- c. 前端源码零硬编码 Key（公开仓库红线） ----------
  console.log('\n[c] 前端源码零硬编码 Key')
  const webSrcFiles = []
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules') walk(full)
      } else if (/\.(js|vue|ts|html)$/.test(entry.name)) {
        webSrcFiles.push(full)
      }
    }
  }
  walk(path.join(ROOT, 'web/src'))
  const sourceTexts = webSrcFiles.map((f) => fs.readFileSync(f, 'utf-8'))
  const indexHtml = readIfExists(path.join(ROOT, 'web/index.html'))
  if (indexHtml) sourceTexts.push(indexHtml)
  const allSource = sourceTexts.join('\n')

  // 高德 Web端 JS Key 形如 32 位 hex；硬编码即泄漏
  const hardcodedKey = allSource.match(/['"`][0-9a-f]{32}['"`]/)
  check('web 源码中不存在 32 位 hex 字面量（疑似硬编码 Key）', !hardcodedKey,
    hardcodedKey ? String(hardcodedKey[0]).slice(0, 12) + '...' : '')

  // 安全密钥只允许来自接口下发，不允许本地赋值常量
  const hardcodedSecret = allSource.match(/securityJsCode\s*[:=]\s*['"][0-9a-zA-Z]{16,}['"]/)
  check('安全密钥无硬编码赋值（只允许 map-status 接口下发）', !hardcodedSecret)

  const placeVue = readIfExists(path.join(ROOT, 'web/src/views/place/PlaceManage.vue')) || ''
  check('PlaceManage 通过 loadAMap 动态加载（Key 来自 mapStatus，非内联 script src）',
    placeVue.includes("loadAMap({") && placeVue.includes('mapStatus.value?.apiKey'))
  check('PlaceManage 选点确认前做了 GCJ-02→WGS84 转换（围栏校验一致性）',
    placeVue.includes('gcj02ToWgs84') && placeVue.includes('wgs84ToGcj02'))
  check('地图选址入口受 mapStatus.enabled 控制（未配置时不出现假按钮）',
    placeVue.includes("v-if=\"mapStatus?.enabled\""))

  const amapLoader = readIfExists(path.join(ROOT, 'web/src/utils/amap.js')) || ''
  check('AMap 加载器存在且 Key 走参数注入', amapLoader.includes('export function loadAMap')
    && amapLoader.includes('${encodeURIComponent(apiKey)}'))

  // ---------- d. 配置模板完整性 ----------
  console.log('\n[d] 配置模板完整性（防"照模板重建丢配置"复发）')
  const example = readIfExists(path.join(ROOT, 'server/lz-app/src/main/resources/application-local.yml.example')) || ''
  check('example 模板含 lz.map 段（provider/api-key/security-code 三项齐全）',
    example.includes('map:') && example.includes('provider:') && example.includes('api-key:')
      && example.includes('security-code:'))
  check('example 模板提醒 Key 不得提交入库（gitignore 教训防复发）', example.includes('gitignore'))

  const appYml = readIfExists(path.join(ROOT, 'server/lz-app/src/main/resources/application.yml')) || ''
  check('application.yml 含 security-code 骨架（环境变量 LZ_MAP_SECURITY_CODE）',
    appYml.includes('LZ_MAP_SECURITY_CODE'))

  check('application-local.yml 被 gitignore（真实 Key 永不入库）',
    readIfExists(path.join(ROOT, 'server/lz-app/src/main/resources/application-local.yml')) !== null
      && await (() => {
        // 用 git 自身判定最可靠：check-ignore 命中返回 0，未命中返回非 0
        try {
          execSync('git check-ignore -q server/lz-app/src/main/resources/application-local.yml', { cwd: ROOT })
          return true
        } catch {
          return false
        }
      })())

  // 注意汇总行的数字顺序：verify-all.mjs 的 parseSummary 以"最后一个数字"为失败数，
  // 因此"跳过"数必须放在"失败"之前，否则跳过会被误计入失败
  console.log(`\n=== 结果：${pass} 项通过，${skipped} 项跳过，${fail} 项失败 ===`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('验证脚本异常：', e)
  process.exit(1)
})
