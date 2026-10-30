> 本文件必须反映**真实状态**。尚未实现 = `未实现`；依赖外部条件 = `待接入`/`受阻`；已写代码但未配置运行 = `已配置未启用`。禁止用模糊表述掩盖问题。

最后更新：2026-10-26

---

## 1. 当前阶段

**阶段 0：项目审计与实施计划 —— 已完成 ✅**
**阶段 1：项目骨架与开发环境 —— 已完成 ✅（2026-10-26 数据库初始化后关闭）**
**阶段 2：基础业务与权限 —— 已完成 ✅（2026-10-26 12:40）**

> **2026-10-26 11:27 实测**：`GET /api/system/health` 返回 `database.connected: true`，阶段 1 全部 5 项验收标准通过。
>
> **2026-10-26 12:40 实测**：阶段 2 完成标志已达成 —— 管理员登录 → 建公司/部门/岗位 → 建场所（手动坐标）→ 建员工（工号自动生成）→ 绑定场所，全部数据落库可查。通过。

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

| 1.10 | Git 版本管理：`git init` + `core.autocrlf=input` + `core.quotepath=false`，首次提交 `685f9b1`（64 个文件） | ✅ `git log` 有提交；`git status` 干净，无 target/、node_modules/、dist/、`.tmp_extract/` 误入 |
| 1.11 | 原始资料归档：3 份 docx 移入 `docs/reference/` 只读存档并纳入版本管理，附 `docs/reference/README.md` 说明优先级 | ✅ 已归档（含 3.3MB 原始方案，可追溯原文） |
| 1.12 | Q-1 决议落地：模块 E《请假/加班审批》7 条需求转"暂缓"，已同步到需求矩阵/架构/计划三处 | ✅ 文档口径一致（必做 38 / 暂缓 6 / 待确认 8 / 扩展 7 / 受阻 2） |

| 1.13 | 端口口径统一：新增 `scripts/start-backend.cmd`（清除 `SERVER__PORT`/`SERVER_PORT`/`PORT` 环境变量后显式传 `--server.port`，默认 8080）；`vite.config.js` 代理目标支持 `LZ_API_TARGET` 覆盖 | ✅ 后端已按 8080 成功启动，`/swagger-ui/index.html`、`/v3/api-docs` 均 200 |
| 1.14 | 前后端联调验证：`curl http://localhost:5173/` → 200；`curl http://localhost:5173/api/system/health` → 经 Vite proxy 转发到 8080 后端并返回**真实 JSON** | ✅ 链路通。返回体 `database.connected=false`（真实报错 `Access denied for user 'root'@'localhost'`），**未做假绿** |

### 阶段 1 已解除阻塞（BK-1）

| 步骤 | 命令/操作 | 结果 |
|---|---|---|
| 定位 MySQL 客户端 | `D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe`（不在 PATH 的默认解析路径内） | ✅ 连接成功，`VERSION()=5.7.19` |
| 前置检查 | `SHOW VARIABLES LIKE 'ngram_token_size'` / `innodb_large_prefix` | ✅ `ngram_token_size=2`（中文二元分词可用）、`large_prefix=ON`（无 767 bytes 索引限制） |
| 建库 | `CREATE DATABASE lianzhi DEFAULT CHARSET utf8mb4` | ✅ 原库列表无 `lianzhi`，无覆盖风险 |
| 建表 | `mysql ... lianzhi < docs/sql/schema.sql` | ✅ **23 张表全部建立**，零错误 |
| 演示数据 | `mysql ... lianzhi < docs/sql/data.sql` | ✅ 零错误；sys_user 5 / sys_role 3 / sys_permission 25 / org_company 2 / org_department 3 / hr_employee 5 / biz_place 3 |
| 本地配置 | `server/lz-app/src/main/resources/application-local.yml` | ✅ 已创建，`.gitignore` 已确认保护（`git check-ignore` 命中第 22 条规则），**未入库** |
| JWT 密钥 | `secrets.token_bytes(48)` → Base64 | ✅ 已写入上述文件，非弱口令 |
| 重启验证 | `java -jar lz-app.jar --server.port=8080 --spring.profiles.active=local` | ✅ **`database.connected: true`** |

### 阶段 1 剩余说明

无未完成项。注意事项：
- `start-backend.cmd` 会自动检测 `application-local.yml` 并激活 `local` profile；该文件也可被重新修改而无需重新打包。
- **安全提醒**：当前使用 `root` 账号直连。演示/答辩环境建议改为专用账号（见下方 BK-1 的降级建议），目前风险仅限本机。
- 演示数据口令：`admin` / `manager01` = `Admin@123`；`LZ0001` ~ `LZ0003` = `Emp@123`（BCrypt 真实摘要，非明文）。

### 阶段 1 发现并修复的真实问题

1. `characterEncoding=utf8mb4` → MySQL Connector/J 抛 `UnsupportedEncodingException`，已改为 `UTF-8`。
2. Druid 默认 `initial-size=5` 在启动即建连接，DB 不可用时应用崩溃 → 改为懒加载 + `fail-fast`。
3. Druid `max-wait=60000` 导致健康检查阻塞 60s → 收敛到 5s。
4. `lz-common` 缺 `jakarta.validation-api`（provided），编译失败已补。
5. 本机 Agent 会话环境注入了 `SERVER__PORT=56043`，会被 Spring 宽松绑定成 `server.port` → 启动端口需显式指定（已在 README 记录）。

已产出：
- `docs/architecture.md`：技术选型对照、架构图、模块边界、AI/人脸适配层设计、待确认事项 Q-1~Q-6
- `docs/requirements-matrix.md`：61 条需求（**必做 38 / 暂缓 6 / 待确认 8 / 扩展 7 / 受阻 2**；2026-10-26 Q-1 决议后 E 模块 6 条转暂缓）
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
| Git | 2.55.0 | ✅ | 已 `git init`，首个提交 `685f9b1`（64 文件）；`autocrlf=input`、`quotepath=false` |
| MySQL | 5.7.19，端口 3306，服务 Running | ✅ | **root/&lt;本机 MySQL 口令&gt; 已连通并完成初始化**（口令只写在 gitignore 的本地配置里，此处不落真实值）；客户端路径 `&lt;你的 mysql 客户端路径&gt;/bin/mysql.exe`（需绝对路径调用）。`ngram_token_size=2`、`innodb_large_prefix=ON`。不支持窗口函数 |
| Redis | 未安装 | ❌ | 相关能力标记为待接入；应用默认降级不依赖 Redis |
| Docker | CLI 29.1.3，**daemon 未启动** | ❌ | 容器化/CI 演示受阻 |
| 虹软 SDK / Face-Api 授权 | 无 | ❌ | 人脸识别走模拟适配层 |
| 大模型 API Key | **有（硅基流动，2026-10-27）** | ✅ | 知识库 `llm-augmented` 生成增强 + bge 向量检索 |
| 地图服务 Key | 无 | ❌ | 坐标支持手动录入，适配层显式上报未启用 |
| Nacos | 未安装 | ➖ | 按用户决策为"已配置未启用" |
| HR/ERP/OA | 无 | ➖ | 仅预留适配接口，不宣称对接 |

## 2.1 阶段 2 完成记录（2026-10-26 实测）

### 交付内容

**后端**（`lz-system` + `lz-place`）
- 认证：`AuthService`（BCrypt 校验 + JWT 签发 + 登录 IP/时间回写）、`AuthController`（login / logout / profile）
- 权限：`PermissionCheckerImpl` 落地框架契约（三表联查取权限码）
- 账号：`UserService` / `UserController`（分页查询、新建、编辑、启停、重置口令、角色全量覆盖授权）
- 角色：`RoleService` / `RoleController`（CRUD + 权限分配 + 删除前引用检查）
- 员工：`EmployeeService` / `EmployeeController`（CRUD、工号唯一校验、自动生成工号、离职=停用不物理删除）
- 组织：`OrgService` / `OrgController`（公司/部门树/岗位；删除前检查子级与在职员工引用）
- 场所：`PlaceService` / `PlaceController`（CRUD、场所码生成、员工绑定/解绑、地图能力状态上报）

**前端**（`web/`）
- 登录页接真实接口，演示账号提示可见
- 路由守卫（未登录跳转 + 权限码入口控制 + 已登录访问登录页回首页）
- 布局菜单按权限动态渲染
- 五个管理页：员工管理、组织架构（公司/部门树/岗位三标签）、办公场所、账号管理、角色权限

### 实测验证结果

| 验证项 | 结果 |
|---|---|
| 登录 `admin / Admin@123` | ✅ 返回 token（194 字符）、44 项权限 |
| 错误口令 | ✅ `40101 用户名或密码错误`（不区分账号是否存在，防枚举） |
| 普通员工越权访问员工列表 | ✅ `40301 没有访问该资源的权限` |
| 完整建组织结构链路 | ✅ 公司 → 部门 → 岗位 → 员工 → 场所 → 绑定，全部成功 |
| 重复工号 | ✅ `40901 工号 LZ0001 已存在` |
| 删除有在职员工的部门 | ✅ `40901 该部门下仍有在职员工，请先调整员工归属` |
| 空姓名 | ✅ `40001 参数校验失败` |
| 地图能力状态 | ✅ 如实返回 `enabled=false, provider=none, coordinateSource=manual-input` |
| 持久性 | ✅ API 写入的公司/部门/岗位/员工/场所/绑定关系全部落库 |
| 前端构建 | ✅ built in 19.31s，五个管理页均产出 |
| 前后端联调 | ✅ 5173 → proxy → 8080 登录成功返回真实数据 |

### 阶段 2 发现并修复的真实问题

1. **`spring-security-crypto` 不在 Spring Boot 依赖管理范围内** —— Spring Boot 3.3.5 虽有 `spring-security.version=6.3.4`，但未纳管 `spring-security-crypto` 这个 artifact，必须显式锁版本。
2. **BCrypt 摘要格式不匹配导致登录 500** —— 库中摘要是纯 `$2a$10$...`，而 `DelegatingPasswordEncoder` 要求 `{bcrypt}` 前缀，找不到前缀时抛 `IllegalArgumentException` 而非返回 false。已统一改为标准 `BCryptPasswordEncoder`。
3. **Element Plus 图标未全局注册** —— `main.js` 只装了组件插件不含图标集合，而布局要用 `<component :is>` 动态渲染菜单图标，会导致渲染失败。已补充全局注册。
4. **MyBatis 分页取不到 offset** —— `PageQuery` 只有 `offset()` 方法没有 `getOffset()`，`#{offset}` 无法取值。已补 getter 形式访问器。
5. **权限码缺失会让功能必然不可用** —— 原 25 条权限码只有 `list/update`，缺 `create/delete`，标注 `@RequirePermission("...:create")` 必然被拒。已用幂等补丁脚本补至 44 条。

---

## 3. 需求完成情况（按模块）

| 模块 | 需求数 | 已完成 | 状态说明 |
|---|---|---|---|
| A 用户/员工/组织 | 7 | 7 | ✅ **已实现**（阶段 2）：JWT 登录/登出/身份查询、角色权限、员工 CRUD、公司/部门树/岗位、后端鉴权 |
| B 办公场所 | 5 | 5 | ✅ **已实现**（阶段 2）：场所 CRUD、场所码生成、手动坐标录入（地图能力如实标注未接入）、员工-场所绑定 |
| C 二维码与 H5 打卡 | 12 | 12 | ✅ **已实现**（阶段 3）：HMAC-SHA256 签名二维码（动态码/长期码/一次性码）、ZXing PNG 输出、H5 扫码+手动输入兜底、打卡校验链与落库、本人/管理员查询与数据范围收敛 |
| D 统计看板 | 9 | 9 | ✅ **已实现**（阶段 4）：`att_daily_stat` 幂等重算 + Spring Task 每日重算（写 `job_exec_log`）、summary/trend/department/place-heat 四个统计接口、ECharts 看板（统计卡/趋势/部门对比/出勤构成/场所时段）、响应带 `dataSource` 区分真实与演示数据 |
| E 请假/加班审批 | 7 | 7 | ✅ **已实现**（阶段 E）：新增 `lz-approval` 模块——申请提交 / 本人查询（员工隔离）/ 审批人待办 / 审批（通过·驳回+意见）/ 撤回 / 状态机防重复审批；权限码 `application:submit`/`approve`/`list` 经幂等补丁 `patch-phaseE-approval.sql` 注册（ADMIN 全有、MANAGER submit+approve、EMPLOYEE 仅 submit），`@RequirePermission` 后端强制；E-07 后端接入点 `listApprovedInRange` 已就绪（统计重算尚未消费，待接）。`scripts/verify-phaseE.mjs` 实测 **29 项断言全通过** |
| F AI 知识库 | 11 | 11 | ✅ **已实现**（阶段 5）：PDFBox/POI 解析 9 种格式、ChunkSplitter 窗口+重叠、MySQL ngram 全文检索（可见范围在 SQL 层过滤）、`LlmClient` 可插拔 + `NoopLlmClient` 默认降级 `retrieval-only`、来源引用、问答日志脱敏 |
| G 人脸识别 | 7 | 5 | ✅ **替代方案已实现**（阶段 5）：`FaceRecognitionService` 接口 + `NoopFaceRecognitionService`（`mock=true`、`[MOCK]` 日志）+ ArcSoft 条件装配占位 + `docs/face-sdk-integration.md`；另 2 条 **受阻**（真实 SDK 与活体，缺授权/设备） |
| H Redis/Nacos/Task | 6 | 0 | Nacos 为**已配置未启用**；Redis 待安装 |
| I 移动端 | 4 | 0 | H5 必做；uni-app 扩展 |
| J 存储/集成/部署 | 6 | 0 | 本地文件存储待实现；Docker/CI/K8s 待确认 |

## 4. 阻塞项

| 编号 | 阻塞项 | 影响 | 需要的支持 |
|---|---|---|---|
| ~~BK-1~~ | ~~MySQL 口令未提供~~ | — | ✅ **已解除（2026-10-26）**：23 张表建表 + 演示数据导入完成，`health` 返回 `connected=true`。⚠️ 遗留建议：`root` 仅限本机开发，答辩演示前建议改用专用账号 `lianzhi` |
| BK-2 | Redis 未安装 | REQ-H-01/02/03 无法在本地验证 | 安装 Redis for Windows 或授权我用其他方式；**不阻塞核心链路**（已开关化） |
| BK-3 | Docker daemon 未启动 | 容器化演示受阻 | 启动 Docker Desktop 或确认不做该演示 |
| BK-4 | 虹软 SDK 授权缺失 | 真实人脸识别/活体检测无法完成 | 向老师确认是否接受"标注的模拟适配层"（Q-3） |
| BK-5 | ~~大模型 API Key 缺失~~ | ~~生成式回答不可用~~ | **已解决（2026-10-27）**：接入硅基流动 Key，`llm-augmented` 上线；无 Key 降级路径保留并实测过 |

## 5. 下一步（阶段 1 收尾 → 阶段 2）

1. ✅ 阶段 1 已全部完成（数据库初始化 + BK-1 解除）。
2. ✅ **阶段 2 已完成**（2026-10-26）：认证鉴权 + 员工/组织/场所 CRUD + 前端五大管理页。
   - 完成标志已达成：管理员登录 → 建公司/部门 → 建场所（手动坐标）→ 建员工并绑定场所，数据落库。
3. ✅ **阶段 3 已完成**（2026-10-26）：二维码签发 → H5 打卡 → 落库 → 查询全链路打通，`scripts/verify-phase3.mjs` 实测 **30 项断言全部通过**。
   - 完成标志已达成：管理员发布二维码 → 员工 H5 打卡成功落库；重复提交/过期/伪造签名/非本场所/已作废/超出围栏六类失败均有明确原因码与文案。
4. ✅ **阶段 4 已完成**（2026-10-26）：`att_daily_stat` 幂等重算 + Spring Task 每日重算 + 四个统计接口 + ECharts 看板，`scripts/verify-phase4.mjs` 实测 **32 项断言全部通过**（含阶段 3 回归 30 项仍全绿）。
   - 按你的要求补录了 **14 天演示流水**（2026-10-12 ~ 10-25，94 条，全部 `source='SAMPLE'`），趋势图有 14 个数据点、按周 2 点、按月 1 点。
   - 完成标志已达成：重算 → 汇总 → 看板呈现；演示数据在接口 `dataSource=mixed` 与界面徽标两处如实标注。
   - 一处**如实提示**：缺勤 11 人天中有 10 人天来自阶段 2 创建的验证账号「验证员工张三」（在职但从未打卡）。若希望统计更干净，可将其置为离职后重算（`UPDATE hr_employee SET status=0 WHERE emp_no='EMP202610261574';`）。
5. ✅ **阶段 5 已完成**（2026-10-26）：`scripts/verify-phase5.mjs` 实测 **46 项断言全部通过**，阶段 3（30 项）、阶段 4（32 项）回归仍全绿。
   - 两项待拍板的问题已于 2026-10-26 由你确认：① 接受无大模型 Key 时降级 `retrieval-only` 且不伪造回答；② 虹软 SDK 授权当前拿不到，模块 G 走模拟适配层。
   - **模块 F**：真实生成了 PDF/DOCX/XLSX/PPTX/MD 五份样例文档（`docs/samples/kb/`，由 `scripts/gen-sample-docs/SampleDocGenerator.java` 用 POI/PDFBox 生成，不是手工伪造的二进制）跑通"解析 → 切片 → 检索 → 问答 → 来源引用"全链路；未命中时返回"未找到足够依据"而不是编造。
   - **模块 G**：录入/核验闭环可用，但每一层都标注 `mock=true`，考勤仍以二维码打卡为准；真实 SDK 改造清单已写入 `docs/face-sdk-integration.md`。
   - BK-4 / BK-5 均按"明确标注 + 保留接入点"处理，不做任何伪造。

---

## 6. 变更记录

| 日期 | 阶段 | 内容 |
|---|---|---|
| 2026-10-26 | 0 | 完成三份原始资料精读（含原始方案 9 张内嵌图片）；产出阶段 0 全部文档；记录环境与阻塞项 |
| 2026-10-26 | 1 | 落地后端 Maven 多模块骨架（构建通过、健康检查与 OpenAPI 可用）与前端 Vue3+Element Plus 骨架（构建通过）；编写 23 张表的初始化脚本与演示数据；修复 5 个真实问题（详见上节） |
| 2026-10-26 | 1 | `git init` 并完成首次提交 `685f9b1`（64 文件）；3 份原始 docx 归档至 `docs/reference/` 只读存档并附 `README.md`；同步 Q-1 决议（模块 E 请假/加班审批转暂缓）至需求矩阵、架构文档、开发计划三处 |
| 2026-10-26 | 1 | **阶段 1 关闭**：收到 MySQL 凭据后完成建库（`lianzhi`）、执行 `schema.sql`（23 表）+ `data.sql`（无报错）、创建 gitignore 保护的 `application-local.yml`（含强 JWT 密钥）、重新打包并以 `local` profile 重启；`GET /api/system/health` 实测 **`database.connected=true`**，BK-1 解除。5 项验收标准全部通过 |
| 2026-10-26 | 2 | **阶段 2 完成**：权限码 25→44（新增幂等补丁脚本 `patch-phase2-permission.sql`）；实现认证鉴权、PermissionChecker、账号/角色/员工/组织/场所五组 CRUD；前端登录页 + 路由守卫 + 五个管理页；修复 5 个真实问题（BCrypt 摘要格式、图标未注册、分页取值、权限码缺失等）；端到端实测通过，数据全部落库 |
| 2026-10-26 | 3 | **阶段 3 完成**：先与你确认四项口径（考勤规则全局可配、动态码+长期码、摄像头+手动输入兜底、围栏可选校验）后开工。补 `att_qrcode.qr_type` 字段与权限码（44→46，含新增 `attendance:qrcode:invalidate`、`attendance:record:admin`）；实现二维码签发/HMAC-SHA256 签名/ZXing PNG、10 步打卡校验链、本人与管理员查询（数据范围 ALL/DEPT/SELF）、H5 打卡页与三个后台页面；新增 `scripts/verify-phase3.mjs` 端到端脚本，30 项断言全通过。修复 2 个真实缺陷：管理端考勤查询与普通员工共用权限码（已拆分）、二维码 PNG 接口需带 token（改为取二进制转临时 URL） |
| 2026-10-26 | 4 | **阶段 4 完成**：按你的要求补录 14 天演示流水（`scripts/seed-sample-attendance.mjs`，94 条，全部 `source='SAMPLE'` 且 request_id 带 `SAMPLE-` 前缀可一键清除）。幂等补丁 `patch-phase4-statistics.sql`：补 `att_daily_stat.checkin_count` 列、纳管 `att_record.source` 的 SAMPLE 取值、新增 `statistics:rebuild` 权限码（47 条，仅 ADMIN）。实现日汇总重算（判定 REST/ABSENT/LATE/EARLY/NORMAL，唯一键 upsert 幂等）、Spring Task 每日 01:30 重算并写 `job_exec_log`、四个统计接口（均带 `dataSource`）、ECharts 看板（统计卡 + 趋势 + 部门对比 + 出勤构成 + 场所时段）。新增 `scripts/verify-phase4.mjs`，32 项断言全通过；阶段 3 回归 30 项仍全绿。修复 1 个真实缺陷：`ON DUPLICATE KEY UPDATE` 返回值三态（1/2/0）导致"影响行数"在两次重算间不一致（168 → 90），改为以覆盖员工数为准 |
| 2026-10-26 | 5 | **阶段 5 完成**：先由你确认两项口径（接受无 Key 时降级 `retrieval-only`、虹软授权拿不到故模块 G 走模拟适配层）。**模块 F**：引入 PDFBox 2.0.30 + POI 5.2.5，实现 9 种格式的解析器、ChunkSplitter（窗口 300 + 重叠 60 + 短块合并）、MySQL ngram 全文检索（可见范围过滤写在 WHERE 里而非返回后过滤）、`LlmClient` 接口 + `NoopLlmClient` 默认 + OpenAI 兼容实现（`ConditionalOnProperty`，缺 Key 只降级不崩溃）、`AgentOrchestrator`（检索无命中即返回"未找到足够依据"，绝不交给模型编造）、问答日志脱敏。**模块 G**：`FaceRecognitionService` 接口 + `NoopFaceRecognitionService`（图片指纹哈希比对，`mock=true`、`[MOCK]` 日志）+ `ArcSoftFaceRecognitionService` 条件装配（缺 SDK 时明确报错而非崩溃）+ `docs/face-sdk-integration.md`。幂等补丁 `patch-phase5-knowledge.sql`：权限码 47→50（新增 `kb:doc:delete`/`kb:doc:rebuild`/`kb:qa:log`，下放 `face:enroll` 给员工）、`kb_document.content_hash` 加索引。新增 `scripts/verify-phase5.mjs`，46 项断言全通过；阶段 3（30 项）、阶段 4（32 项）回归仍全绿。发现并记录 1 个环境特性：MySQL 5.7 InnoDB 全文索引对刚插入的行有极短缓存合并时延，连续上传后立刻检索偶发漏命中，重跑即恢复 |
| 2026-10-26 | 6 | **阶段 6（基础设施部分）完成**：① Redis 已安装并注册为 Windows 服务 `Redis-Lianzhi`（`D:/tools/redis`，127.0.0.1:6379，口令 <见 application-local.yml>，禁用 FLUSHALL/FLUSHDB，开机自启），`lz.cache.enabled` 打开后实测缓存生效（`cached=true`、TTL 60s、命中率可观测）；② 开关化 `CacheService`（`Noop` / `Redis` + 连续失败 3 次熔断 30s）接入统计看板与场所详情，缓存键带数据范围防串权；③ 新增过期二维码清理任务 `QrcodeExpireTask`（每 5 分钟，只改状态不物理删除，写 `job_exec_log`）；④ 部署物产出并**真跑**：Docker Compose 四容器全 healthy 且业务链路通（18081 前端 / 18080 后端）；K8s 清单部署到 kind 集群（`lianzhi`，v1.31.0）四 Pod 全 `1/1 Running`，`daily-rebuild` 14 天 70 行、summary 正常；⑤ CI 用 act 本地实跑，backend（BUILD SUCCESS）与 frontend（vite build）两个 job 均 ✅ 通过。修复 5 个真实缺陷：`commons-pool2` 缺失致启动失败、重算后统计缓存未失效、容器初始化 SQL 顺序颠倒致主键冲突、K8s 中 `$(VAR)` 未展开致 Redis 口令为空与探针 WRONGPASS、act 无 Maven/无 token |

| 2026-10-26 | 6 | **阶段 6 剩余三项完成（移动端与存储）**：① **文件受控存储与鉴权下载**：新建 `lz-file` 模块（`SysFile` / `FileMapper` / `LocalFileStore` / `FileService` / `FileController`），下载只接受 id 不接受路径、解析后二次校验不越界、响应不带存储路径；鉴权口径与文档三档可见范围对齐（PUBLIC 全员 / DEPT 同部门 / PRIVATE 仅上传人，另加 `file:admin`），越权下载写 `log_operation` 留痕；知识库文档列表新增"下载原文"入口。② **H5 响应式**：`index.html` 补 `viewport-fit=cover` 并放开缩放；两个 H5 页补安全区（刘海/Home 条）、触控目标 ≥44px、输入字号 ≥16px（防 iOS 聚焦缩放）、窄屏与横屏适配、取景框按视口自适应、定位失败可重试。③ **uni-app 评估**：产出 `docs/uniapp-evaluation.md`，结论为"不产出小程序"——ElementPlus / html5-qrcode / vue-router / localStorage 四项在小程序端全部不可用，前端等同重写（约 5 人日），且缺企业主体 AppID 与备案域名这两个硬前置。补丁 `patch-phase6-file.sql`：权限码 50→54（`file:download`/`upload`/`delete`/`admin`）、`sys_file` 增 `owner_dept_id` 列、并回填存量文件的可见范围。新增 `scripts/verify-phase6-file.mjs`，20 项断言全通过；阶段 5（46 项）、阶段 4（29 项）回归全绿。修复 5 个真实缺陷：文件可见范围被硬编码为 PRIVATE（DEPT 文档反而下不了原文）、`DocumentView` 漏带 `fileId`（前端拿不到下载入口）、中文文件名导致 `Content-Disposition` 整头被丢弃、EMPLOYEE 能上传却不能删自己的文件、人脸 H5 页登录传参错误（`login()` 收对象却传了两个字符串，登录必然失败） |
| 2026-10-26 | 7 | **阶段 7 进行中**：先按用户拍板清理演示数据（测试员工「验证员工张三」置为离职后重算 2026-10-12~10-25，缺勤 11 人天 → 1 人天，出勤率 97.8%，94 条 SAMPLE 演示流水保留）。单元测试从 **0 个**补到 **59 个**并全部通过（`mvn -B test` BUILD SUCCESS）：TokenServiceTest 9（过期/篡改/换密钥/短密钥四类必须拒绝）、QrSignerTest 5 + QrPayloadTest 6（防伪造二维码）、GeoDistanceUtilTest 6（围栏距离锚点校验）、StatisticsServiceTest 11（**缓存键必须含数据范围**，防部门主管命中管理员全量缓存；演示数据如实标注 mixed/sample/real）、ChunkSplitterTest 10（窗口/重叠/短块合并/索引连续/不丢字）、FileServiceTest 12（三档可见范围、DEPT 缺部门时收紧为仅上传人、file:admin 豁免）。用户已拍板两项：容器镜像**不重建**（C 盘仅 11GB，文档如实注明镜像为阶段 6 中途版本）、阶段 8 文档**先出 Markdown**待学校模板 |
| 2026-10-26 | 6 | **Docker 数据盘迁至 D 盘 + 镜像重建 + 部署缺陷修复**：定位到 C 盘告急主因是 `%LOCALAPPDATA%\Docker\wsl\disk\docker_data.vhdx` 达 **14 GB**（8.1GB 镜像 + 3.7GB 数据卷 + 2.5GB 构建缓存），用户经 Desktop 设置的 Disk image location 迁到 `D:\Docker\wsl\disk`，**C 盘可用 11GB → 24GB，19 个镜像零丢失**。借此机会重建了镜像（含阶段 6 文件模块），并用 `down -v` 重建数据卷以保证容器内 schema 与代码一致（`sys_file.owner_dept_id` 列、54 条权限码均已就位）。**修复一个真实部署缺陷**：`POST /api/statistics/daily-rebuild` 不带日期参数时只重算当天，而演示流水落在过去 14 天，导致首次部署后 `att_daily_stat` 为空、看板全 0（实测 99 条流水 / 0 条汇总），现场会被读成"统计模块没做"；改为自动按"最早一条打卡流水 ~ 今天"推导区间（实测自动推出 2026-10-12~10-26 共 15 天 75 行，概览 70 人天 / 出勤率 88%），并补 `StatisticsControllerTest` 5 项。容器验证：四容器 healthy、文件受控上传下载一致、Content-Disposition 正确、列表不泄露存储路径、**容器删掉重建后文件仍完整（app-files 卷持久化生效）**；回归 阶段 3（30）/ 4（32）/ 6（20）全绿，单元测试 64 项全通过 |
| 2026-10-26 | 7 | **阶段 7.2 完成：全量回归入口**。新增 `scripts/verify-all.mjs` + 根目录 `package.json` scripts，
一条 `npm run verify` 串行跑 Maven 单元测试 + 四套接口回归，最后按套件汇总、任一失败非 0 退出。
**实测 192 项全通过**（单元 64 + 接口 128）。过程中修了自己脚本的两个错：
健康检查返回的是扁平结构没有 `data` 包裹（前置判断踩空）；Maven 结果只取最后一行会把 64 项少报成 10 项
（改为累加各模块 Results 区块、并排除带 `Time elapsed` 的逐类行）。
**7.4 完成**：产出 `docs/acceptance.md` —— 七个模块的验收清单（每项含判定标准与实测结果）+ **十分钟演示脚本**
（按 CPT90 三条链路编排：员工建档 → 扫码打卡闭环 → 知识库检索，每步给出操作、预期画面与讲解词，
另附"演示时应主动交代的三件事"与现场失败应对表） |
| 2026-10-26 | 8 | **阶段 8 文档产出（按决议先出 Markdown，学校模板待定）**：
`docs/test-report.md`（三层测试策略、64 项单元测试各自的守护点、128 项接口回归、容器与部署验证、
9 个发现并修复的真实缺陷、测试覆盖的诚实边界）、
`docs/user-manual.md`（管理员/主管/员工三视角操作说明，含二维码三类、打卡六类失败提示、知识库可见范围）、
`docs/known-issues.md`（受阻能力 5 项、暂缓功能 1 项、架构与环境限制 4 项、测试边界 4 项、演示数据可区分可清除）、
`docs/defense-qa.md`（答辩题库：架构选型 / 核心三问 / 工程质量 / 安全 / 部署 / 收尾 / 现场应急共 20+ 题，
每题给出可直接口述的回答要点）。
同步修正 **README 第 8 节长期停留在阶段 1** 的文档漂移问题（改为阶段总表 + 回归门禁命令），
并在文档导航补齐上述四份文档 |
| 2026-10-27 | E | **阶段 E（模块 E 补做）完成**：用户拍板解除 Q-1 暂缓，"先从模块 E 请假/加班审批开始"。复用 `biz_application`/`biz_application_approval` 两张预留表（**未改表结构**），新增 `lz-approval` 模块：`ApplicationService`（提交校验 end>start、reason≥2 字、employeeId 非空；状态机 SUBMITTED→APPROVED/REJECTED/CANCELLED，终态不可重复审批；撤回仅本人+SUBMITTED）、`ApplicationController`（@RequirePermission 后端强制 `application:submit`/`approve`/`list`）、`ApplicationMapper.xml`（LEFT JOIN hr_employee 带出员工/审批人姓名，子查询带出 lastAction/lastComment）。前端 `web/src/views/approval/Submit.vue`（提交 + 我的申请）+ `Approve.vue`（待我审批 / 全部记录，驳回强制填意见）、`web/src/api/approval.js`、`router/index.js` 两条路由（权限过滤自动生成菜单）。幂等补丁 `docs/sql/patch-phaseE-approval.sql`：注册 3 个权限码、角色绑定、验证 SELECT。新增 `scripts/verify-phaseE.mjs`，**29 项断言全通过**（覆盖 REQ-E-01~07，含越权 40301、参数 40001、状态机 40901）。需如实说明：E-07 仅完成后端数据接入点，统计重算尚未调用它，属"后端就绪、消费待接" |
| 2026-10-27 | F | **阶段 F（地图选址）完成**：用户已申请高德开放平台 Key，按"管理端地图选址"范围接入（H5 端不做地图展示，浏览器原生定位 + 围栏校验已闭环）。后端：`mapStatus()` 扩展为配置后下发 `apiKey`/`securityJsCode`（前端源码零硬编码 Key，唯一来源是 `GET /api/places/map-status`；**占位符值不算已配置**，防下发假 Key 假装已接入）；`application.yml` 补 `lz.map.security-code` 骨架，example 模板补齐 `lz.map` 段。前端：新增 `coordTransform.js`（WGS84↔GCJ-02 纯函数，**库内统一存 WGS84** 与 H5 浏览器定位一致、围栏校验零改动，往返误差 < 1e-5 度≈1 米）与 `amap.js`（JS API 2.0 动态加载器）；`PlaceManage.vue` 地图选址抽屉（POI 搜索 / 点击与拖拽取点 / 打卡半径蓝色圆圈实时可视化 / 确认后自动换算回填；加载失败如实报错、保留手动录入兜底；不能用 destroy-on-close——SDK 实例持有容器 DOM 引用，销毁重开会白屏）。新增 `verify-phaseF.mjs` **20 项断言**（坐标数值 / map-status 未配置与已配置双形态诚实上报 / web 源码 grep 零硬编码 Key / 模板完整性），纳入 `verify-all`，**全量回归 241 项通过**（64+30+32+46+20+29+20）。**待用户**：把真实 Key/安全密钥填入 `application-local.yml` 的 `lz.map` 段并重启后端，`map-status` 即翻转为 `enabled=true`；建议在高德控制台为 Key 配置域名白名单（开发期 localhost:5173） |
| 2026-10-27 | F | **阶段 F（地图选址）完成**：用户已申请高德开放平台 Key，按"管理端地图选址"范围接入（H5 端不做地图展示，浏览器原生定位 + 围栏校验已闭环）。后端：`mapStatus()` 扩展为配置后下发 `apiKey`/`securityJsCode`（前端源码零硬编码 Key，唯一来源是 `GET /api/places/map-status`；**占位符值不算已配置**，防下发假 Key 假装已接入）；`application.yml` 补 `lz.map.security-code` 骨架，example 模板补齐 `lz.map` 段。前端：新增 `coordTransform.js`（WGS84↔GCJ-02 纯函数，**库内统一存 WGS84** 与 H5 浏览器定位一致、围栏校验零改动，往返误差 < 1e-5 度≈1 米）与 `amap.js`（JS API 2.0 动态加载器）；`PlaceManage.vue` 地图选址抽屉（POI 搜索 / 点击与拖拽取点 / 打卡半径蓝色圆圈实时可视化 / 确认后自动换算回填；加载失败如实报错、保留手动录入兜底；不能用 destroy-on-close——SDK 实例持有容器 DOM 引用，销毁重开会白屏）。新增 `verify-phaseF.mjs` **20 项断言**（坐标数值 / map-status 未配置与已配置双形态诚实上报 / web 源码 grep 零硬编码 Key / 模板完整性），纳入 `verify-all`，**全量回归 241 项通过**（64+30+32+46+20+29+20）。**待用户**：把真实 Key/安全密钥填入 `application-local.yml` 的 `lz.map` 段并重启后端，`map-status` 即翻转为 `enabled=true`；建议在高德控制台为 Key 配置域名白名单（开发期 localhost:5173） |
| 2026-10-27 | I | **REQ-I-03 微信小程序端完成（原生，非 uni-app）**：新增独立工程 `miniprogram/`（微信开发者工具直接打开，`urlCheck=false` 以放行本地 HTTP 后端）。范围对齐需求书"移动应用支持"：员工随时随地查看考勤记录、申请请假/加班审批，另加扫码打卡（wx.scanCode → 幂等请求号 → `/api/checkin`，校验链与 H5 完全一致，定位失败按"未上报坐标"降级不阻断）。页面 7 个：login（演示账号一键填充）/ index（入口 + 最近打卡）/ checkin / records（分页+下拉刷新+结果筛选）/ apply（LEAVE/OVERTIME，开始结束拆日期+时间两段 picker）/ applications（我的申请可撤回 + 待我审批可驳回，驳回强制填意见，无 `application:approve` 权限时标签页自动隐藏）/ profile（权限码数、员工档案绑定状态、后端地址、退出）。统一 `utils/request.js`（Bearer Token、code==0 判定、401→清登录态跳登录页）。**接口契约 100% 复用现有后端，后端零改动**；已按真实后端做端到端联调：审批 8 项（提交→mine 可见→todo 可见→员工越权 40301→空意见驳回 40001→带意见驳回→终态再审批 40901→撤回 CANCELLED）+ 打卡 4 项（request-id→打卡成功→同请求号幂等拦截→我的考勤可见）全部通过，测试用长期码已作废。JSON 结构与页面完整性脚本校验通过；模拟器运行验证待微信开发者工具打开 `miniprogram/` 后进行 |
| 2026-10-27 | 数据库 | **数据库从 MySQL 5.7 迁移到 PostgreSQL 17（Supabase 托管，首尔区）**。新增 `docs/sql/postgresql/schema.sql`（patch-phase2~6+phaseE 全部内联为初始结构：qr_type、checkin_count、embedding、owner_dept_id、审批/文件权限码；ngram 全文索引 → pg_trgm GIN 索引；ON UPDATE CURRENT_TIMESTAMP → set_updated_at 触发器；sources 用 TEXT）与 `data.sql`（INSERT IGNORE → ON CONFLICT DO NOTHING；显式 id 后必须 setval 同步序列，否则补丁插入会撞主键）。代码侧：驱动 mysql-connector-j → postgresql 42.7.4；9 个 Mapper 的 `LIMIT ?,?` → `LIMIT ? OFFSET ?`；`TIMESTAMPDIFF(SECOND,...)` → `EXTRACT(EPOCH FROM ...)`；`IFNULL/DATE_FORMAT/HOUR/YEAR/WEEK/DATE_ADD` → PG 等价写法；String 日期参数与 date/timestamp 列比较显式 CAST；知识库检索 `MATCH...AGAINST` → `word_similarity`（minScore 语义对齐 0~1 分值），LIKE → ILIKE；`INSERT IGNORE` → `ON CONFLICT DO NOTHING`；日汇总 upsert → `ON CONFLICT (employee_id, stat_date) DO UPDATE`；Druid 去掉 wall 过滤器（1.2.23 的 PG 方言解析 ON CONFLICT/::cast 误报）。回归脚本 verify-phase3/5 增加清理回退通道：LZ_MYSQL_BIN 优先，`LZ_PG_HOST/LZ_PG_USER/LZ_PG_PWD` 环境变量时走 psycopg2。14 天 SAMPLE 演示流水经 `seed-sample-attendance.mjs --dry-run` 生成后直接灌入 PG（纯 INSERT 语法两库通用）。**验证：接口回归 175 项（6 套件）+ Maven 单元测试全通过**；本地直连 db.xxx.supabase.co 的 IPv6 SSL 被网络中间层重置，改走 Session pooler（IPv4）。演示数据窗口仍为硬编码 2026-10-12~10-25 |
