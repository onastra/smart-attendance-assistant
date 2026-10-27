import request from '@/utils/request'

/**
 * 请假/加班审批接口（后端 lz-approval 模块）。
 *
 * 说明：审批与考勤同属敏感操作，权限边界由后端拦截器把关，前端仅做入口展示控制。
 */
export function submitApplication(data) {
  return request.post('/applications', data)
}

export function pageMyApplications(params) {
  return request.get('/applications/mine', { params })
}

export function cancelApplication(id) {
  return request.post(`/applications/${id}/cancel`)
}

export function pageApprovable(params) {
  return request.get('/applications/todo', { params })
}

export function pageAllApplications(params) {
  return request.get('/applications', { params })
}

export function approveApplication(id, data) {
  return request.post(`/applications/${id}/approve`, data)
}
