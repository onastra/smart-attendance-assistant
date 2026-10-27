// 后端服务地址配置
// 1. 微信开发者工具模拟器：保持 localhost 即可（需在「详情 → 本地设置」勾选
//    「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」）。
// 2. 真机预览：改成电脑的局域网 IP，例如 http://192.168.1.100:8088，
//    手机与电脑必须连同一个 Wi-Fi，且同样勾选上面的"不校验合法域名"。
// 3. 端口 8088 是本机后端实际端口（8080 被本机 tomcat9 服务占用）。
const BASE_URL = 'http://localhost:8088'

module.exports = {
  BASE_URL: BASE_URL
}
