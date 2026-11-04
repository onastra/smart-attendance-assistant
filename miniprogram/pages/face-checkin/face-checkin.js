// 刷脸打卡页：活体 + 1:1 核验通过后写入考勤流水（source=FACE）
// 流程：选场所 → 选类型 → 拍照 → 提交 /api/checkin/face
// 未注册时同一个入口变为人脸注册（/api/face/enroll），注册成功后自动继续打卡
const api = require('../../utils/request')
const auth = require('../../utils/auth')
const checkin = require('../../utils/checkin')
const config = require('../../config')

const TYPE_TEXT = { AUTO: '自动判定', ON_DUTY: '上班', OFF_DUTY: '下班' }

Page({
  data: {
    places: [],
    placeIndex: 0,
    placeLoadError: '',
    statusLoadError: '',
    types: [
      { value: 'AUTO', text: '自动判定' },
      { value: 'ON_DUTY', text: '上班' },
      { value: 'OFF_DUTY', text: '下班' }
    ],
    typeIndex: 0,
    photoPath: '',
    enrolled: false,
    providerMock: false,
    submitting: false
  },

  onLoad: function () {
    console.log('[face-checkin] build=20261104-v3')  // 版本标记：真机 Console 里看不到此行=跑的是旧代码
    if (!auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadPlaces()
    this.loadFaceStatus()
  },

  // 本人绑定的启用场所（后端复验绑定关系，这里只是展示选择器）
  loadPlaces: function () {
    const that = this
    that.setData({ placeLoadError: '' })
    api.get('/api/checkin/my-places').then(function (places) {
      that.setData({ places: places || [], placeIndex: 0 })
    }).catch(function (err) {
      // 失败原因落到页面上：分清"没有绑定"和"请求失败"，避免误判成未绑定场所
      that.setData({ placeLoadError: (err && err.message) || '网络请求失败' })
    })
  },

  retryLoad: function () {
    this.loadPlaces()
    this.loadFaceStatus()
  },

  loadFaceStatus: function () {
    const that = this
    that.setData({ statusLoadError: '' })
    api.get('/api/face/status').then(function (s) {
      that.setData({ enrolled: !!s.enrolled, providerMock: !!s.mock })
    }).catch(function (err) {
      // 状态拿不到不影响打卡：后端会兜底校验，但要如实显示"未知"而不是"未注册"
      that.setData({ statusLoadError: (err && err.message) || '网络请求失败' })
    })
  },

  onPlaceChange: function (e) {
    this.setData({ placeIndex: Number(e.detail.value) || 0 })
  },

  onTypeChange: function (e) {
    this.setData({ typeIndex: Number(e.currentTarget.dataset.index) || 0 })
  },

  // 拍照：走系统相机（chooseMedia），压缩上传控制流量
  takePhoto: function () {
    const that = this
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      sizeType: ['compressed'],
      camera: 'front',
      success: function (res) {
        const file = (res.tempFiles || [])[0]
        if (file && file.tempFilePath) {
          that.setData({ photoPath: file.tempFilePath })
        }
      },
      fail: function () {
        // 用户取消拍摄，不打扰
      }
    })
  },

  submitFace: function () {
    const that = this
    const places = this.data.places
    if (!places.length) {
      wx.showToast({ title: '请先绑定打卡场所', icon: 'none' })
      return
    }
    if (!this.data.photoPath) {
      wx.showToast({ title: '请先拍摄正脸照', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })

    // 定位兜底：个别机型上 getLocation 回调可能迟迟不触发（隐私弹窗被遮挡等），
    // 6 秒后仍未返回就跳过围栏坐标继续提交，避免整条链路卡死在"核验中"
    let located = false
    const fallback = setTimeout(function () {
      if (!located) {
        located = true
        that.doSubmit({})
      }
    }, 6000)
    wx.getLocation({
      type: 'gcj02',
      success: function (loc) {
        if (located) return
        located = true
        clearTimeout(fallback)
        that.doSubmit({ lng: loc.longitude, lat: loc.latitude })
      },
      fail: function () {
        // 定位失败不阻断：后端对未上报坐标跳过围栏校验并在流水标注
        if (located) return
        located = true
        clearTimeout(fallback)
        that.doSubmit({})
      }
    })
  },

  doSubmit: function (coords) {
    const that = this
    try {
      const place = this.data.places[this.data.placeIndex]
      if (!place || !place.id) {
        throw new Error('场所数据异常，请返回重进或点击重新加载')
      }
      const type = this.data.types[this.data.typeIndex].value
      console.log('[face-checkin] doSubmit coords=', JSON.stringify(coords), 'place=', place.id, 'type=', type)
      // 幂等请求号：与扫码通道一致，网络重试不会产生第二条流水
      api.post('/api/checkin/request-id', {}).then(function (data) {
        console.log('[face-checkin] request-id OK:', JSON.stringify(data))
        return that.uploadFace('/api/checkin/face', that.data.photoPath, {
          requestId: data.requestId,
          placeId: place.id,
          checkinType: type,
          lng: coords.lng,
          lat: coords.lat
        })
      }).then(function (result) {
        console.log('[face-checkin] 打卡返回:', JSON.stringify(result).slice(0, 200))
        that.handleCheckinResult(result)
      }).catch(function (err) {
        console.error('[face-checkin] 打卡失败:', err && err.code, err && err.reason, err && err.message)
        that.handleSubmitError(err, coords)
      }).then(function () {
        that.setData({ submitting: false })
      })
    } catch (e) {
      // 同步异常（如场所数据缺失）也要复位按钮并给出提示，不能让"核验中"永久卡住
      this.setData({ submitting: false })
      wx.showModal({ title: '打卡失败', content: (e && e.message) || '请重试', showCancel: false })
    }
  },

  uploadFace: function (path, filePath, formData) {
    const token = auth.getToken()
    const body = {}
    Object.keys(formData).forEach(function (k) {
      if (formData[k] !== undefined && formData[k] !== null) body[k] = String(formData[k])
    })
    return new Promise(function (resolve, reject) {
      wx.uploadFile({
        url: config.BASE_URL + path,
        filePath: filePath,
        name: 'image',
        timeout: 30000,
        formData: body,
        header: { 'Authorization': token ? 'Bearer ' + token : '' },
        success: function (res) {
          let payload = null
          try { payload = JSON.parse(res.data) } catch (e) { /* 稍后统一报错 */ }
          console.log('[face-checkin] upload', path, 'HTTP', res.statusCode, 'body=', String(res.data).slice(0, 200))
          if (!payload || typeof payload.code === 'undefined') {
            reject(new Error('后端响应格式异常（HTTP ' + res.statusCode + '）'))
            return
          }
          if (payload.code === 0) { resolve(payload.data); return }
          const err = new Error(payload.message || '请求失败')
          err.code = payload.code
          err.reason = payload.data && payload.data.reason
          reject(err)
        },
        fail: function () {
          reject(new Error('网络请求失败，请检查后端地址与网络'))
        }
      })
    })
  },

  handleCheckinResult: function (result) {
    const distanceText = result.locationChecked
      ? '围栏校验通过，距场所 ' + Number(result.distanceMeters).toFixed(1) + ' 米'
      : '未做围栏校验（未上报坐标或场所未启用）'
    checkin.goResult({
      ok: true,
      idempotent: !!result.idempotent,
      title: result.idempotent ? '这次打卡已被记录过' : '刷脸打卡成功',
      subtitle: result.idempotent ? '幂等拦截：重复提交不会产生第二条记录' : '人脸核验通过，考勤记录已生成',
      employeeName: result.employeeName || '',
      placeName: result.placeName || '-',
      typeText: result.checkinTypeText || TYPE_TEXT[result.checkinType] || '-',
      resultText: result.resultText || result.result || '-',
      timeText: String(result.checkinTime || '').replace('T', ' ').slice(0, 19),
      distanceText: distanceText,
      result: result.result || ''
    })
  },

  handleSubmitError: function (err, coords) {
    const that = this
    const msg = (err && err.message) || '打卡失败'
    if (err && err.reason === 'NOT_ENROLLED') {
      // 未注册：同一个入口做人脸注册，注册成功后自动继续打卡
      wx.showModal({
        title: '先注册人脸',
        content: '检测到你还没有注册人脸。用刚才这张照片完成注册并继续打卡吗？',
        confirmText: '注册并打卡',
        success: function (res) {
          if (!res.confirm) return
          that.setData({ submitting: true })
          that.uploadFace('/api/face/enroll', that.data.photoPath, {})
            .then(function () {
              that.setData({ enrolled: true })
              wx.showToast({ title: '注册成功', icon: 'success' })
              return new Promise(function (r) { setTimeout(r, 600) })
            })
            .then(function () { that.doSubmit(coords) })
            .catch(function (e2) {
              wx.showModal({ title: '注册失败', content: (e2 && e2.message) || '请重试', showCancel: false })
            })
            .then(function () { that.setData({ submitting: false }) })
        }
      })
      return
    }
    wx.showModal({ title: '打卡失败', content: msg, showCancel: false })
  }
})
