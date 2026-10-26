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
- 演示数据口令：`admin` / `manager01` = `Admin@123`；`emp01` ~ `emp03` = `Emp@123`（BCrypt 真实摘要，非明文）。

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
| MySQL | 5.7.19，端口 3306，服务 Running | ✅ | **root/gjc 已连通并完成初始化**；客户端路径 `D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe`（需绝对路径调用）。`ngram_token_size=2`、`innodb_large_prefix=ON`。不支持窗口函数 |
| Redis | 未安装 | ❌ | 相关能力标记为待接入；应用默认降级不依赖 Redis |
| Docker | CLI 29.1.3，**daemon 未启动** | ❌ | 容器化/CI 演示受阻 |
| 虹软 SDK / Face-Api 授权 | 无 | ❌ | 人脸识别走模拟适配层 |
| 大模型 API Key | 无 | ❌ | 知识库走 `retrieval-only` 模式 |
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
| E 请假/加班审批 | 7 | 0 | ⏸ **暂缓**（Q-1 于 2026-10-26 决议不做）；`biz_application`/`biz_application_approval` 表已作为结构预留，业务代码不实现 |
| F AI 知识库 | 11 | 0 | 未实现（先做真实检索） |
| G 人脸识别 | 7 | 0 | 5 条为替代方案待实现；2 条**受阻**（缺授权/设备） |
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
| BK-5 | 大模型 API Key 缺失 | 生成式回答不可用 | 不做伪造；默认 `retrieval-only` 模式 |

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
5. 下一步进入**阶段 5（AI 知识库与高级能力）**：文档解析 → 切分 → MySQL ngram 全文检索 → `NoopLlmClient` 降级检索模式 → 人脸模拟适配层。**需要你拍板两件事**：① 是否接受"检索-only 降级模式"（无大模型 Key 时）；② 虹软 SDK 授权是否确定拿不到（决定模块 G 走模拟适配层）。

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
