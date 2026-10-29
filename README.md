# 联智·企业级 AI Agent 智能考勤与业务助手

> 学校实训项目｜依据《北游 JavaEE 软件项目开发工程实践（4 周）》方案实施
> 技术栈：**Vue 3 + Element Plus 前端 / Spring Boot 3 模块化单体后端 / MySQL 5.7**
> Nacos 与微服务组件**已配置未启用**；AI 知识库与人脸识别采用**真实检索/接口 + 明确标注的模拟适配层**。

---

## 1. 文档导航

| 文档 | 内容 |
|---|---|
| `docs/introduction.md` | **项目简介**：一页看懂这个项目是什么、能做什么、怎么跑起来 |
| `docs/architecture.md` | 技术选型对照、目标架构、模块边界、AI/人脸适配层设计、待确认事项 |
| `docs/requirements-matrix.md` | 61 条需求（编号 REQ-X-NN），含依据、优先级、实现状态 |
| `docs/database-design.md` | 23 张表的字段、索引、约束与 ER 关系 |
| `docs/api-design.md` | 统一响应/分页/错误码与全模块接口清单 |
| `docs/development-plan.md` | 阶段 0–8 实施计划与依赖关系 |
| `docs/test-plan.md` | 测试用例清单与执行记录规则 |
| `docs/setup-guide.md` | **启动与运行指南（VSCode 版）**，含依赖清单、排错表 |
| `docs/test-report.md` | **测试报告**：全量回归结果、守护点、发现并修复的缺陷清单 |
| `docs/acceptance.md` | **验收清单 + 十分钟演示脚本**（含三条演示链路的操作步骤与讲解词） |
| `docs/user-manual.md` | 用户手册：管理员 / 部门主管 / 员工三视角操作说明 |
| `docs/known-issues.md` | 已知问题与限制：受阻能力、暂缓功能、测试覆盖边界、演示数据说明 |
| `docs/defense-qa.md` | **答辩问答题库**：架构、业务、安全、工程、收尾，含现场应急 |
| `docs/deployment.md` | 部署指南：Redis、Compose、K8s、CI、Docker 磁盘迁出 C 盘 |
| `docs/face-sdk-integration.md` | 真实人脸识别 SDK 的接入改造清单 |
| `docs/uniapp-evaluation.md` | 小程序可行性评估（结论：不做） |
| `docs/progress.md` | 当前完成情况、阻塞项与下一步 |
| `docs/sql/` | 数据库建表脚本、增量补丁与演示数据 |
| `docs/reference/` | **原始资料只读存档**（三份 docx）+ 优先级说明 |

---

## 2. 快速开始

```bash
git clone https://github.com/onastra/smart-attendance-assistant.git
cd smart-attendance-assistant
```

四步跑通：**建库导数据（第 4 节）→ 启动后端（第 5 节，默认 8080）→ 启动前端（第 6 节，默认 5173）→ 用 `admin / Admin@123` 登录。**

跑起来之后有三个地址可用：

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost:5173 |
| 健康检查（真实探测数据库与外部依赖开关） | http://localhost:8080/api/system/health |
| 接口文档 | http://localhost:8080/swagger-ui/index.html |

## 3. 环境要求

| 组件 | 要求 | 说明 |
|---|---|---|
| JDK | 17+ | Spring Boot 3.3.5 的最低要求 |
| Maven | 3.6+ | 首次构建需联网下载依赖 |
| Node.js | 18+ | Vite 5 的要求，仓库含 `package-lock.json` |
| MySQL | 5.7+ | 需 InnoDB 全文索引与 ngram 分词插件（`ngram_token_size=2`），中文检索依赖它 |
| PostgreSQL | 15+ | 可选替代 MySQL：驱动已内置（postgresql 42.7.4），建表脚本见 `docs/sql/postgresql/`，知识库检索走 pg_trgm |
| Redis | 可选 | 默认关闭（`LZ_CACHE_ENABLED=false`），不装也能跑完整链路 |
| Docker | 可选 | 仅容器化演示（Compose / K8s）需要 |
| Git | 2.x | —— |

**所有外部依赖都是开关化的**，默认全部关闭、关闭时应用仍可正常启动：大模型（`lz.llm.enabled`）、地图（`lz.map.provider`）、人脸 SDK（`lz.face.provider`）、Redis（`lz.cache.enabled`）、Nacos/Feign（`lz.nacos.enabled` / `lz.feign.enabled`）。开关与取值集中在 `server/lz-app/src/main/resources/application.yml` 的 `lz.*` 段，代码侧缺配置时只降级、不崩溃，也不会假装可用。

## 4. 数据库初始化

> **PostgreSQL / Supabase 部署**（已验证，接口回归 175 项 + 单元测试全通过）：跳过本节的 MySQL 步骤，
> 改用 `docs/sql/postgresql/` 下的 `schema.sql` + `data.sql`（patch-phase2~6+phaseE 已全部内联，无需再跑补丁），
> 数据源换为 `jdbc:postgresql://<host>:5432/<db>?sslmode=require`、驱动 `org.postgresql.Driver`。
> Supabase 用户直连 `db.<ref>.supabase.co` 需 IPv6；IPv4 网络请用 Session pooler（`aws-0-<region>.pooler.supabase.com:5432`）。
> 知识库全文检索在 PG 侧依赖 `pg_trgm` 扩展（脚本已自动 `CREATE EXTENSION IF NOT EXISTS`）。

```bash
# Windows 下 mysql 客户端未加入 PATH 时，把 MYSQL 换成绝对路径（例如 "C:/mysql/bin/mysql.exe"）
MYSQL="mysql"

# 建库
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 建表（23 张）
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/schema.sql

# 演示数据（虚构员工，BCrypt 真实摘要，不含真实个人信息）
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/data.sql

# 增量补丁：按文件名顺序执行（2 → 6），顺序不可颠倒
# 跳过会导致权限码缺失、二维码/统计/文件功能报 403 或字段不存在
# 提示：不想连续输 5 次口令，可先 export MYSQL_PWD='<口令>' 再执行
for f in docs/sql/patch-phase*.sql; do
  "$MYSQL" -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 lianzhi < "$f"
done
```

- `schema.sql` 可重复执行，但会先清空业务表，只适合开发期重置；`data.sql` 与各补丁脚本均为幂等脚本。
- 补丁不可省略：`data.sql` 只带初始 25 条权限码，五个补丁把它们递增到 54 条，并补上 `att_qrcode.qr_type`、`att_daily_stat.checkin_count`、`sys_file.owner_dept_id` 等运行必需的列。
- 可选：`docs/sql/sample-attendance-14d.sql` 是 14 天演示流水（`source='SAMPLE'`，可按 `request_id` 前缀一键清除），用于让趋势图有数据点。脚本自带的批量登记见 `scripts/seed-sample-attendance.mjs`。

**导入后自检**：应有 23 张表；`sys_user` 5 / `sys_role` 3 / `sys_permission` 54 / `org_company` 2 / `org_department` 3 / `hr_employee` 5 / `biz_place` 3。

**演示账号**（仅演示环境使用，口令在库中以 BCrypt 摘要存储，不入明文）：

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员 |
| `manager01` | `Admin@123` | 部门主管 |
| `emp01` ~ `emp03` | `Emp@123` | 普通员工 |

**员工账号自动分配**：新建员工时系统自动开通登录账号——账号 = 工号（如 `LZ0004`），初始密码统一 `Emp@123`，角色 = 普通员工；创建成功的弹窗里会显示账号密码（明文仅此一次）。员工离职删除时其登录账号一并删除。

**本地私密配置**：复制 `server/lz-app/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入自己的数据库口令与 JWT 密钥。该文件已被 `.gitignore` 忽略（可用 `git check-ignore -v` 验证），仓库里只保留不含真实凭据的模板。

## 5. 后端启动

```bash
# 1) 构建（在仓库根目录执行；finalName 固定为 lz-app）
cd server
mvn -U clean package -DskipTests
cd ..

# 2) 运行期配置一律走环境变量，禁止写入代码库
export LZ_DB_URL="jdbc:mysql://127.0.0.1:3306/lianzhi?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false"
export LZ_DB_USERNAME=root
export LZ_DB_PASSWORD=<你的口令>
export LZ_JWT_SECRET=<至少 32 字节的随机串>

# 3) 启动，二选一
java -jar server/lz-app/target/lz-app.jar --server.port=8080
# Windows 推荐用脚本：会清掉可能污染的端口变量、并在检测到 application-local.yml 时自动激活 local profile
scripts\start-backend.cmd
```

> ⚠️ **端口可能被环境变量改写（踩过）**：Spring 的宽松绑定会把 `SERVER_PORT`、`SERVER__PORT`、`PORT` 这类环境变量映射到 `server.port`，
> shell、IDE、容器平台里若已存在这些变量，8080 会被静默覆盖。排错时先 `echo $SERVER_PORT`；
> Windows 下用 `scripts\start-backend.cmd` 启动（脚本会先清空这三个变量再显式传 `--server.port`），
> 或手动启动时始终显式带 `--server.port`。换端口用 `LZ_SERVER_PORT=8088 scripts\start-backend.cmd`。
>
> 数据源配置了懒加载（`initial-size=0` + `fail-fast`），数据库暂不可用时应用仍会启动，
> 由 `/api/system/health` 真实探测连通性并返回错误信息，**不会因为配不出库就崩，也不会假装连上了**。

## 6. 前端启动

```bash
cd web
npm ci          # 仓库含 package-lock.json，用 ci 保证依赖与锁文件一致
npm run dev     # http://localhost:5173
npm run build   # 生产构建，产物在 web/dist
```

开发期 Vite 已配置 `/api` 代理到 `http://localhost:8080`（默认，可用 `LZ_API_TARGET` 覆盖），无需处理跨域。

**联调自测命令**（验证「前端 → 代理 → 后端」整条链路）：

```bash
curl -s http://localhost:5173/api/system/health
```

应返回后端真实 JSON（不是空、不是错误页）。返回体中的 `database.connected` 反映真实连通性。

## 7. 项目结构

```
├── docs/            项目文档（见导航表）
│   ├── sql/         建表脚本、增量补丁与演示数据
│   ├── samples/kb/  知识库演示用的 PDF/DOCX/XLSX/PPTX/MD 样例
│   └── reference/   原始资料只读存档（docx）
├── server/          后端 Maven 多模块（模块化单体，11 个模块）
│   ├── lz-common/       统一响应、异常、分页、工具类
│   ├── lz-framework/    Web/鉴权/异常/参数校验/OpenAPI/定时任务开关
│   ├── lz-system/       用户、角色、权限、员工、组织
│   ├── lz-place/        办公场所、公司 ID 码、坐标适配
│   ├── lz-attendance/   二维码签发、打卡校验、考勤记录、日汇总统计、定时任务
│   ├── lz-file/         受控文件存储与鉴权下载（本地实现）
│   ├── lz-knowledge/    文档解析、切片、全文+向量混合检索、问答编排、LLM 适配层
│   ├── lz-face/         人脸识别接口与明确标注的模拟实现
│   ├── lz-approval/     请假、加班审批 ✅ 已实现（阶段 E）
│   ├── lz-job/          ⏸ 空模块占位（定时任务当前实现在 lz-attendance/task）
│   └── lz-app/          启动模块（唯一可执行 JAR）
├── web/             前端 Vue3 + Element Plus（后台管理页 + 2 个 H5 移动端页）
├── deploy/          Docker Compose、K8s 清单、Nginx 配置
└── scripts/         启动脚本与分阶段端到端回归脚本
```

## 8. 重要的诚实性约定（本项目红线）

1. 不伪造第三方调用：地图、云存储、HR/ERP/OA、AI 模型、人脸 SDK 未配置时必须显式提示「未启用」。
2. 模拟实现必须标记：响应含 `mock: true`，界面显示「模拟识别」徽章，日志带 `[MOCK]` 前缀。
3. 统计数据必须来自数据库聚合；演示数据必须带 `dataSource=SAMPLE` 标记。
4. 不允许通过跳过测试、关闭校验、吞异常制造构建成功。
5. 学校原始资料（`docs/reference/` 下三份 docx）**只读不改**，需求有歧义时以其为准。
6. **Q-1 决议（2026-10-26 暂缓，2026-10-27 阶段 E 已补齐实现）**：模块 E《请假/加班审批》7 条需求原决议暂缓，后在阶段 E 实现（新增 `lz-approval` 模块，复用两张预留表、未改表结构）。仅 E-07 统计消费尚未接入，属"后端数据就绪、消费待接"，已在 `docs/known-issues.md` 如实标注。

## 9. 当前状态与质量门禁

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 项目审计与实施计划 | ✅ 7 份文档 |
| 1 | 骨架与开发环境（11 个 Maven 模块 + Vue3 前端） | ✅ |
| 2 | 认证鉴权 + 员工/组织/场所 CRUD | ✅ |
| 3 | 二维码 + H5 扫码打卡闭环 | ✅ 30 项回归 |
| 4 | 统计与看板（幂等重算 + 定时任务） | ✅ 32 项回归 |
| 5 | AI 知识库（retrieval-only 起步，后升级 llm-augmented）+ 人脸模拟适配层 | ✅ 46 项回归 |
| 6 | Redis 缓存、过期码清理、文件鉴权下载、H5 响应式、uni-app 评估、Docker/K8s/CI 实跑 | ✅ 20 项回归 |
| 7 | 测试、构建与验收 | ✅ **241 项全通过** + 验收清单/演示脚本 |
| 8 | 文档与答辩 | ✅ 测试报告、用户手册、已知问题、答辩题库（Markdown 版，正式排版待定） |
| E | 模块 E 请假/加班审批补齐（解除 Q-1 暂缓） | ✅ 29 项回归（`verify-phaseE.mjs`），E-07 统计消费待接 |
| F | 地图选址：高德 JS API 2.0 接入（搜索/取点/半径圈可视化，GCJ-02↔WGS84 自动换算） | ✅ 20 项回归（`verify-phaseF.mjs`），Key 未配置时诚实降级为手动录入 |

**质量门禁**：一条命令跑完全量回归

```bash
export LZ_MYSQL_BIN="$(command -v mysql)"   # Windows 未加入 PATH 时填绝对路径
export LZ_MYSQL_PWD=<本机 MySQL 口令>
npm run verify
# Maven 单元测试 64 项 + 接口回归 177 项 = 241 项（含阶段 E 29 项、阶段 F 20 项）
```

`npm run verify:api` 只跑接口回归，`npm run verify:unit` 只跑单元测试；接口回归要求后端已在 8080 运行。

详见 `docs/progress.md`；验收与演示见 `docs/acceptance.md`。

> 🎉 **阶段 9 · AI Agent 增强（2026-10-27 完成）**：接入硅基流动真实 LLM/Embedding 服务——
> ① 新增 `EmbeddingClient`，`kb_chunk` 向量化，检索升级为**全文+语义混合召回（RRF 融合）**；
> ② 新增 `kb_conversation`/`kb_message` 多轮会话（`/api/kb/ask` 支持上下文，`/api/kb/ask/stream` SSE 流式输出）；
> ③ 前端 `Ask.vue` 重构为聊天式 UI（消息气泡、打字机效果、来源引用面板、历史会话）；
> ④ `known-issues.md` 中 BK-2（生成式 AI 回答缺失）已标记解决。一页速览见 `docs/introduction.md`。
