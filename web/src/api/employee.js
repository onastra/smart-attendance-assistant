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

export function changeEmployeeStatus(id, status) {
  return request.post(`/employees/${id}/status`, { status })
}
