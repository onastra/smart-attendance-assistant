import request from '@/utils/request'

/** 健康检查：返回后端真实探测的依赖状态 */
export const getHealth = () => request.get('/system/health')

// ==================== 账号管理 ====================

export function pageUsers(params) {
  return request.get('/system/users', { params })
}

export function listRoles() {
  return request.get('/system/users/roles')
}

export function createUser(data) {
  return request.post('/system/users', data)
}

export function updateUser(id, data) {
  return request.put(`/system/users/${id}`, data)
}

export function changeUserStatus(id, status) {
  return request.post(`/system/users/${id}/status`, { status })
}

export function resetUserPassword(id, password) {
  return request.post(`/system/users/${id}/password`, { password })
}

// ==================== 角色与权限 ====================

export function pageRoles(params) {
  return request.get('/system/roles', { params })
}

export function listPermissions() {
  return request.get('/system/roles/permissions')
}

export function listRolePermissionIds(roleId) {
  return request.get(`/system/roles/${roleId}/permissions`)
}

export function createRole(role, permissionIds) {
  return request.post('/system/roles', { role, permissionIds })
}

export function updateRole(id, role, permissionIds) {
  return request.put(`/system/roles/${id}`, { role, permissionIds })
}

export function grantRolePermissions(id, permissionIds) {
  return request.post(`/system/roles/${id}/permissions`, { permissionIds })
}

export function deleteRole(id) {
  return request.delete(`/system/roles/${id}`)
}
