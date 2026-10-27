// 展示层格式化与文案映射（WXML 不支持函数调用，一律在 JS 里先算好）

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// "2026-10-27T09:00:00" → "10-27 09:00"
function shortTime(iso) {
  if (!iso) return ''
  const s = String(iso).replace('T', ' ')
  const parts = s.split(' ')
  const date = parts[0] || ''
  const time = (parts[1] || '').slice(0, 5)
  const d = date.split('-')
  if (d.length < 3) return s
  return d[1] + '-' + d[2] + ' ' + time
}

// "2026-10-27T09:00:00" → "2026-10-27 09:00"
function fullTime(iso) {
  if (!iso) return ''
  return String(iso).replace('T', ' ').slice(0, 16)
}

// 考勤结果 → 徽标样式类
const RESULT_META = {
  NORMAL: { text: '正常', cls: 'tag-green' },
  LATE: { text: '迟到', cls: 'tag-orange' },
  EARLY: { text: '早退', cls: 'tag-orange' },
  ABSENT: { text: '缺勤', cls: 'tag-red' },
  REST: { text: '休息', cls: 'tag-gray' }
}

function resultMeta(result, resultText) {
  const meta = RESULT_META[result]
  return {
    text: (meta && meta.text) || resultText || result || '-',
    cls: (meta && meta.cls) || 'tag-gray'
  }
}

// 申请状态 → 徽标样式类
const APP_STATUS_META = {
  SUBMITTED: { text: '待审批', cls: 'tag-orange' },
  APPROVED: { text: '已通过', cls: 'tag-green' },
  REJECTED: { text: '已驳回', cls: 'tag-red' },
  CANCELLED: { text: '已撤回', cls: 'tag-gray' }
}

function appStatusMeta(status) {
  const meta = APP_STATUS_META[status]
  return {
    text: (meta && meta.text) || status || '-',
    cls: (meta && meta.cls) || 'tag-gray'
  }
}

function appTypeText(type) {
  return type === 'LEAVE' ? '请假' : (type === 'OVERTIME' ? '加班' : (type || '-'))
}

// 打卡来源：SAMPLE 表示演示数据，界面上必须如实标注
function sourceText(source) {
  if (source === 'SAMPLE') return '演示数据'
  if (source === 'H5') return 'H5'
  if (source === 'MINI') return '小程序'
  if (source === 'FACE_MOCK') return '人脸(模拟)'
  if (source === 'ADMIN') return '管理员登记'
  return source || '-'
}

module.exports = {
  pad2: pad2,
  shortTime: shortTime,
  fullTime: fullTime,
  resultMeta: resultMeta,
  appStatusMeta: appStatusMeta,
  appTypeText: appTypeText,
  sourceText: sourceText
}
