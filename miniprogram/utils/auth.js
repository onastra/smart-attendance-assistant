// 登录态存取：token 与当前用户信息统一放本地缓存
const TOKEN_KEY = 'lz_token'
const PROFILE_KEY = 'lz_profile'

function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

function setToken(token) {
  try {
    wx.setStorageSync(TOKEN_KEY, token || '')
  } catch (e) {
    // 存储异常不阻断登录流程
  }
}

function getProfile() {
  try {
    return wx.getStorageSync(PROFILE_KEY) || null
  } catch (e) {
    return null
  }
}

function setProfile(profile) {
  try {
    wx.setStorageSync(PROFILE_KEY, profile || null)
  } catch (e) {
    // 同上
  }
}

function hasPermission(code) {
  const profile = getProfile()
  if (!profile || !profile.permissions) {
    return false
  }
  return profile.permissions.indexOf(code) >= 0
}

function isLoggedIn() {
  return getToken() !== ''
}

function clear() {
  try {
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(PROFILE_KEY)
  } catch (e) {
    // 清理失败可忽略
  }
}

// 跳转登录页（用于 401 或未登录拦截）
function gotoLogin() {
  wx.reLaunch({ url: '/pages/login/login' })
}

module.exports = {
  getToken: getToken,
  setToken: setToken,
  getProfile: getProfile,
  setProfile: setProfile,
  hasPermission: hasPermission,
  isLoggedIn: isLoggedIn,
  clear: clear,
  gotoLogin: gotoLogin
}
