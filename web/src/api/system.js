import request from '@/utils/request'

/** 健康检查：返回后端真实探测的依赖状态 */
export const getHealth = () => request.get('/system/health')

/** 登录 */
export const login = (data) => request.post('/auth/login', data)
