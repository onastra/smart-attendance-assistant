# 联智·企业级 AI Agent 智能考勤与业务助手

> 学校实训项目｜依据《北游 JavaEE 软件项目开发工程实践（4 周）》方案实施
> 架构决策：**Vue 3 + Element Plus 前端 / Spring Boot 3 模块化单体后端 / MySQL 5.7**
> Nacos 与微服务组件**已配置未启用**；AI 知识与人脸识别采用**真实检索/接口 + 明确标注的模拟适配层**。

---

## 1. 文档导航

| 文档 | 内容 |
|---|---|
| `docs/architecture.md` | 技术选型对照、目标架构、模块边界、AI/人脸适配层设计、待确认事项 |
| `docs/requirements-matrix.md` | 61 条需求（编号 REQ-X-NN），含依据、优先级、实现状态 |
| `docs/database-design.md` | 23 张表的字段、索引、约束与 ER 关系 |
| `docs/api-design.md` | 统一响应/分页/错误码与全模块接口清单 |
| `docs/development-plan.md` | 阶段 0–8 实施计划与依赖关系 |
| `docs/test-plan.md` | 测试用例清单与执行记录规则 |
| `docs/setup-guide.md` | **启动与运行指南（VSCode 版）**，含依赖清单、排错表 |
| `docs/test-report.md` | **测试报告**：192 项回归结果、守护点、发现并修复的缺陷清单 |
| `docs/acceptance.md` | **验收清单 + 十分钟演示脚本**（含 CPT90 三条链路的操作步骤与讲解词） |
| `docs/user-manual.md` | 用户手册：管理员 / 部门主管 / 员工三视角操作说明 |
| `docs/known-issues.md` | 已知问题与限制：受阻能力、暂缓功能、测试覆盖边界、演示数据说明 |
| `docs/defense-qa.md` | **答辩问答题库**：架构、业务、安全、工程、收尾，含现场应急 |
| `docs/deployment.md` | 部署指南：Redis、Compose、K8s、CI、Docker 磁盘迁出 C 盘 |
| `docs/face-sdk-integration.md` | 真实人脸识别 SDK 的接入改造清单 |
| `docs/uniapp-evaluation.md` | 小程序可行性评估（结论：不做） |
| `docs/progress.md` | 当前完成情况、阻塞项与下一步 |
| `docs/sql/` | 数据库建表脚本与演示数据 |
| `docs/reference/` | **原始资料只读存档**（`北游JavaEE工程实践(4周).docx`、`项目总指令.docx`、`项目分析.docx`）+ 优先级说明 |

---

## 2. 环境要求（本机实测 2026-10-26）

| 组件 | 要求 | 本机实际 |
|---|---|---|
| JDK | 17+ | 17.0.13 ✅ |
| Maven | 3.6+ | 3.9.6 ✅ |
| Node.js | 18+ | 22.22.2 ✅ |
| MySQL | 5.7+ | 5.7.19（端口 3306，服务运行中）✅ |
| Redis | 可选（默认关闭） | 未安装 ➖ |
| Docker | 可选（部署演示用） | daemon 未启动 ➖ |
| Git | 2.x | 2.55.0 ✅ |

## 3. 数据库初始化

> **本机（Windows + MySQL 5.7.19）实测路径**：客户端不在 PATH 的默认解析路径内，需用绝对路径：
> `D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe`

```bash
MYSQL="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe"

# 建库（原库列表确认无 lianzhi，不会覆盖既有数据库）
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 建表（23 张，幂等可重复执行；再次执行会清空业务数据）
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/schema.sql

# 演示数据（虚构员工，BCrypt 真实摘要，不含真实个人信息）
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/data.sql
```

**已完成（2026-10-26）**：23 张表 + 演示数据均已导入，零错误。数据量：
`sys_user` 5 / `sys_role` 3 / `sys_permission` 25 / `org_company` 2 / `org_department` 3 / `hr_employee` 5 / `biz_place` 3。

**演示账号**：

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员 |
| `manager01` | `Admin@123` | 部门主管 |
| `emp01` ~ `emp03` | `Emp@123` | 普通员工 |

**口令写入位置**：`server/lz-app/src/main/resources/application-local.yml`（已被 `.gitignore` 忽略，可用 `git check-ignore -v` 验证），**绝不入库**。

## 4. 后端启动

```bash
cd server

# 数据库连接信息用环境变量注入，禁止写入代码库
export LZ_DB_URL="jdbc:mysql://127.0.0.1:3306/lianzhi?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false"
export LZ_DB_USERNAME=root
export LZ_DB_PASSWORD=<你的口令>
export LZ_JWT_SECRET=<32位以上随机串>

mvn -U clean package -DskipTests
# finalName 固定为 lz-app
cd ..
scripts\start-backend.cmd
# 或手动：java -jar server\lz-app\target\lz-app.jar --server.port=8080
```

启动后：
- 健康检查：http://localhost:8080/api/system/health
- 接口文档：http://localhost:8080/swagger-ui/index.html

> ⚠️ **端口被篡改的已知坑（真实踩过）**：Spring 宽松绑定会把环境变量 `SERVER__PORT` 解析成 `server.port`。
> 本机 Agent 终端曾注入 `SERVER__PORT=56043`，导致默认 8080 被静默改写。
> 因此请使用 `scripts/start-backend.cmd`（脚本会先清空 `SERVER__PORT` / `SERVER_PORT` / `PORT` 再显式传 `--server.port`），
> 或手动启动时始终显式带端口。`LZ_SERVER_PORT=8088 scripts\start-backend.cmd` 可换端口。
>
> 另外数据源配置了懒加载（`initial-size=0`），数据库暂不可用时应用仍会启动，
> 由 `/api/system/health` 真实探测连通性并返回错误信息，**不会因为配不出库就崩，也不会假装连上了**。

> 本地私密配置：复制 `server/lz-app/src/main/resources/application-local.yml.example` 为 `application-local.yml`
> 填写数据库口令（该文件已被 `.gitignore` 忽略，绝不入库）。

## 5. 前端启动

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm run build    # 生产构建
```

开发期 Vite 已配置 `/api` 代理到 `http://localhost:8080`（默认，可用 `LZ_API_TARGET` 覆盖），无需处理跨域。

**联调自测命令**（验证"前端 → 代理 → 后端"整条链路）：

```bash
curl -s http://localhost:5173/api/system/health
```

应返回后端真实 JSON（不是空、不是错误页）。返回体中的 `database.connected` 反映真实连通性。

## 6. 项目结构

```
├── docs/            项目文档（见导航表）
│   ├── sql/         数据库建表脚本与演示数据
│   └── reference/   原始资料只读存档（docx）
├── server/          后端 Maven 多模块（模块化单体）
│   ├── lz-common/       统一响应、异常、分页、工具类
│   ├── lz-framework/    Web/鉴权/异常/参数校验/OpenAPI
│   ├── lz-system/       用户、角色、权限、员工、组织
│   ├── lz-place/        办公场所、公司 ID 码、坐标适配
│   ├── lz-attendance/   二维码、打卡、考勤记录、统计
│   ├── lz-approval/     请假、加班审批 ⏸ 暂缓（Q-1 决议，无业务代码）
│   ├── lz-knowledge/    知识库解析、切分、检索、问答
│   ├── lz-face/         人脸识别接口与模拟实现
│   ├── lz-job/          Spring Task 定时任务
│   └── lz-app/          启动模块（唯一可执行 JAR）
└── web/             前端 Vue3 + Element Plus
```

## 7. 重要的诚实性约定（本项目红线）

1. 不伪造第三方调用：地图、云存储、HR/ERP/OA、AI 模型、人脸 SDK 未配置时必须显式提示"未启用"。
2. 模拟实现必须标记：响应含 `mock: true`，界面显示"模拟识别"徽章，日志带 `[MOCK]` 前缀。
3. 统计数据必须来自数据库聚合；演示数据必须带 `dataSource=SAMPLE` 标记。
4. 不允许通过跳过测试、关闭校验、吞异常制造构建成功。
5. 学校原始资料（`docs/reference/` 下三份 docx）**只读不改**，需求有歧义时以其为准。
6. **Q-1 决议（2026-10-26）**：模块 E《请假/加班审批》7 条需求**暂缓实现**，优先保证核心考勤闭环。数据库表保留作为结构预留，不做半截实现混入验收。

## 8. 当前状态（2026-10-26）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 项目审计与实施计划 | ✅ 7 份文档 |
| 1 | 骨架与开发环境（11 个 Maven 模块 + Vue3 前端） | ✅ |
| 2 | 认证鉴权 + 员工/组织/场所 CRUD | ✅ |
| 3 | 二维码 + H5 扫码打卡闭环 | ✅ 30 项回归 |
| 4 | 统计与看板（幂等重算 + 定时任务） | ✅ 32 项回归 |
| 5 | AI 知识库（retrieval-only）+ 人脸模拟适配层 | ✅ 46 项回归 |
| 6 | Redis 缓存、定时任务、文件鉴权下载、H5 响应式、uni-app 评估、Docker/K8s/CI 真跑 | ✅ 20 项回归 |
| 7 | 测试、构建与验收 | ✅ **192 项全通过** + 验收清单/演示脚本 |
| 8 | 文档与答辩 | ✅ Markdown 已产出（测试报告、用户手册、已知问题、答辩题库），待学校模板后套格式 |

**质量门禁**：一条命令跑完全量回归

```bash
export LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe"
export LZ_MYSQL_PWD=<本机 MySQL 口令>
npm run verify
# Maven 单元测试 64 项 + 接口回归 128 项 = 192 项
```

**当前进度已到阶段 8 收尾**，Git 从 `685f9b1` 起共约 20 次提交。
详见 `docs/progress.md`；验收与演示见 `docs/acceptance.md`。

> ⚠️ **当前后台进程**：后端 8080、前端 dev server 5173 正在运行，可直接访问验证。
