# 项目启动与运行指南（VSCode 版）

> 适用环境：Windows + VSCode + PostgreSQL(Docker 容器) + 本机 Redis
> 实测于 2026-10-27，命令与预期输出均为真实结果。
>
> ⚠️ **2026-10-27 起数据库已从 MySQL 迁移到 PostgreSQL**（合作者提交 `97aafc3`）。
> 文中凡涉及 MySQL 客户端、3306 端口、`LZ_MYSQL_*` 环境变量的地方，
> 均已换成 PostgreSQL 的对应写法；旧 MySQL 库仅作为历史数据保留，不再被代码使用。
>
> 项目根目录记作 `<ROOT>` = `D:\A_school_training\Project Document`

---

## 0. 先讲清楚：这套系统由 4 个进程组成

启动顺序**有依赖关系**，跳步会报错：

```
   ┌──────────────────┐   ┌──────────────┐
   │ PostgreSQL :5432 │   │ Redis :6379  │     ① 基础设施（PG 走 Docker 容器，Redis 是 Windows 服务）
   └──────┬───────────┘   └──────┬───────┘
          │                 │
          └────────┬────────┘
                   ▼
        ┌───────────────────────┐
        │ 后端 SpringBoot :8080 │              ② 依赖 ①，缺一就起不来
        └───────────┬───────────┘
                    ▼
        ┌───────────────────────┐
        │ 前端 Vite :5173       │              ③ 依赖 ②，反向代理 /api → 8080
        └───────────────────────┘
```

**Redis 不是硬依赖**：它挂了后端仍能起（缓存自动降级为直查，日志 WARN），只是没缓存。
**PostgreSQL 是硬依赖**：它没起来，后端能启动但所有接口报数据库连接失败。
本机 PG 跑在 Docker 容器 `lz-postgres` 里（`--restart unless-stopped`，开机自动拉起）。

---

## 1. 环境依赖清单

| 组件 | 版本（你的机器实测） | 检查命令 | 备注 |
|---|---|---|---|
| JDK | 17.0.13 | `java -version` | **必须 17**，SpringBoot 3.x 要求 ≥17 |
| Maven | 3.9.6 | `mvn -v` | 装在 `D:\apache-maven-3.9.6-bin\apache-maven-3.9.6` |
| Node | v22.22.2 | `node -v` | |
| npm | 10.9.7 | `npm -v` | |
| PostgreSQL | 16.15 | `docker ps --filter name=lz-postgres` | 容器 `lz-postgres`，卷 `lz-pg-data`，库名 `lianzhi`；客户端用 `docker exec -it lz-postgres psql -U postgres -d lianzhi` |
| Redis | 5.0.14 | 服务名 `redis-lianzhi` | 装在 `D:\tools\redis` |
| ~~MySQL~~ | 5.7.19（仅历史库） | 服务名 `MySQL` | **代码已不兼容**，仅保留旧数据供对照，不要再配成它 |
| VSCode | — | `code -v` | 可执行在 `D:\Microsoft VS Code\bin\code` |

> 若 `java` / `mvn` / `node` 提示"不是内部或外部命令"，说明没配 PATH。
> 装完 JDK/Maven/Node 后**要重开终端**（PATH 变更不会作用于已打开的窗口）。

---

## 2. VSCode 准备工作

### 2.1 打开项目

**必须打开整个根目录**，不要单独打开 `server/` 或 `web/`：

```bash
cd "D:\A_school_training\Project Document"
code .
```

单独打开子目录会导致：**根 `package.json`（全量回归入口 `npm run verify`）找不到**，
Maven 多模块的父子 pom 关系也会失效。

### 2.2 推荐插件（装了少踩很多坑）

| 插件 | 用途 | 必要性 |
|---|---|---|
| **Extension Pack for Java**（微软官方） | Java 语法、Maven 集成、JUnit 运行 | 必装（否则 Java 文件全是裸文本） |
| **Spring Boot Extension Pack** | application.yml 自动补全、Bean 跳转 | 强烈推荐 |
| **Vue - Official**（Volar，注意不是 Vetur） | Vue 3 单文件组件高亮 | 必装 |
| **ESLint** | 前端规范提示 | 推荐 |
| **REST Client** | 直接在 .http 文件里调接口 | 可选但很好用 |
| **GitLens** | 看每行代码是谁改的 | 可选 |

> ⚠️ **装了 Vetur 请禁用它**，与 Vue - Official 冲突，会导致 `.vue` 文件报错。

### 2.3 集成终端的选择

推荐用 **Git Bash**（VSCode 里：`Ctrl+`\`` 打开终端 → 右上角下拉 → Select Default Profile → Git Bash）。

原因是本项目脚本里既有 `.cmd` 又有 `.mjs`，Git Bash 两种都能跑；
PowerShell 下 `export KEY=VALUE` 语法不同，命令示例要改写。

---

## 3. 首次搭建（只需做一次）

### 3.1 安装前端依赖

```bash
cd <ROOT>/web
npm install
```

预期输出：`added 68 packages`（本机实测 68 个顶层包）。
若网络慢，可换国内源：`npm config set registry https://registry.npmmirror.com`

### 3.2 生成本机私密配置（关键步骤）

`application-local.yml` 存的是数据库口令和 JWT 密钥，**已被 .gitignore 忽略**，仓库里没有。
不生成这个文件，后端会用默认配置启动 → 连不上数据库。

```bash
cd <ROOT>/server/lz-app/src/main/resources
cp application-local.yml.example application-local.yml
```

生成后按你的实际环境确认/修改这几项：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/lianzhi
    username: postgres
    password: <你的 PostgreSQL 口令>   # ← 容器 POSTGRES_PASSWORD 设的那个
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ...        # ← 改成你的 Redis 口令（没设就等于没认证时删掉这行）

lz:
  cache:
    enabled: true          # ← Redis 没装就设为 false，走 Noop 不报错
  security:
    jwt:
      secret: ...          # ← 生产环境务必换成自己的随机串
  map:
    provider: amap         # ← 地图选址：高德 Web端 JS API
    api-key: ...           # ← 高德控制台的应用 Key（不填则坐标手动录入，页面不出现地图入口）
    security-code: ...     # ← 配套安全密钥 jscode（2021-12 后申请的 Key 必填）
```

> **地图 Key 安全说明**：Key 只写在本文件（已 gitignore），仓库与前端源码里都没有。
> 高德 JS API Key 本质"前端可见"（由后端 map-status 接口下发），真正的防线是
> 高德控制台 → 应用管理 → 为该 Key 配置**域名白名单**（开发期加 `http://localhost:5173`）。

> **Git Bash 里执行 `cp` 时若提示文件已存在**，说明之前生成过，直接跳过即可。

> ⚠️ **改完配置要重新打包，只重启不管用（2026-10-27 实测踩过）**
> 本文件在 `src/main/resources` 下，会被 `mvn package` 打进 jar。你改了本地文件后
> 直接 `java -jar` 重启，跑的仍是 **jar 里的旧副本**。验证方法：
> `unzip -p server/lz-app/target/lz-app.jar BOOT-INF/classes/application-local.yml | grep map:`
> 正确做法：改完先 `mvn -B package -DskipTests`，再重启；
> 或者不想重打包时，把同一份文件放到 `server/config/application-local.yml`
> （Spring Boot 默认会读 `file:./config/`，且外部配置优先级高于 classpath 内的同名文件）。

### 3.3 初始化数据库

PostgreSQL 的建表脚本把 MySQL 版的 `patch-phase2~6 + phaseE` 全部内联了，**一次执行即为最终结构**，
不用再逐个跑 patch：

```bash
cd <ROOT>
docker exec -i lz-postgres psql -U postgres -d lianzhi -q < docs/sql/postgresql/schema.sql
docker exec -i lz-postgres psql -U postgres -d lianzhi -q < docs/sql/postgresql/data.sql
```

然后补 14 天演示打卡流水（**不补的话趋势图与统计看板是空的**）：

```bash
export LZ_PG_HOST=127.0.0.1 LZ_PG_PORT=5432 LZ_PG_DB=lianzhi LZ_PG_USER=postgres
export LZ_PG_PWD=你的口令 LZ_PG_SSLMODE=disable
node scripts/seed-sample-attendance.mjs
```

> `schema.sql` 开头会 `CREATE EXTENSION IF NOT EXISTS pg_trgm`（知识库中文检索依赖它）。
> 脚本用 `DROP TABLE IF EXISTS ... CASCADE`，**可重复执行，但会清空业务数据**。
> 如果本地 PG 没开 SSL（Docker 默认如此），务必带 `LZ_PG_SSLMODE=disable`，
> 否则种子脚本与回归脚本的 psycopg2 通道会静默连不上。

### 3.4 首次部署后必须做一次日汇总重算

初始化脚本只插打卡流水，**不算日汇总**，直接看板是空的。

```bash
curl -X POST http://localhost:8080/api/statistics/daily-rebuild \
  -H "Authorization: Bearer <admin的token>"
```

不带日期参数时会自动按「最早一条打卡流水 ~ 今天」推导区间（这条逻辑是阶段 7 专门修的缺陷，
在此之前它只算当天，导致首次部署看板全 0）。

---

## 4. 日常启动流程（每次开机做这些）

### 4.1 检查 / 启动基础设施

```powershell
Get-Service redis-lianzhi | Select-Object Name, Status
docker ps --filter name=lz-postgres --format "{{.Names}} {{.Status}}"
```

预期 Redis 是 `Running`、容器是 `Up`。若不是，手动启动：

```powershell
Start-Service redis-lianzhi
docker start lz-postgres
```

两者都是 `Automatic` 启动类型，正常开机就在跑。**不建议停它们**——它们是 services 不是"演示服务"。

### 4.2 启动后端

**方式 A：用现成 jar（推荐，快）**

```bash
cd <ROOT>/server/lz-app
java -jar target/lz-app.jar --server.port=8080 --spring.profiles.active=local
```

启动成功标志（日志末尾）：

```
Started AppApplication in 6.6 seconds
```

**方式 B：改了代码要重新打包**

```bash
cd <ROOT>/server
mvn -B package          # 会自动跑 64 个单元测试
```

> ⚠️ 若报 **Unable to rename lz-app.jar**：8080 上还有旧的 java 进程占着 jar 文件。
> 先 `taskkill //PID <pid> //F`（用 `netstat -ano | grep :8080` 查 pid），再打包，之后再重新拉起。

**方式 C：用项目脚本**

```bash
# Git Bash
cd <ROOT> && ./scripts/start-backend.cmd
```

这个脚本的价值在于：它会**清掉 `SERVER__PORT` / `SERVER_PORT` / `PORT` 环境变量**，
再显式传 `--server.port`。因为 SpringBoot 的 relaxed-binding 会把这些环境变量映射到 `server.port`，
曾出现过被某个工具注入 `SERVER__PORT=56043` 导致端口悄悄漂移的情况。
另外它会自动检测 `application-local.yml` 是否存在并决定是否激活 local profile。

#### 本机群的坑：环境变量污染端口

如果你在终端里看到后端起了但 8080 访问不了，检查：

```bash
env | grep -i "SERVER\|^PORT"
```

有输出就说明被污染了，换方式 C 启动。

### 4.3 启动前端

**另开一个终端**（后端那个要一直占着）：

```bash
cd <ROOT>/web
npm run dev
```

预期输出：

```
VITE v5.4.21  ready in 749 ms
➜  Local:   http://localhost:5173/
```

> Vite 已配好代理：`/api/*` → `http://localhost:8080`（见 `web/vite.config.js`），
> 所以前端页面里直接写 `/api/xxx` 就能通，不用配 CORS，也不会跨域。
> 想改后端地址：`LZ_API_TARGET=http://localhost:8088 npm run dev`

### 4.4 VSCode 一键启动（推荐）

我已在本机创建 `.vscode/tasks.json`，用法：

1. `Ctrl+Shift+P` → 输入 `Tasks: Run Task`
2. 选 `dev: 前端 Vite` 或 `dev: 后端 SpringBoot`
3. 想两个一起起：选 `dev: 全栈（后端+前端）`

终端面板会分成两个 tab，各自独立可见。

> 注意：`.vscode/` 在项目 `.gitignore` 里（第 15 行），所以这份配置**只在你本机生效、不进仓库**，
> 换机器需要重新建。`docs/setup-guide.md` 里保留了等价内容，照抄即可。

---

## 5. 验证是否真的起来了

### 5.1 三秒快速自检

```bash
curl http://localhost:8080/api/system/health
```

预期返回（**注意是扁平结构，没有 data 包裹**——这点写脚本时容易踩空）：

```json
{
  "status": "UP",
  "database": { "connected": true },
  "cache": { "mode": "redis", "hitRate": "..." }
}
```

- `database.connected` 必须是 `true`（且 url 以 `jdbc:postgresql` 开头），否则去看 PG 容器
- `cache.mode` 是 `redis` 说明缓存生效；显示 `noop` 说明 Redis 没连上（会降级但不致命）

### 5.2 浏览器验收

打开 http://localhost:5173 ，用管理员登录：

| 账号 | 口令 | 权限 |
|---|---|---|
| `admin` | `Admin@123` | 全部 |
| `manager`（部门主管） | 见 `docs/sql/data.sql` | 本部门 |
| `employee`（普通员工） | 见 `docs/sql/data.sql` | 仅本人 |

能进首页 + 看板有数字 = 全链路 OK。

### 5.3 跑全量回归（改完代码建议跑一次）

```bash
cd <ROOT>
export LZ_PG_HOST=127.0.0.1 LZ_PG_PORT=5432 LZ_PG_DB=lianzhi LZ_PG_USER=postgres
export LZ_PG_PWD=你的 PostgreSQL 口令 LZ_PG_SSLMODE=disable
npm run verify
```

预期输出（2026-10-27 实测）：

```
套件                              通过    失败    耗时
Maven 单元测试                    64       0    12.7s
阶段 3 二维码打卡闭环                  30       0    5.0s
阶段 4 统计与看板                    31       0    2.1s
阶段 5 知识库与人脸识别                 46       0   48.4s
阶段 6 文件受控存储与鉴权                20       0    0.6s
阶段 E 审批中心（用户新增）               29       0    0.6s
阶段 F 地图选址（用户新增）               20       0    0.3s
----------------------------------------------------------------
合计：通过 240 项，失败 0 项
```

> ⚠️ **必须带 PG（或 MySQL）连接环境变量**，脚本才会清理上一轮测试数据。
> 不带的话会因为"5 分钟去重窗口"和"上传去重"出现**假失败**——
> 看起来像功能坏了，实际是数据没清。这个坑在 `progress.md` 里踩过并记录下来了。
>
> PG 通道走 Python 的 `psycopg2`（`pip install psycopg2-binary`）。
> 本地 Docker 的 PG 没开 SSL，**必须** `LZ_PG_SSLMODE=disable`；
> 连 Supabase 等云端 PG 则去掉它（默认 `require`）。

---

## 6. 停止服务

**停演示服务**（保留 PostgreSQL 容器与 Redis）：

```powershell
# 查 pid
netstat -ano | Select-String ":8080|:5173" | Select-String "LISTENING"
# 停掉
Stop-Process -Id <pid> -Force
```

或者直接关闭 VSCode 里对应的终端 tab（Ctrl+C 不行的话就直接关 tab）。

**停容器版**（如果跑了 Docker）：

```bash
cd <ROOT>/deploy
export DOCKER_HOST=tcp://localhost:2375
docker compose stop        # 保留数据卷
# docker compose down -v   # 连数据一起删（下次会重新初始化）
```

> **Redis 不建议停**——它是 Windows 服务，`Automatic` 启动类型，
> 停了以后每次都要手动 Start-Service，没必要。
> PostgreSQL 在 Docker 容器里，容器设了 `--restart unless-stopped`，
> 会随 Docker Desktop 一起自动拉起；`docker stop lz-postgres` 才会停。

---

## 7. 常见故障排查表

按"遇到概率"排序，都是从实际踩坑里来的：

| 现象 | 原因 | 解法 |
|---|---|---|
| `mvn package` 报 `Unable to rename lz-app.jar` | 8080 上的旧 java 进程占着 jar | `netstat -ano \| grep :8080` 查 pid 后 `taskkill //PID <pid> //F` |
| 后端起了但 health 里 `database.connected=false` | PG 容器没跑，或 `application-local.yml` 里连的还是 mysql 串/口令不对 | `docker start lz-postgres`；核对 yml 的 url/username/password |
| health 里 `cache.mode=noop` | Redis 没开或口令错 | `Start-Service redis-lianzhi`；**不致命**，会自动降级直查 |
| 8080 能启动但访问不到 | 环境变量 `SERVER__PORT/PORT` 污染了端口 | `env \| grep -i server`；改用 `scripts/start-backend.cmd` |
| 前端 5173 起不来：`EADDRINUSE` | 端口被上次的 vite 占着 | 换端口 `npm run dev -- --port 5174`，或杀掉旧进程 |
| 前端页面白屏 / 接口 404 | 前端代理目标是 8080，但后端没起 | 先确认 `curl localhost:8080/api/system/health` 通 |
| 回归脚本一堆失败，但功能明明正常 | 没带 `LZ_PG_PWD`/`LZ_MYSQL_PWD`，旧数据没清理 | 见 §5.3 说明，带上环境变量重跑 |
| 首次部署看板全是 0 | 没跑日汇总重算，或没灌演示打卡流水 | 见 §3.3（种子脚本）与 §3.4 |
| 统计看板有数据但出勤率 0% | 库里只有当天流水，重算区间没覆盖 | 跑 `scripts/seed-sample-attendance.mjs` 补 14 天流水后重算 |
| Redis 里读不到 key / 看板数字不更新 | 缓存 TTL 未失效 | 重算后会自动 evict；或 `docker exec lz-redis redis-cli FLUSHDB`（容器）/ 直接 `redis-cli -a <pwd> FLUSHDB` |
| Vue 文件满屏报错 | 装了 Vetur 而不是 Vue - Official | 禁用 Vetur，装 Vue - Official |
| **手机能打开 H5 打卡页，点登录却弹「没有访问该资源的权限」** | **CORS**：后端白名单不含手机用的局域网 Origin。Vite 代理会把 `Origin: http://<局域网IP>:5173` 原样转发，后端 Host 是 `localhost:8080`，于是判定跨域 → OPTIONS 预检 403 | 见 §10。默认已放行 `192.168.*` / `10.*` / `172.20.*`；其它网段重启后端时加 `LZ_WEB_ORIGINS=http://<网段>:*` |
| H5 页点「打开摄像头扫码」提示"不是 HTTPS 或 localhost" | 局域网 HTTP 不是安全上下文，浏览器禁用摄像头与定位 | 正常行为不是故障：改用后台「复制原文」手动粘贴（校验链照走）；要真扫码需给 Vite 配 HTTPS 或用 Android Chrome 的 `#unsafely-treat-insecure-origin-as-secure` |

---

## 8. 端口一览表

| 端口 | 用途 | 谁在跑 |
|---|---|---|
| 5432 | PostgreSQL | Docker 容器 `lz-postgres`（数据卷 `lz-pg-data`） |
| 6379 | Redis | Windows 服务 `redis-lianzhi` |
| 8080 | 后端 API | `java -jar lz-app.jar` |
| 5173 | 前端开发服务器 | Vite |
| 3306 | ~~MySQL~~ | 仅历史库，**代码已不用** |
| 18080 | **容器版** 后端 | docker compose（`deploy/`） |
| 18081 | **容器版** 前端（Nginx） | docker compose |

> 本机 8080/5173 与容器 18080/18081 是**两套独立环境**，可以同时跑互不干扰。
> `deploy/` 那套 compose 仍是旧版（含 MySQL），数据库迁移后**尚未同步**，
> 要跑容器演示需先把 compose 里的 db 服务换成 PostgreSQL。

---

## 9. 一条命令版（日常快速回顾）

```bash
# 前置：Docker Desktop 已启动（PostgreSQL 容器 lz-postgres 会随 Docker 自启），
#        Redis 是 Windows 服务 redis-lianzhi，开机自启

# 终端 1
cd "D:/A_school_training/Project Document/server/lz-app"
java -jar target/lz-app.jar --server.port=8080 --spring.profiles.active=local

# 终端 2
cd "D:/A_school_training/Project Document/web"
npm run dev

# 浏览器：http://localhost:5173  （admin / Admin@123）
```

改了后端代码后，把"终端 1"换成：

```bash
cd "D:/A_school_training/Project Document/server" && mvn -B package
# 然后重新执行终端 1 的 java -jar
```

---

## 10. 手机真机演示

手机与电脑连**同一个 Wi-Fi**（用错网线/VirtualBox/WSL 的网段会连不上）。本机 Wi-Fi 地址用
`ipconfig` 查，形如 `192.168.1.4`。

### 10.1 H5 打卡页

浏览器打开 `http://<电脑IP>:5173/h5/checkin`，页面内登录 `emp01 / Emp@123`。

- 防火墙：首次启动 java / node 时 Windows 会弹窗，**必须点"允许访问"**，否则手机连不进来。
- 手机只需能访问 5173：`/api` 由 Vite 代理转发到本机 8080。
- **摄像头扫不了码**：局域网 HTTP 不是安全上下文，浏览器禁掉 `getUserMedia`。
  演示改用后台「考勤二维码 → 复制原文」发到手机粘贴提交——签名、有效期、场所绑定、
  幂等这些校验链照走，只是跳过围栏校验。

### 10.2 微信小程序

1. `miniprogram/config.js` 的 `BASE_URL` 改成 `http://<电脑IP>:8080`（**是 8080，小程序没有 Vite 代理**）
2. 开发者工具：详情 → 本地设置 → 勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」
3. 点「预览」，手机微信扫出来的码打开开发版
4. `wx.scanCode` **模拟器不能真扫**（只弹"选本地图片"），必须真机；
   模拟器用"选图片"可验证扫码之后的业务逻辑
5. 被扫的码用**长期固定码（24 小时）**：动态码只有 120 秒，演示中途会过期；
   且**别勾「一次性」**，否则第二次扫就失败

### 10.3 换网络后登录报「没有访问该资源的权限」

这是跨域（CORS），不是权限问题——前端把 `Origin` 原样转发给后端，后端按白名单校验：

```bash
# 诊断：看预检是否 200，403 就是来源不在白名单
curl -X OPTIONS -H "Origin: http://<电脑IP>:5173" \
     -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: content-type" \
     http://127.0.0.1:8080/api/auth/login
```

后端已默认放行 `localhost` / `127.0.0.1` / `192.168.*` / `10.*` / `172.20.*`（iPhone 热点网段）。
若你落在别的网段，重启后端时显式指定：

```bash
set LZ_WEB_ORIGINS=http://localhost:5173,http://172.18.*:*
java -jar lz-app/target/lz-app.jar --spring.profiles.active=local
```

> Spring 的 CORS 模式**只支持 `*` 通配符**，不支持 `172.1[6-9]` 这类字符类（实测返回 403）。
> 生产环境务必用 `LZ_WEB_ORIGINS` 指定真实域名，别依赖开发默认值。

---

## 11. 相关文档

| 文档 | 内容 |
|---|---|
| `README.md` | 项目总览与技术栈 |
| `docs/deployment.md` | Docker Compose / K8s / CI 部署 |
| `docs/acceptance.md` | 验收清单 + 十分钟演示脚本 |
| `docs/test-report.md` | 测试策略与 192 项回归说明 |
| `docs/known-issues.md` | 已知限制（人脸模拟、AI retrieval-only 等） |
