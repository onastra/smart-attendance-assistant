/**
 * 阶段 4 演示数据补录：14 天打卡流水。
 *
 * 背景：阶段 3 只产生了零星几条真实打卡，趋势图只有 1~2 个点看不出东西。
 * 这里补录 2026-10-12 ~ 2026-10-25 共 14 天（10 个工作日 + 4 个休息日）的流水，
 * 让日/周/月趋势、部门对比、场所时段分布都有可看的数据。
 *
 * 三条硬规矩（对应需求 IDX-D8「区分真实统计与示例数据」）：
 *   1. 全部写 source='SAMPLE'，与真实打卡（H5/FACE_MOCK/ADMIN）物理隔离；
 *   2. request_id 统一 SAMPLE- 前缀，可一键清除、不与服务端签发的 CK- 前缀冲突；
 *   3. 只用确定性伪随机（固定种子），每次生成结果一致，便于复现与核对。
 *
 * 运行：
 *   LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe" LZ_MYSQL_PWD=<口令> node scripts/seed-sample-attendance.mjs
 * 加 --dry-run 只生成 SQL 不执行。
 */
import { execSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const START_DATE = '2026-10-12'
const DAYS = 14

/** 员工 → 常用场所（取自 hr_employee_place 的有效绑定） */
const EMPLOYEES = [
  { id: 1, name: '张明', placeId: 1 },
  { id: 2, name: '李慧', placeId: 1 },
  { id: 3, name: '王强', placeId: 2 },
  { id: 4, name: '赵敏', placeId: 3 },
  { id: 5, name: '陈静', placeId: 1 }
]

/** 场所基准坐标（与 biz_place 一致，补录记录落在围栏内） */
const PLACE_COORD = {
  1: { lng: 116.40740, lat: 39.90420 },
  2: { lng: 116.41800, lat: 39.91500 },
  3: { lng: 121.47370, lat: 31.23040 }
}

const WORK_START = '09:00'
const WORK_END = '18:00'
/** 迟到宽限：与后端 AttendanceProperties 默认一致 */
const LATE_GRACE_MINUTES = 15

/**
 * 确定性伪随机（线性同余）。
 * 用 Math.random 的话每次补录数据都不一样，出问题没法复盘，也不好写断言。
 */
function createRandom(seed) {
  let state = seed >>> 0
  return function next() {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 0xffffffff
  }
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function toDateStr(date) {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

function addDays(dateStr, delta) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const date = new Date(Date.UTC(y, m - 1, d))
  date.setUTCDate(date.getUTCDate() + delta)
  return date
}

/** 0=周日 … 6=周六 */
function dayOfWeek(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay()
}

function isWeekend(dateStr) {
  const w = dayOfWeek(dateStr)
  return w === 0 || w === 6
}

/** 分钟数 → HH:mm */
function minutesToTime(minutes) {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`
}

function toMinutes(hhmm) {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

function escapeSql(value) {
  return String(value).replace(/'/g, "''")
}

function main() {
  const random = createRandom(20261026)
  const rows = []
  const summary = { workdays: 0, weekends: 0, records: 0, late: 0, early: 0, absent: 0, missingOut: 0 }

  for (let i = 0; i < DAYS; i++) {
    const date = addDays(START_DATE, i)
    const dateStr = toDateStr(date)

    if (isWeekend(dateStr)) {
      // 休息日不产生打卡流水，由日汇总任务统一判为 REST
      summary.weekends++
      continue
    }
    summary.workdays++

    for (const emp of EMPLOYEES) {
      const roll = random()

      // 全天缺勤：约 5%，让"缺勤"这一统计项不是永远 0
      if (roll < 0.05) {
        summary.absent++
        continue
      }

      // 上班打卡：08:32 ~ 09:47 之间波动，超过 09:15 判定迟到
      const onOffset = Math.floor(random() * 75) - 28 // 基准 09:00 前后
      const onMinutes = toMinutes(WORK_START) + onOffset
      const onLate = onOffset > LATE_GRACE_MINUTES
      if (onLate) {
        summary.late++
      }

      const coord = PLACE_COORD[emp.placeId] || PLACE_COORD[1]
      rows.push({
        employeeId: emp.id,
        placeId: emp.placeId,
        checkinType: 'ON_DUTY',
        checkinTime: `${dateStr} ${minutesToTime(onMinutes)}:00`,
        result: onLate ? 'LATE' : 'NORMAL',
        lng: (coord.lng + (random() - 0.5) * 0.0008).toFixed(7),
        lat: (coord.lat + (random() - 0.5) * 0.0008).toFixed(7),
        requestId: `SAMPLE-${dateStr.replace(/-/g, '')}-${emp.id}-ON`
      })

      // 忘记打下班卡：约 8%，用来验证"有上班无下班"时工时为 0 而不是负数
      if (random() < 0.08) {
        summary.missingOut++
        continue
      }

      // 下班打卡：17:40 ~ 19:30，早于 18:00 判定早退
      const offMinutes = toMinutes(WORK_END) + Math.floor(random() * 90) - 20
      const offEarly = offMinutes < toMinutes(WORK_END)
      if (offEarly) {
        summary.early++
      }

      rows.push({
        employeeId: emp.id,
        placeId: emp.placeId,
        checkinType: 'OFF_DUTY',
        checkinTime: `${dateStr} ${minutesToTime(offMinutes)}:00`,
        result: offEarly ? 'EARLY' : 'NORMAL',
        lng: (coord.lng + (random() - 0.5) * 0.0008).toFixed(7),
        lat: (coord.lat + (random() - 0.5) * 0.0008).toFixed(7),
        requestId: `SAMPLE-${dateStr.replace(/-/g, '')}-${emp.id}-OFF`
      })
    }
  }

  summary.records = rows.length

  const values = rows
    .map((r) => {
      return (
        `('${r.employeeId}', '${r.placeId}', NULL, '${r.checkinType}', '${r.checkinTime}', ` +
        `'SAMPLE', '${r.result}', '${r.lng}', '${r.lat}', '127.0.0.1', ` +
        `'${r.requestId}', 'SAMPLE 演示补录数据（14 天趋势演示，非真实打卡）')`
      )
    })
    .join(',\n    ')

  const sql = `-- 阶段 4 演示数据：14 天打卡流水（由 scripts/seed-sample-attendance.mjs 生成，勿手工编辑）
-- 覆盖范围：${START_DATE} ~ ${toDateStr(addDays(START_DATE, DAYS - 1))}（${summary.workdays} 个工作日 + ${summary.weekends} 个休息日）
-- 记录数：${summary.records}（迟到 ${summary.late} / 早退 ${summary.early} / 全天缺勤 ${summary.absent} / 漏打下班卡 ${summary.missingOut}）
-- 全部标记 source='SAMPLE'，清除命令：DELETE FROM att_record WHERE request_id LIKE 'SAMPLE-%';

DELETE FROM att_record WHERE request_id LIKE 'SAMPLE-%';

INSERT INTO att_record
    (employee_id, place_id, qrcode_id, checkin_type, checkin_time, source, result, lng, lat, client_ip, request_id, remark)
VALUES
    ${values};
`

  // 用 fileURLToPath 而不是 URL.pathname：后者会保留 %20 之类的转义，
  // 项目目录名带空格（Project Document）时 mysql 客户端会报"系统找不到指定的路径"
  const outPath = fileURLToPath(new URL('../docs/sql/sample-attendance-14d.sql', import.meta.url))
  writeFileSync(outPath, sql, 'utf8')
  console.log(`已生成 SQL：docs/sql/sample-attendance-14d.sql（${summary.records} 条记录）`)
  console.log(
    `  工作日 ${summary.workdays} 天 / 休息日 ${summary.weekends} 天；迟到 ${summary.late} 次、早退 ${summary.early} 次、` +
      `全天缺勤 ${summary.absent} 人天、漏打下班卡 ${summary.missingOut} 次`
  )

  if (process.argv.includes('--dry-run')) {
    console.log('  --dry-run：未写入数据库')
    return
  }

  const password = process.env.LZ_MYSQL_PWD
  if (!password) {
    console.log('  [跳过执行] 未设置 LZ_MYSQL_PWD，SQL 已落盘，可手工执行或用环境变量重跑')
    return
  }

  const bin = process.env.LZ_MYSQL_BIN || 'mysql'
  const user = process.env.LZ_MYSQL_USER || 'root'
  const database = process.env.LZ_MYSQL_DB || 'lianzhi'
  execSync(`"${bin}" -u${user} -p${password} ${database} < "${outPath}"`, {
    stdio: 'inherit',
    cwd: path.dirname(outPath)
  })

  const count = execSync(
    `"${bin}" -u${user} -p${password} ${database} -N -e "SELECT COUNT(*) FROM att_record WHERE source='SAMPLE';"`
  )
    .toString()
    .trim()
  console.log(`  已写入数据库，当前 SAMPLE 记录数：${count}`)
}

main()
