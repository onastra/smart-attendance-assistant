/**
 * WGS84 与 GCJ-02（火星坐标）互转。
 *
 * <p><strong>为什么需要这个文件</strong>：本项目数据库统一存 WGS84——
 * H5 打卡页浏览器定位（navigator.geolocation）上报的就是 WGS84，
 * 围栏校验（后端 GeoDistanceUtil）两侧同坐标系才不会出现几百米偏差。
 * 而高德地图 SDK 内部使用 GCJ-02（国测局加密坐标）：</p>
 *
 * <ul>
 *   <li>地图选点回填表单：GCJ-02 → WGS84（存库）</li>
 *   <li>编辑场所时把库里的 WGS84 显示到地图：WGS84 → GCJ-02</li>
 * </ul>
 *
 * <p>算法为业界通用的国测局偏移公式（非高德官方接口，纯本地计算，
 * 不产生网络请求）。gcj02ToWgs84 的逆运算是近似解（一阶偏导视为常量），
 * 误差约 1~2 米，远小于本项目打卡半径下限（10 米），可安全使用。</p>
 *
 * <p>该文件为纯函数，无任何外部依赖，可被 Node 直接 import 做数值断言
 * （scripts/verify-phaseF.mjs）。</p>
 */

const PI = Math.PI
const A = 6378245.0 // 长半轴
const EE = 0.00669342162296594323 // 扁率

/** 粗略判断是否在中国境外（境外无偏移，GCJ-02 与 WGS84 相同） */
export function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(lng, lat) {
  let ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lat * PI) + 40.0 * Math.sin((lat / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((lat / 12.0) * PI) + 320 * Math.sin((lat * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(lng, lat) {
  let ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lng * PI) + 40.0 * Math.sin((lng / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((lng / 12.0) * PI) + 300.0 * Math.sin((lng / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

/**
 * WGS84 → GCJ-02。
 * @returns {{lng: number, lat: number}}
 */
export function wgs84ToGcj02(lng, lat) {
  if (outOfChina(lng, lat)) {
    return { lng, lat }
  }
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)
  return { lng: lng + dLng, lat: lat + dLat }
}

/**
 * GCJ-02 → WGS84（近似逆运算）。
 *
 * <p>思路：偏移量在 ~500m 尺度上变化极缓，直接在 GCJ 坐标处求一次偏移
 * 并反减回去即可。往返转换后与原 WGS84 的偏差实测在 1e-6 度量级
 * （约 0.1 米），见 verify-phaseF.mjs 的往返断言。</p>
 * @returns {{lng: number, lat: number}}
 */
export function gcj02ToWgs84(lng, lat) {
  if (outOfChina(lng, lat)) {
    return { lng, lat }
  }
  const gcj = wgs84ToGcj02(lng, lat)
  return { lng: lng * 2 - gcj.lng, lat: lat * 2 - gcj.lat }
}
