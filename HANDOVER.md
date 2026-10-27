# 联智考勤系统 · 交付包上手说明

> 模块化单体 Spring Boot 3.3.5 + Vue 3 的考勤/审批/知识库系统。
> **四步跑通**：建库导数据 → 起后端（8080）→ 起前端（5173）→ 用 `admin / Admin@123` 登录。

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost:5173 |
| 健康检查（真实探测数据库与外部依赖） | http://localhost:8080/api/system/health |
| 接口文档 | http://localhost:8080/swagger-ui/index.html |

---

## 1. 包里有什么 / 需要再生成什么

### ✅ 必须打包（已在本包内）

| 路径 | 作用 | 为什么必须有 |
|---|---|---|
| `server/`（含 11 个 Maven 模块的 `pom.xml` 与源码） | 后端全部源码 | 没有源码就无法构建 `lz-app.jar` |
| `web/` 的 `src/`、`index.html`、`package.json`、`package-lock.json`、`vite.config.js` | 前端源码与依赖锁定 | `package-lock.json` 保证依赖版本与作者一致 |
| `docs/sql/`（`schema.sql`、`data.sql`、`patch-phase*.sql`） | 建表 + 演示数据 + 增量补丁 | 没有它数据库是空的，权限码缺失会 403 |
| `scripts/`（`start-backend.cmd`、`verify-*.mjs` 等） | 启动脚本与回归脚本 | Windows 启动脚本会规避端口环境变量坑 |
| 根目录 `package.json` | `npm run verify` 等聚合命令入口 | 一键回归依赖它 |
| `README.md` | 项目完整说明（架构、口径、演示脚本） | 比本文件更详细的原始文档 |

### 🔶 可选打包（不影响跑通）

| 路径 | 说明 |
|---|---|
| `docs/`（除 `docs/sql/` 外） |  phases 开发记录、答辩题库、设计文档。含作者本机路径等个人信息，**纯阅读用**，不看也不影响运行 |
| `deploy/` | Docker Compose / K8s / Nginx 配置，只在容器化演示时需要 |
| `.github/` | CI 工作流，本地运行用不到 |
| `docs/sql/sample-attendance-14d.sql` | 14 天演示打卡流水，想让统计趋势图有数据点才需要 |

### ⛔ 绝对不要打包（也**没**放进本包）

| 路径 | 为什么不能发 |
|---|---|
| `web/node_modules/`（约 13 万个文件） | 平台相关、体积大；由 `npm install` 重新生成 |
| `server/*/target/`、`web/dist/` | Maven / Vite 构建产物，由 `mvn package`、`npm run build` 重新生成 |
| `application-local.yml` | **含本机数据库口令、JWT 密钥、大模型与地图 Key**。只能用同目录的 `.example` 模板自行生成 |
| `.git/`、`.vscode/`、`.workbuddy/`、`.tmp_extract/`、`.edge-debug/` | 版本历史 / 编辑器配置 / AI 工作日志 / 临时解析产物 |
| `server/data/files/`、`server/lz-app/data/files/`、`*.log`、backup 类文件 | 运行时上传文件与日志，含个人数据 |
| `.workbuddy/`、`docs/reference/` | 作者本机 AI 工作日志与学校原始作业文档（3.3 MB），与运行无关且含个人信息，**本包已排除**；自己重新打包时同样要排除 |

**需要重新生成的产物**：前端依赖 `npm install`；后端可执行包 `mvn package`（产出 `server/lz-app/target/lz-app.jar`，`finalName` 固定为 `lz-app`）。

---

## 2. 运行环境要求

| 组件 | 要求 | 硬性 / 推荐 | 用途 |
|---|---|---|---|
| JDK | **17+** | 硬性 | Spring Boot 3.3.5 的最低要求，低版本直接报 `class file has wrong version` |
| Maven | **3.6+** | 硬性（构建 jar 时） | 首次构建需联网拉依赖；已只发 jar 则可免 |
| Node.js | **18+** | 硬性 | Vite 5 与 `package.json` 的 engines 要求 |
| MySQL | **5.7+** | 硬性 | 必须支持 InnoDB 全文索引 + ngram 中文分词 |
| MySQL 服务端参数 | `ngram_token_size=2` | 硬性（否则知识库中文检索失效） | 见下方配置片段 |
| Redis | 5.x+ | **可选** | 默认 `lz.cache.enabled=false`，不装也能跑完整链路 |
| Docker + Compose | 20.x+ | 可选 | 仅容器化演示需要 |
| Git | 2.x | 可选 | 想保留版本历史才需要 |

### MySQL 服务端必须加的参数（改完重启 MySQL）

```ini
[mysqld]
ft_min_word_len=1
ngram_token_size=2        # 中文二元分词，知识库全文检索依赖它
innodb_large_prefix=ON    # 避免 767 bytes 索引长度限制
```

> 未设置时系统**不会崩**，检索器会自动降级到 `LIKE` 并在日志打 WARN，但召回质量明显下降。

### 三平台安装方式

| 组件 | Windows | macOS | Linux (Ubuntu/Debian) |
|---|---|---|---|
| JDK 17 | `winget install Microsoft.OpenJDK.17` | `brew install openjdk@17` | `sudo apt install openjdk-17-jdk` |
| Maven | `winget install Apache.Maven` | `brew install maven` | `sudo apt install maven` |
| Node 18+ | `winget install OpenJS.NodeJS.LTS` | `brew install node` | `sudo apt install nodejs npm` |
| MySQL 5.7/8 | mysql.com Installer（选 Server only） | `brew install mysql@8.0` | `sudo apt install mysql-server` |
| Redis（可选） | tporadowski Redis 或 WSL | `brew install redis` | `sudo apt install redis-server` |

装完自检：`java -version`、`mvn -v`、`node -v`、`mysql --version`。

---

## 3. 必须替换 / 建议检查的配置

### 3.1 第一步必做：生成本地私密配置

```bash
cd server/lz-app/src/main/resources
cp application-local.yml.example application-local.yml    # Windows: copy
```

模板里每一项的含义与必改标记：

| 字段 | 是否必改 | 说明 |
|---|---|---|
| `spring.datasource.url` 里的 `127.0.0.1:3306/lianzhi` | 按需 | 端口不是 3306 或库名不同才改 |
| `spring.datasource.username` / `password` | **必须** | 换成你自己的 MySQL 账号口令 |
| `spring.data.redis.*`（含 password） | 按需 | **没装 Redis 就把整段连同 `lz.cache` 一起删掉**；删了会走 Noop 缓存，不报错 |
| `lz.cache.enabled` | **必须确认** | 没 Redis 时必须是 `false` |
| `lz.security.jwt.secret` | **必须** | 至少 32 字节随机串：`openssl rand -base64 32` |
| `lz.llm.api-key` 等 llm 段 | 可选 | 知识库"生成式回答"开关；留空则退化为仅检索模式（`lz.llm.enabled=false`） |
| `lz.map.api-key` / `security-code` | 可选 | 管理端"地图选址"功能；留空则坐标手动录入（**入口会自动隐藏**）。需自己在高德控制台申请 Web端(JS API) Key 并配域名白名单 |
| `lz.cache.ttl.*` | 可选 | 默认 10 分钟 / 60 秒，一般不用动 |

### 3.2 建议检查（通常不需改，改了要重启才生效）

| 位置 | 字段 | 说明 |
|---|---|---|
| `server/lz-app/src/main/resources/application.yml` | `lz.security.jwt.secret` 的**默认值** `please-change-me-32-bytes-minimum-lianzhi` | 生产环境务必用环境变量 `LZ_JWT_SECRET` 覆盖 |
| 同上 | `lz.attendance.qr-sign-secret` 默认串 | 二维码签名密钥，建议 `LZ_QR_SIGN_SECRET` 覆盖 |
| 同上 | `lz.web.allowed-origins` = `http://localhost:5173,http://127.0.0.1:5173` | 局域网/ip 演示时追加 `http://<你的IP>:5173` |
| 同上 | `lz.storage.local-path` = `./data/files` | 上传文件落盘目录，可用 `LZ_STORAGE_PATH` 改 |
| `web/vite.config.js` | `proxy./api.target` = `http://localhost:8080` | 后端换端口时用 `LZ_API_TARGET` 覆盖，不改源码 |
| 端口 | 后端 8080、前端 5173、MySQL 3306、Redis 6379 | 被占用时改，改法见第 5 节 |

### 3.3 演示账号（`docs/sql/data.sql` 内置，口令为 BCrypt 摘要）

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员（全权限） |
| `manager01` | `Admin@123` | 部门主管 |
| `emp01` ~ `emp03` | `Emp@123` | 普通员工 |

> 这是演示数据，接手后**第一件事就是改掉 admin 口令**，别带到任何真实环境。

---

## 4. 完整启动步骤（从解压到跑通）

下面以解压到 `<ROOT>` 为例，先把 MySQL 与（可选的）Redis 启动好。

### 4.1 初始化数据库

```bash
# Windows PowerShell：mysql 不在 PATH 时把 MYSQL 换成绝对路径
$MYSQL = "mysql"

mysql -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/schema.sql      # 建 23 张表
mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/data.sql        # 演示数据

# 增量补丁：按 2→6→E 顺序执行，顺序不可颠倒，缺一个就会 403 或字段不存在
# Windows PowerShell 版：Get-ChildItem docs/sql/patch-phase*.sql | ForEach-Object { mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < $_.FullName }
for f in docs/sql/patch-phase*.sql; do
  mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < "$f"
done
```

自检：`sys_user` 5 / `sys_role` 3 / `sys_permission` 54 / `org_company` 2 / `org_department` 3 / `hr_employee` 5 / `biz_place` 3，共 23 张表。

### 4.2 启动后端

```bash
cd server
mvn -U clean package -DskipTests        # 产出 server/lz-app/target/lz-app.jar
cd ..

# Windows（推荐，脚本会清掉可能污染端口的环境变量）
scripts\start-backend.cmd

# macOS / Linux
java -jar server/lz-app/target/lz-app.jar --server.port=8080 --spring.profiles.active=local
```

看到健康检查返回 `"status":"UP"`、`database.connected=true` 才算真的连上库。

### 4.3 启动前端

```bash
cd web
npm install          # 首次需要联网，生成 node_modules/
npm run dev          # 默认 http://localhost:5173
```

浏览器打开 http://localhost:5173 ，用 `admin / Admin@123` 登录。

### 4.4 可选：跑一轮回归（要求后端在跑 + mysql 客户端可用）

```bash
# 根目录执行
LZ_MYSQL_PWD=<你的 MySQL 口令> npm run verify        # Windows / bash 通用写法
```

共 241 项断言（Maven 单元测试 64 + 接口回归 177），全过说明链路完整。

---

## 5. 常见坑（按踩坑概率排序）

1. **改了 `application-local.yml` 却没生效** —— 该文件在 `src/main/resources` 下会被 `mvn package` 打进 jar；只重启跑的是 jar 里的旧副本。要么改完重新打包，要么把同一份文件放到 `server/config/application-local.yml`（Spring Boot 优先读 `file:./config/`）。
2. **8080 被悄悄改成别的端口** —— Spring 宽松绑定会把环境变量 `SERVER_PORT` / `SERVER__PORT` / `PORT` 映射到 `server.port`。Windows 统一用 `scripts\start-backend.cmd` 启动（脚本先清这三个变量）；手动启动时务必显式带 `--server.port`。
3. **解压路径不要带空格或中文** —— 例如本仓库作者的路径含空格也能跑，但 Maven / 批处理脚本对空格路径极其敏感，**强烈建议解压到如 `D:\dev\lianzhi` 这种干净路径**。
4. **中文检索召回差/报 WARN** —— 一定是 MySQL 没设 `ngram_token_size=2`，见第 2 节。
5. **MySQL 8 与 5.7 差异** —— 本项目在 5.7 上开发验证；8.0 可用但要注意默认的认证插件（`mysql_native_password`）与全文索引参数。
6. **npm install 失败** —— 多为网络问题，可换源重试：`npm config set registry https://registry.npmmirror.com`。Node 版本必须 ≥18，低版本 Vite 5 起不来。
7. **Redis 没装但 `lz.cache.enabled=true`** —— 应用仍会启动（自动降级直查库并打 WARN），但每次请求访问 Redis 会拖慢；没装就把 local 配置里那段删掉并把 `lz.cache.enabled` 设为 `false`。
8. **H5 打卡页取不到定位** —— 浏览器只在 `localhost` 或 HTTPS 下开放定位/摄像头。用 `http://<局域网IP>:5173` 真机访问时这两项会被禁用，页面会自动降级到手动粘贴二维码原文（这是设计好的降级，不是 bug）。
9. **演示数据库中的 demo 账号** —— 仅供演示，任何对外部署前必须改口令轮换 JWT 密钥。

---

## 6. 外部依赖都是开关化的（缺哪一项都不会崩）

大模型（`lz.llm.enabled`）、地图选址（`lz.map.provider`）、人脸识别（`lz.face.provider`，默认 `noop` 模拟层并在日志打 `[MOCK]`）、Redis（`lz.cache.enabled`）、Nacos/Feign（`lz.nacos.enabled` / `lz.feign.enabled`）。全部集中在 `server/lz-app/src/main/resources/application.yml` 的 `lz.*` 段，关闭时代码只降级、**不假装可用**。

因此拿到包之后，最省事的跑法是：只填 MySQL 口令 + 一个随机 JWT 密钥，其余全保持默认 —— 除了"知识库生成式回答"和"地图选址"这两个需要你自己 Key 的功能外，其它功能都是真实可用的。
