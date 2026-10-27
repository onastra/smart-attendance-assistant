// 统一请求封装：自动带 Token、统一解析 R 包装（code==0 成功）、统一 401 处理
const config = require('../config')
const auth = require('./auth')

// 业务错误码：未登录 / 登录过期
const CODE_UNAUTHORIZED = 40100

function request(options) {
  return new Promise(function (resolve, reject) {
    const token = auth.getToken()
    wx.request({
      url: config.BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success: function (res) {
        const body = res.data
        // HTTP 层 401：Token 缺失或过期
        if (res.statusCode === 401) {
          auth.clear()
          wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          setTimeout(function () { auth.gotoLogin() }, 800)
          reject(new Error('未登录'))
          return
        }
        if (!body || typeof body.code === 'undefined') {
          reject(new Error('后端响应格式异常（HTTP ' + res.statusCode + '），请确认访问的是联智考勤后端'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        // 业务层未登录
        if (body.code === CODE_UNAUTHORIZED) {
          auth.clear()
          wx.showToast({ title: body.message || '请先登录', icon: 'none' })
          setTimeout(function () { auth.gotoLogin() }, 800)
          reject(new Error(body.message || '未登录'))
          return
        }
        // 其他业务错误：弹提示并抛出
        wx.showToast({ title: body.message || '操作失败', icon: 'none' })
        const err = new Error(body.message || '操作失败')
        err.code = body.code
        reject(err)
      },
      fail: function () {
        wx.showToast({
          title: '网络请求失败，请确认后端已启动（' + config.BASE_URL + '）',
          icon: 'none',
          duration: 2500
        })
        reject(new Error('网络请求失败'))
      }
    })
  })
}

function get(url, data) {
  return request({ url: url, method: 'GET', data: data })
}

function post(url, data) {
  return request({ url: url, method: 'POST', data: data })
}

module.exports = {
  request: request,
  get: get,
  post: post
}
