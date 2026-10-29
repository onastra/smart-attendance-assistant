import request from '@/utils/request'

/**
 * 员工档案接口（后端 lz-system 模块）。
 */
export function pageEmployees(params) {
  return request.get('/employees', { params })
}

export function getEmployee(id) {
  return request.get(`/employees/${id}`)
}

export function createEmployee(data) {
  return request.post('/employees', data)
}

export function updateEmployee(id, data) {
  return request.put(`/employees/${id}`, data)
}

/**
 * 离职（物理删除档案及其关联数据）。误删需重新建档，无复职。
 */
export function deleteEmployee(id) {
  return request.delete(`/employees/${id}`)
}
