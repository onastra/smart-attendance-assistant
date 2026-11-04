# 独立服务器部署指南（域名 + Nginx 方案）

> 适用场景：云服务器（ECS/轻量/VPS）+ 已购域名。与 `deploy-aliyun-fc.md`（阿里云函数计算 Docker 方案）**二选一**，目标相同：让小程序/Web 端通过公网访问后端。

## 0. 架构说明（先读）

- **数据库不在服务器上**：用的是共享 Supabase PostgreSQL（两名开发者数据实时互通），服务器只跑 Spring Boot 应用，不需要安装任何数据库。
- **人脸识别在 Linux 服务器上为模拟模式**：虹软 SDK 只提供 Windows x64 库（`server/libs/arcface/` 已被 gitignore），Linux 下 `lz.face.provider` 默认 `noop`（见 `application.yml` 第 127 行），注册/打卡全流程可用，比对为模拟性质。要演示真实活体识别，用真机连开发者本机后端（见 README）。
- **单 jar 部署**：前端产物打进 jar 的 static 目录，API 与页面同源，Nginx 只做反向代理 + HTTPS。

## 1. 环境准备（服务器上）

```bash
# JDK 17、Maven 3.9+、Node 18+、git
java -version    # 需要 17.x
mvn -version
node -v
```

## 2. 拉取代码与配置

```bash
git clone <仓库地址> && cd smart-attendance-assistant   # 或 git pull origin master 更新

# 创建云端配置（已被 .gitignore 忽略，不会被提交）
cp server/lz-app/src/main/resources/application-cloud.yml.example \
   server/lz-app/src/main/resources/application-cloud.yml
```

编辑 `application-cloud.yml`，填入 Supabase 真实值（与开发者共用同一库，向项目作者索取）：

| 字段 | 填什么 |
|---|---|
| `url` | `jdbc:postgresql://aws-0-<区域>.pooler.supabase.com:5432/postgres?sslmode=require` |
| `username` | `postgres.<项目ref>`（Session pooler 格式，带前缀） |
| `password` | Supabase 控制台 Database password |
| `lz.security.jwt.secret` | ≥32 字节的随机串（**必须与开发者一致**，否则互相解析不了对方的 token） |
| `lz.security.qr-sign-secret` | 同上，二维码签名密钥需一致 |

> ⚠️ 该 yml 会被打进 jar，**不要把 jar 外发或提交**。

## 3. 打包（单 jar，含前端）

```bash
node scripts/build-fc-bundle.mjs
# 产出：server/lz-app/target/lz-app.jar
# （脚本虽名为 fc-bundle，但注释明确适用于任何"单体 jar 部署"场景）
```

依赖 Node 的构建机与运行服务器可以不是同一台：打包完成后只需把 jar + `application-cloud.yml` 拷贝到服务器。

## 4. 运行（systemd 托管）

```bash
sudo mkdir -p /opt/lianzhi/config
sudo cp server/lz-app/src/main/resources/application-cloud.yml /opt/lianzhi/config/
sudo cp server/lz-app/target/lz-app.jar /opt/lianzhi/
```

`/etc/systemd/system/lianzhi.service`：

```ini
[Unit]
Description=LianZhi Attendance Backend
After=network.target

[Service]
User=www
WorkingDirectory=/opt/lianzhi
ExecStart=/usr/bin/java -jar /opt/lianzhi/lz-app.jar --server.port=8080 --spring.profiles.active=cloud --spring.config.additional-location=file:/opt/lianzhi/config/
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now lianzhi
curl http://127.0.0.1:8080/api/auth/login -X POST -H 'Content-Type: application/json' -d '{}'   # 返回 JSON 即正常
```

> 注意：`--spring.config.additional-location` 不能省——外部 config 目录里的 cloud 配置优先于 jar 内，便于日后改配置不动 jar。

## 5. Nginx 反向代理 + HTTPS

```nginx
server {
    listen 80;
    server_name your.domain.com;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 25m;    # 人脸照片上传，与后端 multipart 限制对齐
    }
}
```

```bash
sudo certbot --nginx -d your.domain.com    # 免费 HTTPS 证书，自动续期
```

## 6. 客户端切换到新地址

- **小程序**：`miniprogram/config.js` 的 `BASE_URL` 改为 `https://your.domain.com`；
  - 微信开发者工具勾选「不校验合法域名」即可调试；
  - **正式发布/体验版**要求域名已 ICP 备案且在 mp.weixin.qq.com 配置 request 合法域名（HTTPS 必须）——域名没备案就只能在开发/真机调试模式用；
- **Web 管理端**：浏览器直接访问 `https://your.domain.com`（前端已打进 jar）。

## 7. 验证清单

1. `curl -X POST https://your.domain.com/api/auth/login -H 'Content-Type: application/json' -d '{}'` 返回业务 JSON；
2. 小程序登录 LZ0001 / admin 成功；
3. Web 端登录 admin，考勤数据与开发者本机后端看到的**完全一致**（同一个 Supabase 库）；
4. 扫码打卡全流程通；人脸打卡流程通（provider=noop 模拟比对，属预期）。

## 与 FC 方案的差异速览

| | 本文档（服务器） | deploy-aliyun-fc.md |
|---|---|---|
| 运行载体 | systemd + java -jar | 函数计算 Docker 容器 |
| 产物 | lz-app.jar | Docker 镜像 |
| 域名/HTTPS | 自备域名 + Nginx + certbot | FC 自动分配域名（HTTPS 自带） |
| 费用 | 服务器已有则零增量 | FC 按量计费 |
| 小程序正式版 | 需域名 ICP 备案 | FC 香港域名无法备案，正式版不可用 |
