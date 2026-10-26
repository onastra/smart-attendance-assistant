#!/usr/bin/env node
/**
 * 阶段 7：全量回归入口。
 *
 * <p>为什么要有一个聚合入口：四套脚本分散执行时，人很容易只跑改动的那一套，
 * 结果上层链路被底层改动带崩却没人发现。这里把它们串成一次执行，
 * 最后按"套"给出通过/失败汇总，任一失败即以非 0 退出，便于 CI 判定与人工核对。</p>
 *
 * 用法：
 *   LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe" LZ_MYSQL_PWD=<口令> node scripts/verify-all.mjs
 *   node scripts/verify-all.mjs --api-only       # 跳过 Maven 单元测试，只跑接口回归
 *
 * 说明：
 *   1) 不提供 LZ_MYSQL_PWD 时脚本仍可运行，但各分脚本清理不了上一轮数据，
 *      可能因 5 分钟重复打卡窗口 / 上传去重而出现假失败，请以带口令的执行结果为准；
 *   2) 必须先启动后端（默认 http://localhost:8080），否则直接退出并给出提示。
 */
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..')
const API = process.env.LZ_API_BASE || 'http://localhost:8080/api'
const API_ONLY = process.argv.includes('--api-only')

/** 接口回归套件，顺序即依赖顺序：先有打卡流水，才有统计，再是知识库与文件 */
const SUITES = [
    { file: 'verify-phase3.mjs', name: '阶段 3 二维码打卡闭环' },
    { file: 'verify-phase4.mjs', name: '阶段 4 统计与看板' },
    { file: 'verify-phase5.mjs', name: '阶段 5 知识库与人脸识别' },
    { file: 'verify-phase6-file.mjs', name: '阶段 6 文件受控存储与鉴权' },
]

const c = {
    reset: '\x1b[0m', dim: '\x1b[2m', bold: '\x1b[1m',
    green: '\x1b[32m', red: '\x1b[31m', yellow: '\x1b[33m', cyan: '\x1b[36m',
}

function line(title) {
    console.log(`\n${c.cyan}${'='.repeat(64)}${c.reset}`)
    console.log(`${c.bold}${title}${c.reset}`)
    console.log(`${c.cyan}${'='.repeat(64)}${c.reset}`)
}

/**
 * 从分脚本输出里提取最后的「N 通过 / M 失败」。
 *
 * 四套脚本的汇总行写法各不相同（"通过 30 项，失败 0 项" / "46 项通过，0 项失败" /
 * "20 通过 / 0 失败"），但都满足"最后一行含『失败』且里面有两个数字，
 * 前一个是通过、后一个是失败"，因此用一条规则通吃，避免逐个适配后失同步。
 */
function parseSummary(stdout) {
    const summaryLine = stdout.split('\n')
        .map((l) => l.trim())
        .filter((l) => l.includes('失败'))
        .pop()
    if (!summaryLine) {
        return null
    }
    const numbers = summaryLine.match(/\d+/g)
    if (!numbers || numbers.length < 2) {
        return null
    }
    return { passed: Number(numbers[0]), failed: Number(numbers[numbers.length - 1]) }
}

function runSuite(suite) {
    const started = Date.now()
    const result = spawnSync(process.execPath, [path.join(HERE, suite.file)], {
        cwd: ROOT,
        encoding: 'utf-8',
        env: process.env,
    })
    const stdout = result.stdout || ''
    const stderr = result.stderr || ''
    const output = stdout + stderr

    if (result.error) {
        console.error(`${c.red}无法启动 ${suite.file}：${result.error.message}${c.reset}`)
        return { ...suite, passed: 0, failed: 1, skipped: false, error: true, millis: 0, output }
    }
    const summary = parseSummary(stdout)
    const millis = Date.now() - started
    if (!summary) {
        // 汇总行都没打出来，多半是直接崩了：原样输出尾部便于定位
        console.error(output.split('\n').slice(-25).join('\n'))
        return { ...suite, passed: 0, failed: 1, skipped: false, error: true, millis, output }
    }
    return { ...suite, ...summary, skipped: false, error: false, millis, output }
}

function runUnitTests() {
    line('Maven 单元测试（mvn -B test）')
    const started = Date.now()
    const result = spawnSync('mvn', ['-B', 'test'], {
        cwd: path.join(ROOT, 'server'),
        encoding: 'utf-8',
        shell: true,
        env: process.env,
    })
    const output = (result.stdout || '') + (result.stderr || '')
    const millis = Date.now() - started

    if (result.error) {
        console.log(`${c.yellow}跳过：本机未找到 mvn（${result.error.message}）${c.reset}`)
        return { name: 'Maven 单元测试', passed: 0, failed: 0, skipped: true, millis, output: '' }
    }
    /*
     * 只取 Results 区块的汇总行（形如 "Tests run: 33, Failures: 0, Errors: 0, Skipped: 0"），
     * 排除带 "Time elapsed" 的逐类行——两类数字含义不同，混着加会得出错误总数；
     * 也不能只取最后一行，那是最后一个模块的，会把总数少报成一个模块的值。
     */
    const summaryLines = output.split('\n')
        .filter((l) => l.includes('Tests run:') && !l.includes('Time elapsed'))
    let passed = 0
    let failures = 0
    for (const l of summaryLines) {
        const m = l.match(/Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)/)
        if (!m) {
            continue
        }
        const [, total, f, e, skipped] = m.map(Number)
        passed += total - f - e - skipped
        failures += f + e
    }

    return {
        name: 'Maven 单元测试',
        passed,
        failed: failures,
        skipped: false,
        millis,
        output,
    }
}

async function checkBackend() {
    try {
        const response = await fetch(`${API}/system/health`, { signal: AbortSignal.timeout(5000) })
        if (!response.ok) {
            return `健康检查返回 HTTP ${response.status}`
        }
        const body = await response.json()
        // 健康检查返回的是扁平对象（不是 R<T> 包装），这里兼容两种结构以免后续改动再次踩空
        const root = body?.data ?? body
        if (!root?.database?.connected) {
            return '后端已启动但数据库未连通'
        }
        return null
    } catch (e) {
        return `无法连接后端 ${API}（${e.message}）。请先启动：java -jar server/lz-app/target/lz-app.jar --server.port=8080 --spring.profiles.active=local`
    }
}

async function main() {
    console.log(`${c.bold}联智考勤 —— 阶段 7 全量回归${c.reset}`)
    console.log(`${c.dim}接口基址：${API}${c.reset}`)
    if (!process.env.LZ_MYSQL_PWD) {
        console.log(`${c.yellow}提示：未设置 LZ_MYSQL_PWD，各脚本不会清理上一轮数据，个别用例可能因去重窗口而假失败。${c.reset}`)
    }

    const backendError = await checkBackend()
    if (backendError) {
        console.error(`\n${c.red}前置检查失败：${backendError}${c.reset}`)
        process.exit(1)
    }

    const results = []

    if (!API_ONLY) {
        results.push(runUnitTests())
    } else {
        console.log(`${c.dim}按 --api-only 跳过 Maven 单元测试${c.reset}`)
    }

    for (const suite of SUITES) {
        line(suite.name)
        const outcome = runSuite(suite)
        results.push(outcome)
        if (outcome.error) {
            console.log(`${c.red}✗ ${suite.name}：脚本未能完成${c.reset}`)
        } else {
            console.log(`${c.green}✓${c.reset} ${suite.name}：通过 ${outcome.passed} / 失败 ${outcome.failed}`)
        }
    }

    line('回归汇总')
    console.log('套件                              通过    失败    耗时')
    console.log('-'.repeat(64))
    let totalPassed = 0
    let totalFailed = 0
    for (const r of results) {
        const minutes = (r.millis / 1000).toFixed(1) + 's'
        const name = (r.name || '').padEnd(24, ' ')
        if (r.skipped) {
            console.log(`${c.yellow}${name}      --      --    ${minutes}（跳过）${c.reset}`)
            continue
        }
        totalPassed += r.passed
        totalFailed += r.failed
        const mark = r.failed === 0 ? c.green : c.red
        console.log(`${mark}${name}${String(r.passed).padStart(8)}${String(r.failed).padStart(8)}    ${minutes}${c.reset}`)
    }
    console.log('-'.repeat(64))
    console.log(`${c.bold}合计：通过 ${totalPassed} 项，失败 ${totalFailed} 项${c.reset}`)

    if (totalFailed > 0) {
        console.log(`\n${c.red}回归未通过。失败套件的完整输出：${c.reset}`)
        for (const r of results.filter((x) => x.failed > 0 && !x.skipped)) {
            console.log(`\n----- ${r.name} -----`)
            console.log(r.output.split('\n').slice(-30).join('\n'))
        }
        process.exit(1)
    }
    console.log(`\n${c.green}全部通过 ✅${c.reset}`)
}

main().catch((e) => {
    console.error(e)
    process.exit(1)
})
