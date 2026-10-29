#!/usr/bin/env node
/**
 * FC 合并打包脚本：前端产物打进 Spring Boot jar 的 static 目录，产出单个可部署 jar。
 *
 * 用途：阿里云函数计算（FC）单函数部署——一个 jar 同时服务 API 与前端页面，
 *      无跨域、无独立静态托管。也适用于任何"单体 jar 部署"场景（如内网机、
 *      1Panel 等）。
 *
 * 用法：
 *   node scripts/build-fc-bundle.mjs            # 完整流程
 *   node scripts/build-fc-bundle.mjs --skip-web # 跳过前端构建（dist 未变时）
 *
 * 流程：
 *   1. web/ 下 vite build → dist/
 *   2. dist/ 拷贝到 server/lz-app/src/main/resources/static/（先清空旧产物）
 *   3. server/ 下 mvn -DskipTests package → lz-app/target/lz-app.jar
 *
 * 注意：static/ 目录产物仅本地打包用。它不影响 Git（被 .gitignore 挡住），
 *      但会打进 jar——不要把含前端产物的 jar 提交或外发，jar 内含配置密钥。
 */
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const WEB_DIR = path.join(ROOT, 'web')
const DIST_DIR = path.join(WEB_DIR, 'dist')
const STATIC_DIR = path.join(ROOT, 'server/lz-app/src/main/resources/static')

const skipWeb = process.argv.includes('--skip-web')

function run(cmd, cwd) {
  console.log(`\n[build] $ ${cmd}  (cwd=${path.relative(ROOT, cwd) || '.'})`)
  execSync(cmd, { cwd, stdio: 'inherit' })
}

// 1. 前端构建
if (skipWeb) {
  if (!fs.existsSync(DIST_DIR)) {
    console.error('[ERROR] --skip-web 但 web/dist 不存在，请先完整执行一次')
    process.exit(1)
  }
  console.log('[build] 跳过前端构建（--skip-web）')
} else {
  run('npm run build', WEB_DIR)
}
if (!fs.existsSync(path.join(DIST_DIR, 'index.html'))) {
  console.error('[ERROR] web/dist/index.html 不存在，前端构建疑似失败')
  process.exit(1)
}

// 2. 清空并拷贝到 static
console.log(`\n[build] 同步 dist -> resources/static`)
if (fs.existsSync(STATIC_DIR)) {
  fs.rmSync(STATIC_DIR, { recursive: true, force: true })
}
fs.mkdirSync(STATIC_DIR, { recursive: true })

/**
 * 手写递归拷贝：不用 fs.cpSync——部分定制版 Node（如 WorkBuddy 内置 22.x）
 * 上 cpSync 在深层目录下会静默失败（目标目录为空且不报错），基础 API 反而可靠。
 */
function copyRecursive(src, dest) {
  fs.mkdirSync(dest, { recursive: true })
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, entry.name)
    const d = path.join(dest, entry.name)
    if (entry.isDirectory()) copyRecursive(s, d)
    else fs.copyFileSync(s, d)
  }
}
copyRecursive(DIST_DIR, STATIC_DIR)
const copied = fs.readdirSync(STATIC_DIR)
if (!fs.existsSync(path.join(STATIC_DIR, 'index.html'))) {
  console.error('[ERROR] 拷贝后 static/index.html 仍不存在，拷贝失败')
  process.exit(1)
}
console.log(`[build] 已拷贝 ${copied.join(', ')}`)

// 3. 后端打包
run('mvn -DskipTests package', path.join(ROOT, 'server'))

const jar = path.join(ROOT, 'server/lz-app/target/lz-app.jar')
const sizeMb = (fs.statSync(jar).size / 1024 / 1024).toFixed(1)
console.log(`\n[build] 完成: ${jar} (${sizeMb} MB)`)
console.log('[build] 部署到 FC 前请阅读 docs/deploy-aliyun-fc.md（环境变量清单 + 镜像推送步骤）')
