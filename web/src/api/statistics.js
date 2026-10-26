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
