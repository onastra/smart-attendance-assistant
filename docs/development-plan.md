> 阶段划分依据：《项目总指令》第八节。每个阶段结束按第九节要求输出：完成的需求编号、新增/修改文件、关键设计、执行命令与结果、失败项、下一阶段计划。

最后更新：2026-10-26

---

## 0. 总体节奏

| 阶段 | 名称 | 目标 | 状态 |
|---|---|---|---|
| 0 | 项目审计与实施计划 | 已产出架构/需求矩阵/数据库/接口/测试/进度文档 | **已完成** |
| 1 | 项目骨架与开发环境 | Maven 多模块 + Vue3 工程成型，`mvn compile` 与 `npm run build` 通过 | **仅差数据库初始化**（等 MySQL 口令） |
| 2 | 基础业务与权限 | 认证鉴权、角色权限、员工/组织/场所管理 | 未开始 |
| 3 | 核心考勤闭环 | 二维码、H5 打卡、打卡记录、个人与管理员查询 | 未开始 |
| 4 | 统计与可视化 | 统计汇总、Trend、ECharts 看板（审批模块已按 Q-1 暂缓） | 未开始 |
| 5 | AI 知识库与高级能力 | 文档解析、切分、检索、问答与来源引用；人脸适配层 | 未开始 |
| 6 | 基础设施与移动端扩展 | Redis（待安装）、Nacos（已配置未启用）、定时任务、存储与场景规则 | 未开始 |
| 7 | 测试、构建与验收 | 单元测试/接口测试、构建、持久性验证、部署物整理 | 未开始 |
| 8 | 文档与答辩材料 | 全套 project docs + 演示脚本 + 答辩问答准备 | 未开始 |

---

## 阶段 0：项目审计与实施计划 ✅

**产出**：`docs/architecture.md`、`docs/requirements-matrix.md`、`docs/database-design.md`、`docs/api-design.md`、`docs/test-plan.md`、`docs/progress.md`、`README.md`。

**关键结论**
- 原始方案「提到」的技术 ≠ 「必须实现」的功能；原始文档**未给出逐项必做清单**，因此按优先级划分：**必做 38 / 暂缓 6 / 待确认 8 / 扩展 7 / 受阻 2**（共 61 条，见需求矩阵）。
- **2026-10-26 决议（Q-1）**：模块 E《请假/加班审批》7 条需求整体暂缓，理由是原始方案仅在项目介绍处提及、核心功能与课程计划均未单列；数据库表保留作为结构预留。若后续导师要求或主线完成，在阶段 4 尾部补做即可，无需改库。
- 技术冲突已裁定：Element Plus（Vue3）、模块化单体、Nacos/Feign 已配置未启用、AI 与人脸识别走"真实检索 + 标注的模拟适配层"。
- 环境结论：JDK 17 ✅、Maven 3.9.6 ✅、Node 22.22.2 ✅、MySQL 5.7.19 运行中 ✅、**Redis 未安装 ⚠️**、**Docker daemon 未启动 ⚠️**、**无虹软 SDK 授权 ⚠️**。

**待你/老师确认**（见 `docs/architecture.md` 第 7.2 节 Q-2~Q-6）：Redis 是否必须、模拟人脸识别可否交付、Docker/CI/K8s 是否要真演示、提交清单与答辩形式。（Q-1 请假/加班已于 2026-10-26 决议暂缓）

---

## 阶段 1：项目骨架与开发环境

**目标：所有 `mvn compile` / `npm run build` 必须通过，且目录结构定型。**

| 步骤 | 内容 | 验收 |
|---|---|---|
| 1.1 | 创建 `server/` Maven 多模块：parent + `lz-common` + `lz-framework` + `lz-app` + 业务模块占位 | `mvn -q -DskipTests package` 成功 |
| 1.2 | 统一响应 `R`、错误码、异常、分页 VO、`GlobalExceptionHandler` | 单测覆盖响应结构 |
| 1.3 | 配置：多环境 `application.yml / -dev / -local(gitignored)`，Druid、MyBatis、JWT、OpenAPI | 启动日志无 WARN 级配置错误 |
| 1.4 | 健康检查接口 `GET /api/system/health` 与 OpenAPI UI 可访问 | curl 200 |
| 1.5 | Git 初始化 + `.gitignore`（排除 `application-local.yml`、node_modules、target、密钥文件） | `git status` 干净 |
| 1.6 | 前端 `web/`：Vite5 + Vue3 + Element Plus + Pinia + Router + Axios + ECharts，布局骨架 + 登录页 + 健康检查联调页 | `npm run build` 成功，页面能显示后端健康状态 |
| 1.7 | `README.md` 补齐本地启动步骤 | 按文档一键复现 |

**阶段 1 完成的可验证验收标准**
1. `cd server && mvn -DskipTests package` 退出码 0。
2. `java -jar lz-app/target/lz-app.jar` 能启动并输出 `Started Application`，访问 `/swagger-ui.html` 返回 200。
3. `cd web && npm install && npm run build` 成功，`dist/` 产生产物。
4. 前端页面点击"检查后端健康"能真实返回后端 JSON（证明前后端链路通）。
5. `git log` 至少有阶段提交，`git status` 无遗漏的大文件。

**阶段 1 实际执行结果（2026-10-26）**

| 验收项 | 命令 | 结果 |
|---|---|---|
| 后端构建 | `mvn -B -DskipTests package` | ✅ BUILD SUCCESS（10 个模块，耗时约 1m38s；第二次增量约 20s） |
| 后端启动 | `java -jar lz-app.jar --server.port=8088` | ✅ 启动成功；注：本机会话环境注入了 `SERVER__PORT=56043`，Spring 宽松绑定会覆盖默认 8080，故显式指定端口 |
| 健康检查 | `GET /api/system/health` | ✅ 200，返回真实状态：`database.connected=false`，message 为 `Access denied for user 'root'@'localhost'`（尚未提供口令） |
| 接口文档 | `GET /swagger-ui/index.html`、`/v3/api-docs` | ✅ 均 200 |
| 前端依赖 | `npm install` | ✅ added 86 packages |
| 前端构建 | `npm run build` | ✅ built in 8.06s，产物在 `web/dist/` |
| 前后端联调 | dev server 代理转发 `GET /api/system/health` | ✅ **2026-10-26 实测通过**：`curl http://localhost:5173/` → 200；`curl http://localhost:5173/api/system/health` → 经 Vite proxy 转发到 8080 后端并**返回真实 JSON**（含真实报错 `Access denied for user 'root'`），证明整条链路通而非页面伪造 |
| Git 初始化 | `git init` + `.gitignore` + 首次提交 | ✅ **已完成**：提交 `685f9b1`（64 文件）；`core.autocrlf=input`、`core.quotepath=false`；`git status` 干净，无 `target/`、`node_modules/`、`dist/`、`.tmp_extract/` 误入；3 份原始 docx 已归档 `docs/reference/` 并纳入版本管理 |
| 原始资料归档 | 3 份 docx 集中管理 | ✅ 已移入 `docs/reference/`（只读存档），附 `docs/reference/README.md` 说明三者优先级与图片提取结论 |
| 端口口径统一 | 后端 8080 / 前端 5173 | ✅ 已统一。新增 `scripts/start-backend.cmd`（清空 `SERVER__PORT`/`SERVER_PORT`/`PORT` 后显式传端口）；`vite.config.js` 代理目标支持 `LZ_API_TARGET` 覆盖 |

**过程中发现并修复的真实问题**
1. `characterEncoding=utf8mb4` 导致 `UnsupportedEncodingException`（MySQL Connector/J 要求 Java 字符集名）→ 改为 `UTF-8`，同步修正 README 与示例配置。
2. Druid 默认 `initial-size=5` 会在启动时建连接，数据库不可用时应用直接崩溃 → 改为懒加载并加 `fail-fast`，健康检查负责真实探测。
3. `max-wait=60000` 会让健康检查在数据库不可用时阻塞 60 秒 → 收敛到 5 秒。
4. `lz-common` 缺少 `jakarta.validation-api`（provided）→ 编译失败已修复。

---

## 阶段 2：基础业务与权限

1. 表：`sys_user/role/permission/*`、`org_*`、`hr_*`、`biz_place`（见 `docs/sql/schema.sql`）。
2. 认证：BCrypt 存密码 → JWT 签发/校验 → `AuthInterceptor` 注入 `AuthContext` → `@RequirePermission`。
3. 权限：角色-权限建模，系统内置权限码表，`PermissionChecker` 由 `lz-system` 实现（`lz-framework` 只定义接口，避免反向依赖）。
4. 员工/公司/部门/岗位/场所：CRUD + 停用 + 唯一性校验 + 分页查询 + 前端表格/表单/校验/反馈。
5. 每个模块同步产出 Testcontainers 不可用条件下的 MySQL 集成测试（本机真实库 + 事务回滚）。

**完成标志**：管理员登录 → 建公司/部门 → 建场所（含手动坐标）→ 建员工并绑定场所，全流程数据落库且重启后仍在。

---

## 阶段 3：核心考勤闭环（最高优先级）

1. 二维码：生成（placeId + nonce + expire + HMAC-SHA256 签名）→ PNG 输出 → 失效/过期 → 一次性标记。
2. H5 打卡页：移动端自适应，支持扫码入口与手动输入 payload 兜底，请求前置获取 `requestId` 做幂等。
3. 打卡接口：身份/签名/有效期/一次性/员工-场所关系/时间内重复 → 落库 `att_record` → 返回可读原因。
4. 查询：我的考勤（仅本人）、管理员考勤（多条件分页）。
5. 日志：`log_operation` + 失败原因落 `traceId`。

**完成标志**：管理员发布二维码 → 员工 H5 扫码 → 成功→落库；重复/过期/越权/非本场所四类失败均有明确提示。

---

## 阶段 4：统计与可视化（原"统计、审批与可视化"）

> **Q-1 已决议（2026-10-26）**：原第 4 项"请假/加班审批"**移出本阶段**，见 `docs/architecture.md` 第 7.1 节。本阶段只做统计与可视化。

1. `att_daily_stat` 汇总 + Spring Task 每日重算 + 手动重算接口（幂等）。
2. 统计接口：summary / trend / department / place-heat，全部来自 SQL 聚合，响应带 `dataSource`。
3. ECharts 看板：折线趋势、柱状对比、饼图构成、空数据空态。
4. ~~请假/加班：提交 → 待办 → 审批/驳回 → 状态机防重复审批 → 与统计联动~~ **⏸ 已暂缓（Q-1 决议）**。补做时的入口：`biz_application` / `biz_application_approval` 表已建；实现链路为 提交 → 待办列表 → 审批/驳回（驳回必填备注）→ 状态机防重复审批 Guard → 统计联动（联动口径需先与导师确认）。

---

## 阶段 5：AI 知识库与高级能力

1. 文档解析：PDF/Word/Excel/PPT/TXT/MD（PDFBox + POI），失败保留 `parse_message`。
2. 切分：固定窗口 + 重叠 + 章节元数据，落 `kb_chunk`。
3. 检索：MySQL 5.7 `FULLTEXT ... WITH PARSER ngram`，评分排序，命中低于阈值直接返回"未找到足够依据"。
4. 生成增强：`LlmClient` 接口 + `NoopLlmClient`（默认）+ OpenAI 兼容实现（`@ConditionalOnProperty` 需 Key）。
5. 权限：文档可见范围过滤必须作用于检索阶段（不是返回后再过滤）。
6. 人脸：`FaceRecognitionService` 接口 + `Noop`（mock 标注）+ ArcSoft 条件装配占位 + `docs/face-sdk-integration.md`。

---

## 阶段 6：基础设施与移动端扩展

1. Spring Task：过期二维码清理、日统计、任务执行日志与幂等（阶段 3/4/6 分批落地）。
2. Redis：开关化 `CacheService`（无 Redis 时降级本地/直查并记录 WARN），待本机安装后启用。
3. Nacos：保持"已配置未启用"，补齐本地 Nacos 2.x 启动脚本与启用说明。
4. 存储：`sys_file` 本地受控存储 + 鉴权下载 + 备份/恢复脚本。
5. 移动端：H5 响应式完善；uni-app 视时间评估，不承诺未完成的小程序。
6. 部署物：Dockerfile / compose / CI 工作流 / K8s 清单先产出，**实际运行待确认**。

---

## 阶段 7：测试、构建与验收

1. 单元测试：签名、校验器、状态机、切分器、检索评分、权限注解路径。
2. 接口测试：按 `docs/test-plan.md` 全量执行，脚本化（HTTP 脚本或 Postman 集合导出）。
3. 持久性：重启后数据一致；演示数据与真实数据可区分。
4. 构建：`mvn -DskipTests=false package`（测试失败即失败，不允许跳过测试）。
5. 验收清单 + 演示脚本（10 分钟走完核心链路）。

---

## 阶段 8：文档与答辩

产出与实际代码一致的需求规格/架构/数据库/接口/模块说明/测试报告/环境指南/部署说明/用户手册/已知问题/演示流程/答辩问答题库（重点：解释 CPT90 三条链路——员工建档、扫码打卡闭环、知识库检索；以及"为什么单体""人脸识别为什么是模拟"）。

---

## 依赖关系

```
阶段1 ──▶ 阶段2 ──▶ 阶段3 ──▶ 阶段4
                       │         │
                       └──▶ 阶段5 │
                                 └▶ 阶段6 ──▶ 阶段7 ──▶ 阶段8
```

低风险并行：阶段 5 的知识库模块与阶段 4 的统计/看板模块可并行推进（互不阻塞）。
