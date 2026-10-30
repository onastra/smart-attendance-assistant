import request from '@/utils/request'

/**
 * 打卡与考勤记录接口（后端 lz-attendance 模块）。
 */
export function requestCheckinId() {
  return request.post('/checkin/request-id')
}

export function submitCheckin(data) {
  return request.post('/checkin', data)
}

export function fetchMyRecords(params) {
  return request.get('/my/attendance/records', { params })
}

export function fetchAdminRecords(params) {
  return request.get('/admin/attendance/records', { params })
}

/**
 * 全局考勤时间设置（管理员侧）：上下班时间与迟到/早退宽限分钟数。
 */
export function getAttendanceSettings() {
  return request.get('/attendance/settings')
}

export function updateAttendanceSettings(data) {
  return request.put('/attendance/settings', data)
}

/**
 * 打卡失败原因码 → 可读提示。
 *
 * <p>后端在失败的 data.reason 里给出机器可读的原因码（见 CheckinService），
 * 这里只负责翻译成人话；未知原因码不猜测，直接返回后端原文。</p>
 */
export function describeCheckinFailure(error) {
  const reason = error?.data?.reason
  const map = {
    QR_FORMAT_INVALID: '这不是本系统的考勤二维码，请重新扫码',
    QR_NOT_FOUND: '该二维码不是本系统签发的，请联系管理员',
    QR_SIGN_INVALID: '二维码校验未通过，内容可能已被篡改',
    QR_PAYLOAD_MISMATCH: '二维码与签发记录不一致，请重新获取',
    QR_EXPIRED: '该二维码已过期，请向管理员获取最新二维码',
    QR_INVALID: '该二维码已被管理员作废',
    QR_USED: '这是一次性二维码，且已被使用过',
    PLACE_DISABLED: '该办公场所已停用，无法打卡',
    EMPLOYEE_NOT_BOUND: '当前账号未关联员工档案，无法打卡',
    EMPLOYEE_INACTIVE: '你的员工档案已停用，无法打卡',
    PLACE_NOT_BOUND: '你不属于该办公场所，无法在此打卡',
    DUPLICATE_CHECKIN: '你刚刚已经打过卡了，无需重复提交',
    REQUEST_ID_CONFLICT: '页面停留过久，请刷新后重新打卡',
    OUT_OF_RANGE: '你不在该场所允许的打卡范围内'
  }
  return map[reason] || error?.message || '打卡失败'
}
