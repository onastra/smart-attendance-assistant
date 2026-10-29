# 部署到阿里云函数计算（FC）——不买云服务器的方案

> 目标：把前后端合并成一个容器镜像，部署到阿里云函数计算 FC，
> 数据库继续用现有的 Supabase——**全程不买云服务器**。
>
> 架构：`浏览器 ──HTTPS──> FC(容器, 9000端口, jar 内含前端页面) ──> Supabase PG`
>
> 成本预期：FC 新用户有免费试用额度；试用后按量计费，课程演示的访问量级
> 每月大约几块~几十元（以控制台账单为准）。弹性模式没有固定包月费用。

---

## 0. 前置知识：为什么这样设计

| 问题 | 方案 | 原因 |
|---|---|---|
| 前端放哪 | 打进 jar 的 `static/` 目录 | 一个函数一个域名，无跨域、无独立静态托管费 |
| 数据库 | 继续用 Supabase | 免费额度够两人开发，免运维，FC 无需 VPC |
| 密钥（数据库密码/JWT/LLM/地图） | FC **环境变量**注入 | jar 与镜像里零密钥（pom 已排除 local/cloud 配置），泄露镜像也不泄露密钥 |
| Redis | 不部署（cache 关闭） | 本机开发时也没用 Redis，代码自动降级直查库 |
| HTTPS | FC 默认域名自带 | 不用买证书 |

## 1. 一次性准备（约 10 分钟）

1. **开通函数计算 FC**：https://fcnext.console.aliyun.com → 首次进入按引导开通
   （选择"3.0"，区域建议 `华东1（杭州）` 或 `华北2（北京）`）
2. **开通容器镜像服务 ACR**（个人版免费）：https://cr.console.aliyun.com
   → 创建**个人版实例** → 创建**命名空间**（如 `lianzhi`）→ 创建**镜像仓库**
   （名称 `lianzhi-backend`，代码源选"本地仓库"），记下仓库地址，形如：
   `registry.cn-hangzhou.aliyuncs.com/lianzhi/lianzhi-backend`
3. **设置 Registry 登录凭据**：ACR 控制台 → 实例 → 访问凭证 → 设置固定密码

## 2. 构建合并产物（本机，每次代码更新后执行）

```bash
cd code/smart-attendance-assistant
node scripts/build-fc-bundle.mjs
```

脚本做三件事：`vite build` → 产物拷进 `resources/static` → `mvn package`。
产出 `server/lz-app/target/lz-app.jar`（约 73 MB，前后端一体）。

> ⚠️ 打包前先停掉本机正在跑的后端进程，否则 jar 被锁定会 repackage 失败。

## 3. 构建并推送镜像（本机 Docker）

```bash
# <区域> 按你第 1 步选择的区域替换，如 cn-hangzhou
docker build -f Dockerfile.fc -t lianzhi-backend:fc .
docker login registry.<区域>.aliyuncs.com    # 用户名是 ACR 页面显示的阿里云账号名
docker tag lianzhi-backend:fc registry.<区域>.aliyuncs.com/lianzhi/lianzhi-backend:fc
docker push registry.<区域>.aliyuncs.com/lianzhi/lianzhi-backend:fc
```

镜像内容：`eclipse-temurin:17-jre` + 一个 jar，**不含任何数据库密码**（已由 pom 排除）。

## 4. 创建 FC 函数（控制台，只做一次）

1. FC 控制台 → **创建函数** → **使用容器镜像** → 选刚才推送的 `:fc` 镜像
2. 关键配置：
   - **监听端口**：`9000`（镜像里 EXPOSE 的就是它）
   - **CPU/内存**：0.35 核 / 1024 MB 起步（够用，后续可调）
   - **实例类型**：弹性实例；实例并发建议 1~4
   - **请求处理超时**：120 秒（知识库问答链路较长）
3. **环境变量**（函数配置 → 环境变量，全部必填，值找李嘉哲要或沿用本地配置）：

   | 变量名 | 值（示例） | 说明 |
   |---|---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require` | Supabase Session pooler |
   | `SPRING_DATASOURCE_USERNAME` | `postgres.nxvomwfjzrrbfciwvtil` | 带项目前缀 |
   | `SPRING_DATASOURCE_PASSWORD` | `数据库密码` | 别写进任何文档 |
   | `SPRING_DATASOURCE_HIKARI` 系 | 不需要 | 用默认即可 |
   | `LZ_JWT_SECRET` | 32 字节以上随机串 | 与李嘉哲本机 cloud.yml 一致 |
   | `LZ_QR_SIGN_SECRET` | 与李嘉哲本机一致 | 二维码 HMAC 签名 |
   | `LZ_LLM_ENABLED` | `true` | 知识库生成增强 |
   | `LZ_LLM_API_KEY` | SiliconFlow 的 key | |
   | `LZ_LLM_BASE_URL` | `https://api.siliconflow.cn/v1` | |
   | `LZ_LLM_MODEL` | `Qwen/Qwen2.5-7B-Instruct` | |
   | `LZ_LLM_EMBEDDING_ENABLED` | `true` | 向量检索 |
   | `LZ_LLM_EMBEDDING_MODEL` | `BAAI/bge-large-zh-v1.5` | |
   | `LZ_MAP_PROVIDER` | `amap` | 高德地图选址 |
   | `LZ_MAP_API_KEY` | 高德 JS API Key | |
   | `LZ_MAP_SECURITY_CODE` | 高德安全密钥 | |

   > Spring Boot 的环境变量天然覆盖 yml（`SPRING_DATASOURCE_PASSWORD` →
   > `spring.datasource.password`），这正是 jar 里不放密钥也能跑通的原因。

4. **高级配置 → 存储挂载**：不需要。上传文件落在容器可写层（见"已知限制"）

## 5. 验证

创建完成后 FC 给一个默认域名，形如：
`https://<函数名>-<hash>.<区域>.fcapp.run`

```bash
# 1) 健康检查：database.connected 必须是 true
curl https://<默认域名>/api/system/health

# 2) 前端首页：应返回 HTML
curl -I https://<默认域名>/

# 3) 浏览器打开默认域名 → 登录页 → admin 登录 → 逐页点一遍
```

验收清单：
- [ ] `/api/system/health` 的 `database.connected = true`
- [ ] 登录、员工管理、打卡统计各页正常
- [ ] 直接刷新深链接（如 `/attendance/records`）不 404（SPA 回退已内置）
- [ ] 知识库提问能返回生成式答案（LLM 通了）
- [ ] 办公场所编辑弹窗出现高德地图

## 6. 更新版本的日常流程

```bash
git pull
node scripts/build-fc-bundle.mjs
docker build -f Dockerfile.fc -t lianzhi-backend:fc .
docker tag lianzhi-backend:fc registry.<区域>.aliyuncs.com/lianzhi/lianzhi-backend:fc
docker push registry.<区域>.aliyuncs.com/lianzhi/lianzhi-backend:fc
# FC 控制台 → 部署新版本（选同一镜像 tag 即可，容器镜像更新后点"部署"）
```

## 7. 已知限制（演示场景下可接受）

1. **上传文件不持久**：文件存储 provider=local，落在容器可写层。FC 按需拉起实例，
   实例回收后上传的知识库文档会丢。演示前重新上传一遍即可；要彻底解决需接 OSS
   （代码侧 `lz.storage.provider` 预留了扩展位）
2. **冷启动**：弹性模式实例缩到 0 后首个请求要等 10~30 秒（Java 启动 + 建库连接池）。
   介意的话花小钱配 1 个**预留实例**（约 0.35C/1G 按月计费，量级几十元/月）
3. **小程序线上发布仍需备案域名**：FC 默认域名不能用于微信小程序"服务器域名"配置
   （要求 ICP 备案）。网页端演示不受影响；小程序要上线时需要：买域名 → 备案 →
   自定义域名绑定 FC + 证书
4. **Supabase 免费版 7 天不活跃休眠**：部署上云后有人访问反而更不容易休眠；
   长期没人用，答辩前记得去控制台唤醒
5. **FC 流量费用**：公网出流量按 GB 计费，演示级可以忽略；别拿来当下载站

## 8. 回退方案

随时可以回到本机模式：`scripts/start-backend.cmd` + 前端 `npm run dev`，
数据库两边连的是同一个 Supabase，云端下线不影响本地开发。
