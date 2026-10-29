import request from '@/utils/request'

/**
 * 统计与看板接口。
 *
 * 说明：所有响应都带 dataScope（ALL/DEPT/SELF）与 dataSource（real/sample/mixed），
 * 前端必须展示这两项——前者告诉用户"你看到的是谁的考勤"，
 * 后者声明"这些数字里有几分是演示数据"（需求 IDX-D8）。
 */

export function fetchSummary(params) {
  return request({ url: '/statistics/summary', method: 'get', params })
}

export function fetchTrend(params) {
  return request({ url: '/statistics/trend', method: 'get', params })
}

export function fetchDepartmentStat(params) {
  return request({ url: '/statistics/department', method: 'get', params })
}

export function fetchPlaceHeat(params) {
  return request({ url: '/statistics/place-heat', method: 'get', params })
}

/**
 * 重算日汇总（幂等）。
 *
 * @param {Object} payload { date } 单日，或 { dateFrom, dateTo } 区间
 */
export function rebuildDailyStat(payload) {
  return request({ url: '/statistics/daily-rebuild', method: 'post', params: payload })
}

/** 出勤趋势预测：近 28 天同星期均值外推未来 7 天 */
export function fetchForecast() {
  return request({ url: '/statistics/forecast', method: 'get' })
}

/** 今日未打卡 / 请假名单 */
export function fetchTodayAbsent() {
  return request({ url: '/statistics/today-absent', method: 'get' })
}

/** 人均工时趋势 */
export function fetchWorkHours(params) {
  return request({ url: '/statistics/work-hours', method: 'get', params })
}

/** 员工画像（不传 employeeId 默认本人） */
export function fetchEmployeeProfile(params) {
  return request({ url: '/statistics/employee-profile', method: 'get', params })
}
