import request from '@/utils/request'

/**
 * 办公场所接口（后端 lz-place 模块）。
 */
export function pagePlaces(params) {
  return request.get('/places', { params })
}

export function getPlace(id) {
  return request.get(`/places/${id}`)
}

export function createPlace(data) {
  return request.post('/places', data)
}

export function updatePlace(id, data) {
  return request.put(`/places/${id}`, data)
}

export function changePlaceStatus(id, status) {
  return request.post(`/places/${id}/status`, { status })
}

export function getMapStatus() {
  return request.get('/places/map-status')
}

export function listPlaceEmployees(placeId) {
  return request.get(`/places/${placeId}/employees`)
}

export function bindEmployees(placeId, employeeIds) {
  return request.post(`/places/${placeId}/employees`, { employeeIds })
}

export function unbindEmployee(placeId, employeeId) {
  return request.delete(`/places/${placeId}/employees/${employeeId}`)
}

export function regeneratePlaceCode(placeId) {
  return request.post(`/places/${placeId}/regenerate-code`)
}
