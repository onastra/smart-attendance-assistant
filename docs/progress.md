> 本文件必须反映**真实状态**。尚未实现 = `未实现`；依赖外部条件 = `待接入`/`受阻`；已写代码但未配置运行 = `已配置未启用`。禁止用模糊表述掩盖问题。

最后更新：2026-10-26

---

## 1. 当前阶段

**阶段 0：项目审计与实施计划 —— 已完成 ✅**
**阶段 1：项目骨架与开发环境 —— 骨架已成型（差 2 项收尾）**

### 阶段 1 已完成项（2026-10-26 实测）

| 项 | 内容 | 验证结果 |
|---|---|---|
| 1.1 | `server/` Maven 多模块：parent + lz-common + lz-framework + 8 个业务/启动模块 | `mvn -B -DskipTests package` = BUILD SUCCESS |
| 1.2 | 统一响应 `R`、错误码 `ResultCode`、`BusinessException`、`PageQuery/PageResult`、`GlobalExceptionHandler` | 随主构建通过 |
| 1.3 | 多环境配置 `application.yml` + `application-local.yml.example`，所有外部依赖开关化并在 `.gitignore` 保护本地私密文件 | 启动成功，无配置项缺失报错 |
| 1.4 | 认证骨架：`TokenService`(JJWT 0.12.6)、`AuthContext`、`AuthInterceptor`、`@RequireLogin/@RequirePermission`、`PermissionChecker` 契约（由 lz-system 后续实现） | 编译通过；尚未有需要鉴权的接口 |
| 1.5 | OpenAPI：`springdoc-openapi 2.6.0`，`/swagger-ui/index.html`、`/v3/api-docs` | 均返回 200 |
| 1.6 | 健康检查 `GET /api/system/health`：真实探测数据库连通性与 6 项外部依赖开关 | 返回真实结果，无伪造的"全绿" |
| 1.7 | 前端 `web/`：Vite5 + Vue3 + Element Plus + Pinia + Router + Axios，含登录页/后台布局/看板/依赖状态页/H5 占位页 | `npm install` + `npm run build` 成功（8.06s），`web/dist/` 已产出 |
| 1.8 | `.gitignore` | 已写入（含 `application-local.yml`、`data/`、`.tmp_extract/`、node_modules、target） |
| 1.9 | `docs/sql/schema.sql`（23 张表）+ `docs/sql/data.sql`（虚构员工演示数据，含真实 BCrypt 口令摘要） | 脚本已就绪，**尚未执行**（缺 MySQL 口令） |

### 阶段 1 未完成项

- **未做**：`git init` 与首次提交（等你确认是否把 3 份原始 docx 一并纳入版本管理）。
- **未做**：前后端联调人工确认（前端 dev server 打开看板 → 调后端 health），依赖后端运行 + 浏览器操作。
- **阻塞**：数据库初始化与集成测试（BK-1）。

### 阶段 1 发现并修复的真实问题

1. `characterEncoding=utf8mb4` → MySQL Connector/J 抛 `UnsupportedEncodingException`，已改为 `UTF-8`。
2. Druid 默认 `initial-size=5` 在启动即建连接，DB 不可用时应用崩溃 → 改为懒加载 + `fail-fast`。
3. Druid `max-wait=60000` 导致健康检查阻塞 60s → 收敛到 5s。
4. `lz-common` 缺 `jakarta.validation-api`（provided），编译失败已补。
5. 本机 Agent 会话环境注入了 `SERVER__PORT=56043`，会被 Spring 宽松绑定成 `server.port` → 启动端口需显式指定（已在 README 记录）。

已产出：
- `docs/architecture.md`：技术选型对照、架构图、模块边界、AI/人脸适配层设计、待确认事项 Q-1~Q-6
- `docs/requirements-matrix.md`：61 条需求（必做 44 / 待确认 8 / 扩展 7 / 受阻 2）
- `docs/database-design.md`：21 张表的字段-索引-关系设计
- `docs/api-design.md`：统一响应/分页/错误码/接口清单
- `docs/development-plan.md`：阶段 0–8 计划与依赖关系
- `docs/test-plan.md`：9 组共 60+ 测试用例
- `README.md`：环境与启动说明

## 2. 环境检查清单（2026-10-26 实测）

| 项 | 版本/状态 | 是否满足需求 | 备注 |
|---|---|---|---|
| JDK | 17.0.13（JAVA_HOME=E:\jdk-17） | ✅ | 满足 Spring Boot 3 要求 |
| Maven | 3.9.6 | ✅ | 本地仓库已有 Spring Boot 3.3.5 等依赖 |
| Node.js / npm | 22.22.2 / 10.9.7 | ✅ | Vite 5 兼容 |
| Git | 2.55.0 | ✅ | 尚未 `git init` |
| MySQL | 5.7.19，端口 3306 已监听，服务 Running | ⚠️ 部分 | **root 口令未提供**，尚未连通验证；不支持窗口函数 |
| Redis | 未安装 | ❌ | 相关能力标记为待接入；应用默认降级不依赖 Redis |
| Docker | CLI 29.1.3，**daemon 未启动** | ❌ | 容器化/CI 演示受阻 |
| 虹软 SDK / Face-Api 授权 | 无 | ❌ | 人脸识别走模拟适配层 |
| 大模型 API Key | 无 | ❌ | 知识库走 `retrieval-only` 模式 |
| 地图服务 Key | 无 | ❌ | 坐标支持手动录入，适配层显式上报未启用 |
| Nacos | 未安装 | ➖ | 按用户决策为"已配置未启用" |
| HR/ERP/OA | 无 | ➖ | 仅预留适配接口，不宣称对接 |

## 3. 需求完成情况（按模块）

| 模块 | 需求数 | 已完成 | 状态说明 |
|---|---|---|---|
| A 用户/员工/组织 | 7 | 0 | 未实现 |
| B 办公场所 | 5 | 0 | 未实现 |
| C 二维码与 H5 打卡 | 12 | 0 | 未实现（最高优先级） |
| D 统计看板 | 9 | 0 | 未实现 |
| E 请假/加班审批 | 7 | 0 | 未实现（是否必做待确认 Q-1） |
| F AI 知识库 | 11 | 0 | 未实现（先做真实检索） |
| G 人脸识别 | 7 | 0 | 5 条为替代方案待实现；2 条**受阻**（缺授权/设备） |
| H Redis/Nacos/Task | 6 | 0 | Nacos 为**已配置未启用**；Redis 待安装 |
| I 移动端 | 4 | 0 | H5 必做；uni-app 扩展 |
| J 存储/集成/部署 | 6 | 0 | 本地文件存储待实现；Docker/CI/K8s 待确认 |

## 4. 阻塞项

| 编号 | 阻塞项 | 影响 | 需要的支持 |
|---|---|---|---|
| BK-1 | MySQL 口令未提供 | 无法执行 schema.sql、无法跑集成测试与启动验证 | 请提供 root 口令或新建 `lianzhi` 账号及口令（推荐后者） |
| BK-2 | Redis 未安装 | REQ-H-01/02/03 无法在本地验证 | 安装 Redis for Windows 或授权我用其他方式；**不阻塞核心链路**（已开关化） |
| BK-3 | Docker daemon 未启动 | 容器化演示受阻 | 启动 Docker Desktop 或确认不做该演示 |
| BK-4 | 虹软 SDK 授权缺失 | 真实人脸识别/活体检测无法完成 | 向老师确认是否接受"标注的模拟适配层"（Q-3） |
| BK-5 | 大模型 API Key 缺失 | 生成式回答不可用 | 不做伪造；默认 `retrieval-only` 模式 |

## 5. 下一步（阶段 1 收尾 → 阶段 2）

1. **【需要你提供】MySQL 口令**（或新建一个 `lianzhi` 账号并授予 `lianzhi` 库权限），收到后执行 `docs/sql/schema.sql` 与 `data.sql`，完成 BK-1。
2. 确认是否 `git init`：我建议把 3 份原始 docx 排除出版本管理或单独放 `docs/reference/`（只读），避免误改。
3. 启动前端 dev server 做一次人工联调，补齐阶段 1 最后一项验收证据。
4. 随后进入阶段 2：用户/角色/权限 + 员工 + 组织 + 办公场所的完整 CRUD 与鉴权落地。

---

## 6. 变更记录

| 日期 | 阶段 | 内容 |
|---|---|---|
| 2026-10-26 | 0 | 完成三份原始资料精读（含原始方案 9 张内嵌图片）；产出阶段 0 全部文档；记录环境与阻塞项 |
| 2026-10-26 | 1 | 落地后端 Maven 多模块骨架（构建通过、健康检查与 OpenAPI 可用）与前端 Vue3+Element Plus 骨架（构建通过）；编写 23 张表的初始化脚本与演示数据；修复 5 个真实问题（详见上节） |
