import request from '@/utils/request'

/**
 * 组织架构接口：公司、部门、岗位。
 */
export function listCompanies() {
  return request.get('/org/companies')
}

export function createCompany(data) {
  return request.post('/org/companies', data)
}

export function updateCompany(id, data) {
  return request.put(`/org/companies/${id}`, data)
}

export function deleteCompany(id) {
  return request.delete(`/org/companies/${id}`)
}

export function listDepartments(companyId) {
  return request.get('/org/departments', { params: { companyId } })
}

export function departmentTree(companyId) {
  return request.get('/org/departments/tree', { params: { companyId } })
}

export function createDepartment(data) {
  return request.post('/org/departments', data)
}

export function updateDepartment(id, data) {
  return request.put(`/org/departments/${id}`, data)
}

export function deleteDepartment(id) {
  return request.delete(`/org/departments/${id}`)
}

export function listPositions(companyId) {
  return request.get('/org/positions', { params: { companyId } })
}

export function createPosition(data) {
  return request.post('/org/positions', data)
}

export function updatePosition(id, data) {
  return request.put(`/org/positions/${id}`, data)
}

export function deletePosition(id) {
  return request.delete(`/org/positions/${id}`)
}
