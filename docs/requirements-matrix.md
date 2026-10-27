# 需求矩阵（Requirements Matrix）

> 编号规则：`REQ-<模块>-<序号>`。
> **状态**：`必做` / `待确认` / `扩展`。
> **实现状态**：`未实现` / `进行中` / `已完成` / `待接入`（依赖外部条件）/ `受阻`。
> **依据来源**：`P<页码>` 指《北游JavaEE工程实践(4周).docx》页；`IDX` 指《项目总指令.docx》模块小节；`ANA` 指《项目分析.docx》推断（**仅作参考，不作为验收依据**）。

最后更新：2026-10-26（阶段 0）

---

## 模块 A：用户、员工与组织管理

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限要求 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-A-01 | 登录、登出、获取当前用户 | P6/11/15（H5 登陆页面、移动端查看考勤）、IDX-A1 | 必做 | login.vue | POST /api/auth/login、POST /api/auth/logout、GET /api/auth/profile | sys_user | 无需登录（除 profile） | TC-A-01 正确登录返回 token；TC-A-02 错误口令 401；TC-A-03 停用账号拒绝登录 | ✅ 已实现（阶段2） | Postman + 前端实操 |
| REQ-A-02 | 角色与权限管理（管理员/部门主管/普通员工可配置） | ANA（原始文档未逐条列出，属工程必要）、IDX-A2 | 必做 | role.vue | GET/POST/PUT /api/roles、GET /api/permissions | sys_role、sys_role_permission、sys_permission | 需 `system:role:*` | TC-A-04 越权访问返回 403（后端校验，非前端隐藏） | ✅ 已实现（阶段2） | 接口测试 + 越权用例 |
| REQ-A-03 | 员工信息新增/查询/编辑/停用 | P11（员工采集/员工信息采集）、P10（员工信息上传） | 必做 | employee.vue | GET/POST/PUT /api/employees、PUT /api/employees/{id}/status | hr_employee | 管理员/主管；普通员工仅本人 | TC-A-05 重复工号拒绝；TC-A-06 停用后不可打卡 | ✅ 已实现（阶段2） | 接口 + 页面演示 |
| REQ-A-04 | 公司/部门/岗位组织管理 | P11（各公司以及子公司办公场所登记）→ 组织为工程必要；IDX-A4 | 必做（公司层）/ 待确认（岗位层） | org.vue | GET/POST/PUT /api/companies、/api/departments、/api/positions | org_company、org_department、org_position | 管理员 | TC-A-07 删除含子部门的部门被拒绝 | ✅ 已实现（阶段2） | 接口测试 |
| REQ-A-05 | 员工与部门、办公场所绑定 | P11（员工信息采集、办公地点绑定） | 必做 | employee-bind.vue | POST /api/employees/{id}/bind-place | hr_employee_place | 管理员/主管 | TC-A-08 绑定后打卡归属正确场所 | ✅ 已实现（阶段2） | 端到端链路 |
| REQ-A-06 | 数据校验、重复检查与操作反馈 | ANA（隐含于"规范代码/项目质量"）、IDX-A6 | 必做 | 全局表单校验 | 全局异常处理器 | — | — | TC-A-09 非法邮箱/手机号返回明确字段错误 | ✅ 已实现（阶段2） | 接口测试 |
| REQ-A-07 | 受保护接口的后端鉴权 | IDX-A7（明确要求） | 必做 | — | AuthInterceptor + @RequirePermission | — | — | TC-A-10 无 token 401、无权限 403 | ✅ 已实现（阶段2） | 自动化测试 |

## 模块 B：办公场所管理

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-B-01 | 办公场所新增/查询/编辑/停用 | P11（办公场所：登记） | 必做 | place.vue | GET/POST/PUT /api/places、PUT /api/places/{id}/status | biz_place | 管理员 | TC-B-01 同名场所校验；TC-B-02 停用后不可作为打卡场所 | ✅ 已实现（阶段2） | 接口 + 页面 |
| REQ-B-02 | 公司及子公司办公场所登记 | P11（各公司以及子公司等办公场所登记） | 必做 | place.vue | 同上（company_id 关联） | biz_place.company_id | 管理员 | TC-B-03 子公司场所可独立维护 | ✅ 已实现（阶段2） | 页面演示 |
| REQ-B-03 | 公司 ID 码 / 业务识别码生成 | P9（第 9 天：办公室登记 公司 ID 码生成） | 必做 | place.vue | POST /api/places/{id}/code | biz_place.place_code | 管理员 | TC-B-04 生成的码全局唯一且可续期/吊销 | ✅ 已实现（阶段2） | 接口测试 |
| REQ-B-04 | 经纬度与第三方地图坐标采集 | P11（坐标采集：第三方地图坐标采集）、P9 | 必做 | PlaceManage.vue（地图选址抽屉 + 手动录入兜底） | GET /api/places/map-status（enabled/apiKey 下发） | biz_place(lng,lat) | 管理员 | TC-B-05 无 API Key 时状态显式返回 `enabled=false`；TC-B-06 手动坐标可保存并生效 | ✅ 已实现（阶段2手动录入；阶段F接入高德选址：搜索/取点/半径圈/GCJ-02→WGS84 自动换算） | 接口 + 界面标注 |
| REQ-B-05 | 场所重复/无效坐标/关联数据校验 | IDX-B7 | 必做 | — | PlaceValidator | — | 管理员 | TC-B-07 经纬度越界被拒绝 | ✅ 已实现（阶段2） | 接口测试 |

## 模块 C：二维码与 H5 考勤打卡（最高优先级核心链路）

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-C-01 | 动态生成携带核心参数的二维码 | P11（二维码：二维码动态生成，携带核心参数） | 必做 | qrcode-manage.vue | POST /api/qrcodes、GET /api/qrcodes/{id}/image | att_qrcode | 管理员 | TC-C-01 生成含 placeId/nonce/expire/sign | ✅ 已实现（阶段3） | 脚本验证 + 页面演示 |
| REQ-C-02 | 二维码签名与服务端校验 | IDX-C2（明确要求） | 必做 | — | QrSigner(HMAC-SHA256) | — | — | TC-C-02 篡改参数被拒；TC-C-03 伪造签名被拒 | ✅ 已实现（阶段3） | 脚本验证（QR_SIGN_INVALID） |
| REQ-C-03 | 有效期、失效与重复使用限制 | IDX-C3 | 必做 | 后台失效按钮 | POST /api/qrcodes/{id}/invalidate、定时任务 | att_qrcode(expires_at,used,status) | 管理员 | TC-C-04 过期码打卡失败；TC-C-05 一次性码二次使用被拒 | ✅ 已实现（阶段3） | 脚本验证（QR_EXPIRED / QR_USED） |
| REQ-C-04 | H5 扫码打卡页面 | P9（移动端 H5 页面开发）、P11（H5 终端页面，用户扫码一体化信息提交） | 必做 | h5/checkin.vue | — | — | 需登录 | TC-C-06 移动浏览器打开并完成提交 | ✅ 已实现（阶段3） | H5 页面演示（/h5/checkin） |
| REQ-C-05 | 打卡业务规则校验（身份/二维码状态/员工-场所关系） | IDX-C5 | 必做 | 结果反馈区 | CheckinValidator | — | — | TC-C-07 非本场所员工打卡被拒并返回原因 | ✅ 已实现（阶段3） | 脚本验证（PLACE_NOT_BOUND） |
| REQ-C-06 | 打卡记录落库（员工/类型/场所/时间/来源/结果） | P10（打卡记录上报、签到数据上传） | 必做 | — | POST /api/checkin | att_record | 登录用户 | TC-C-08 记录字段完整落库，重启后仍在 | ✅ 已实现（阶段3） | 脚本验证 + DB 校验 |
| REQ-C-07 | 防重放、防重复提交 | IDX-C7 | 必做 | 按钮幂等 | 幂等表/去重策略 | att_record_uniq / 幂等 key | — | TC-C-09 同一 nonce 二次提交拒绝 | ✅ 已实现（阶段3） | 脚本验证（幂等 requestId + 一次性码） |
| REQ-C-08 | 前端展示成功/失败及具体原因 | IDX-C8 | 必做 | h5 结果页 | ResultCode 枚举 | — | — | TC-C-10 每种失败原因有可读文案 | ✅ 已实现（阶段3） | 页面演示（data.reason 翻译表） |
| REQ-C-09 | 管理员查询打卡记录 | P11（概要：员工考勤实时数据报表） | 必做 | record-admin.vue | GET /api/admin/attendance/records | att_record | 管理员 | TC-C-11 多条件筛选结果正确 | ✅ 已实现（阶段3） | 脚本验证 + 页面演示 |
| REQ-C-10 | 员工查询本人考勤记录 | P6（移动应用：查看自己的考勤记录） | 必做 | record-mine.vue | GET /api/my/attendance/records | att_record | 登录用户（只能本人） | TC-C-12 越权查他人被拒 | ✅ 已实现（阶段3） | 脚本验证（40301 + dataScope） |
| REQ-C-11 | 业务日志 | IDX-C11 | 必做 | — | 操作/异常日志 | log_operation | — | TC-C-13 关键操作有日志落库 | ✅ 已实现（阶段3） | 日志抽查（log_operation，独立事务） |
| REQ-C-12 | 摄像头不可用时的二维码 content 输入替代方案 | IDX-C（浏览器/设备限制） | 必做 | h5 手动输入 | POST /api/checkin payload 支持 content | — | — | TC-C-14 手动粘贴 payload 可完成打卡 | ✅ 已实现（阶段3） | H5 手动输入入口（摄像头不可用时降级） |

## 模块 D：考勤记录与统计看板

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-D-01 | 考勤记录分页查询 | ANA（看板基础）、IDX-D1 | 必做 | 表格组件 | GET /api/attendance/records（分页） | att_record | 按角色过滤范围 | TC-D-01 分页参数正确 | 已实现（阶段 3） | 接口测试 |
| REQ-D-02 | 按员工/日期/部门/场所/状态筛选 | IDX-D2 | 必做 | 筛选区 | 同上 query 参数 | — | — | TC-D-02 组合筛选结果正确 | 已实现（阶段 3） | 接口测试 |
| REQ-D-03 | 打卡次数、出勤、缺勤、工时统计 | P11（员工考勤分析，员工工时分析） | 必做 | 统计卡 | GET /api/statistics/summary | 视图/聚合查询 | 管理员/主管 | TC-D-03 同一数据两次统计结果一致 | 已实现（阶段 4） | 与 DB 手工核对 |
| REQ-D-04 | 按日/周/月统计 | IDX-D4 | 必做 | 时间切换 | GET /api/statistics/trend | — | — | TC-D-04 跨月边界正确 | 已实现（阶段 4） | 接口测试 |
| REQ-D-05 | 趋势图（折线/柱状） | P16（场所热力图、近期趋势图）、P18 | 必做（课程"了解"层级） | ECharts | GET /api/statistics/trend | — | — | TC-D-05 空数据渲染不报错 | 已实现（阶段 4） | 页面演示 |
| REQ-D-06 | ECharts 数据看板 | P18（eCharts 数据看板） | 必做 | `views/statistics/Dashboard.vue` | — | — | — | TC-D-06 看板加载完成 | 已实现（阶段 4） | 演示 |
| REQ-D-07 | 场所热流趋势数据基础 | P11（分析场所热流趋势，高峰低峰值时区变化） | 待确认 | 场所时段折线（无地图 Key 时的降级形态） | GET /api/statistics/place-heat | — | 管理员 | TC-D-07 返回按时段分布的场所数据 | 已实现（阶段 4，时段数据形态） | 演示（真实数据） |
| REQ-D-08 | 区分真实统计与示例数据 | IDX-D8（明确要求） | 必做 | 数据标签 | 响应 `dataSource=real/sample/mixed` | — | — | TC-D-08 演示数据带 `sample=true` 标记 | 已实现（阶段 4） | 接口 + 界面标注 |
| REQ-D-09 | 空数据/异常/跨日/重复打卡处理 | IDX-D9 | 必做 | 空态 | 统计服务容错 | — | — | TC-D-09 无数据返回空集合而非报错 | 已实现（阶段 4） | 自动化测试 |

## 模块 E：请假与加班审批 —— ✅ 已实现（阶段 E）

> **决议变更**：Q-1（2026-10-26）原为"暂缓"，后在**阶段 E** 由项目负责人拍板补齐实现（"先从模块 E 请假/加班审批开始"）。
>
> **实现说明**：`biz_application` / `biz_application_approval` 两张预留表直接复用（**无需改表结构**）。新增 `lz-approval` 模块：申请提交、本人查询、审批人待办、审批（通过/驳回+意见）、撤回、状态机防重复审批，以及 E-07 的后端接入点 `ApplicationService.listApprovedInRange`（统计重算尚未消费，属"后端数据就绪、前端/统计消费待接"）。权限码 `application:submit` / `application:approve` / `application:list` 经幂等补丁 `docs/sql/patch-phaseE-approval.sql` 注册，角色绑定：ADMIN 全有、MANAGER 有 submit+approve、EMPLOYEE 仅有 submit；鉴权由 `@RequirePermission` 后端强制（非前端伪装）。
>
> **对需求总数的影响**：E 模块 6 条由"暂缓"转为"已实现"，当前**必做 38 条 / 待确认 8 条 / 扩展 7 条 / 受阻 2 条**，另 **已实现（含 E）** 计数随阶段推进累加。总数 61 不变。

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-E-01 | 提交请假/加班申请 | P6（移动应用：申请请假或加班审批） | 必做 | `views/approval/Submit.vue` | POST /api/applications | biz_application | application:submit | TC-E-01 结束早于开始被拒 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-02 | 查询个人申请记录 | P6 同上 | 必做 | Submit.vue「我的申请」 | GET /api/applications/mine | biz_application | application:submit（本人隔离） | TC-E-02 只能看到本人记录 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-03 | 管理员/授权审批人审批 | P6 同上 | 必做 | `views/approval/Approve.vue` | POST /api/applications/{id}/approve | biz_application_approval | application:approve | TC-E-03 非审批人审批被拒 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-04 | 通过/拒绝 + 审批备注 | IDX-E4 | 必做 | Approve.vue 审批弹窗 | 同上 | 同上 | application:approve | TC-E-04 拒绝必须填备注 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-05 | 记录状态、申请时间、审批信息 | IDX-E5 | 必做 | 列表列（状态/审批人/意见） | — | biz_application | — | TC-E-05 字段完整 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-06 | 防重复审批（状态机校验） | IDX-E6 | 必做 | — | 状态机 Guard | — | — | TC-E-06 已通过单据二次审批被拒 | 已实现（阶段 E） | `verify-phaseE.mjs` |
| REQ-E-07 | 审批结果与考勤统计联动 | IDX-E7 | 必做（后端数据就绪） | — | `ApplicationService.listApprovedInRange` | biz_application | — | TC-E-07 已通过单据含时长/状态/审批人，可被统计读取 | 已实现（阶段 E，统计消费待接） | `verify-phaseE.mjs` |

## 模块 F：AI Agent 与企业知识库

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-F-01 | 上传/导入文档（PDF/Word/Excel/PPT 分阶段） | P6（多模态知识解析与入库） | 必做（文本类）/ 扩展（OCR、音视频） | kb-upload.vue | POST /api/kb/documents | kb_document | 上传者/管理员 | TC-F-01 PDF/Word 解析成功并入库 | 已实现 · 2026-10-26 | 实操演示 + `verify-phase5.mjs` |
| REQ-F-02 | 解析、切分、索引与元数据管理 | P6（自动解析、智能切片与向量化） | 必做 | — | ChunkSplitter + FULLTEXT 索引 | kb_chunk | 管理员 | TC-F-02 切分长度与重叠符合配置 | 已实现 · 2026-10-26 | `verify-phase5.mjs` |
| REQ-F-03 | 可更新知识库（增删改重建） | P6（可更新的企业专属知识底座） | 必做 | 文档列表 | PUT/DELETE /api/kb/documents/{id} | kb_document | 管理员 | TC-F-03 删除后检索不再命中 | 已实现 · 2026-10-26 | 接口测试 |
| REQ-F-04 | 自然语言提问入口 | P6/项目名 AI Agent | 必做 | kb-qa.vue | POST /api/kb/ask | kb_qa_log | 登录用户 | TC-F-04 提问返回结构化结果 | 已实现 · 2026-10-26 | 演示 |
| REQ-F-05 | 基于文档内容的检索回答 + 来源引用 | IDX-F5 | 必做 | 答案区 + 来源列表 | Retriever（MySQL ngram 全文检索） | kb_chunk | 受文档可见范围限制 | TC-F-05 返回来源文档名与片段位置 | 已实现 · 2026-10-26 | 演示 + 自动化 |
| REQ-F-06 | 检索不足时明确告知，不编造答案 | IDX-F6（明确要求） | 必做 | 提示文案 | 命中阈值守卫 | — | — | TC-F-06 无命中时返回"未找到足够依据" | 已实现 · 2026-10-26 | 自动化测试 |
| REQ-F-07 | 清晰的 Agent / 检索 / 模型 / 工具调用边界 | IDX-F7 | 必做 | — | 分层：AgentOrchestrator → Retriever → LlmClient | — | — | TC-F-07 各层可独立单测 | 已实现 · 2026-10-26 | 分层可独立构造 |
| REQ-F-08 | 模型 API / 向量服务配置化 | IDX-F8 | 必做 | — | `lz.llm.*` 配置项 | — | — | TC-F-08 未配置 Key 时仍能启动 | 已实现 · 2026-10-26 | 冷启动验证 |
| REQ-F-09 | 无 Key 时可降级为检索模式并提示 | IDX-F9 | 必做 | 缺失提示条 | NoopLlmClient + 提示 | — | — | TC-F-09 响应含 `mode=retrieval-only` | 已实现 · 2026-10-26 | 自动化测试 |
| REQ-F-10 | 文档访问权限控制 | IDX-F10 | 必做 | — | 可见范围过滤（部门/角色/公开） | kb_document(visibility) | 受限文档非授权不可读 | TC-F-10 越权文档不出现在检索结果 | 已实现 · 2026-10-26 | 越权测试 |
| REQ-F-11 | 问答日志（脱敏） | IDX-F11 | 必做 | — | 异步落库 | kb_qa_log | 管理员 | TC-F-11 日志不记录完整答案正文与密钥 | 已实现 · 2026-10-26 | 日志抽查 |

## 模块 G：人脸识别与活体检测

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-G-01 | 人脸识别服务接口与适配层 | P13–15（人脸检测/跟踪/比对）→ 无 SDK 授权，走 IDX-G 替代方案 | 必做（适配层） | — | FaceRecognitionService 接口 | — | — | TC-G-01 接口可被替换实现 | 已实现 · 2026-10-26 | 切 provider 即换实现 |
| REQ-G-02 | 模拟实现（明确标注） | IDX-G2 | 必做 | 人脸打卡页带"模拟"徽章 | NoopFaceRecognitionService | face_feature(hash) | 员工本人 | TC-G-02 响应 `mock=true` | 已实现 · 2026-10-26 | 演示 + `verify-phase5.mjs` |
| REQ-G-03 | 界面与日志标注模拟 | IDX-G3 | 必做 | 徽章/警示条 | `[MOCK]` 日志前缀 | — | — | TC-G-03 日志含 MOCK 标记 | 已实现 · 2026-10-26 | 抽查 |
| REQ-G-04 | 真实 SDK 接入点与文档 | IDX-G4 | 必做（文档） | — | `lz.face.provider=arcsoft` 条件装配 | — | — | TC-G-04 切换配置后提示缺少授权文件而非崩溃 | 已实现 · 2026-10-26 | 配置验证 + `docs/face-sdk-integration.md` |
| REQ-G-05 | 生物特征数据最小化存储说明 | IDX-G7 | 必做 | 隐私说明 | `lz.face.store-image=false` | — | — | TC-G-05 默认不落原始人脸图 | 已实现 · 2026-10-26 | 配置 + 文档 |
| REQ-G-06 | 真实虹软 SDK 集成 | P14（虹软 SDK 使用） | **受阻** | — | ArcSoftFaceRecognitionService | — | — | — | **受阻**：缺 SDK 授权/AppId/设备 | 待外部条件 |
| REQ-G-07 | 活体检测 / 人证核验 / 人脸质量 | P14（活体检测、人证核验） | **受阻** | — | 同上 | — | — | — | **受阻**：同上，且不得用静态图冒充 | 待外部条件 |

## 模块 H：Redis、Nacos 与定时任务

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-H-01 | Redis 用于有明确价值的缓存 | P11（Redis：人员信息、足迹线路）、P12 课程 | 待确认（本机无 Redis） | — | CacheService（可降级） | — | — | TC-H-01 关闭时不报错；开启时命中缓存 | 未实现 | 开关验证 |
| REQ-H-02 | 过期策略与一致性处理 | IDX-H2 | 待确认 | — | TTL + 失效通知 | — | — | TC-H-02 更新员工后缓存同步失效 | 未实现 | 接口测试 |
| REQ-H-03 | 二维码/员工状态等临时数据设计 | IDX-H3 | 待确认 | — | QrTicketStore（内存实现先） | — | — | TC-H-03 重启后票据重建 | 未实现 | 重启验证 |
| REQ-H-04 | Nacos 注册/配置（可复现的本地启动方式） | P11（Nacos 分布式服务注册中心、配置中心） | **扩展（已配置未启用）** | — | NacosConditionalConfig | — | — | TC-H-04 开关关闭不影响启动；开启需本地 Nacos 2.x | 未启用 | 用户决策 D-3 |
| REQ-H-05 | Spring Task 定时任务（过期二维码清理/日统计） | P11（Spring Task 定时任务，定时采集器） | 必做 | — | @Scheduled task | att_qrcode、att_daily_stat | — | TC-H-05 任务执行后过期码状态变更 | 未实现 | 日志 + DB 校验 |
| REQ-H-06 | 任务幂等、失败处理与日志 | IDX-H6 | 必做 | — | 幂等锁 + 异常捕获 | job_exec_log | — | TC-H-06 重复触发不产生重复数据 | 未实现 | 自动化测试 |

## 模块 I：移动端与多场景支持

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-I-01 | H5 页面在移动浏览器完成打卡 | P9/P11 | 必做 | web H5 路由 | 同模块 C | — | — | TC-I-01 手机浏览器完成一次打卡 | 未实现 | 演示 |
| REQ-I-02 | 考勤/审批移动端自适应布局 | P6（移动应用支持） | 必做 | 响应式 CSS | — | — | — | TC-I-02 375px 宽度可用 | 未实现 | 演示 |
| REQ-I-03 | uni-app / 微信小程序适配 | P17（了解层级） | 扩展 | 独立工程（待定） | — | — | — | — | 未实现 | 未定 |
| REQ-I-04 | 远程办公、工地等场景规则说明并标注模拟 | P6（多场景适配）、IDX-I | 待确认 | 打卡来源字段 | checkin source + 场景规则 | att_record(source) | — | TC-I-04 场景规则可配置且结果标注 | 未实现 | 文档 + 演示 |

## 模块 J：存储、集成与部署

| 编号 | 需求名称 | 依据 | 优先级 | 前端 | 后端接口 | 数据表 | 权限 | 测试用例 | 实现状态 | 验收方式 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-J-01 | 受控的本地文件存储（云端待确认） | P6（云端存储与同步）→ 无云账号 | 必做（本地）/ 扩展（云端） | — | FileStorageService（本地实现） | sys_file | 鉴权后访问 | TC-J-01 未登录无法下载文件 | 未实现 | 越权测试 |
| REQ-J-02 | 数据库备份/恢复脚本 | IDX-J2 | 必做（本地脚本） | — | scripts/backup-db.* | — | — | TC-J-02 备份可还原 | 未实现 | 实操 |
| REQ-J-03 | HR/ERP/OA 集成适配层（不宣称真实对接） | P6（集成与扩展性强）→ 无外部接口 | 扩展/待确认 | — | ExternalSystemAdapter 接口 + 桩实现 | — | — | TC-J-03 未启用时返回 not-configured | 未实现 | 配置验证 |
| REQ-J-04 | Docker 容器化 | P11（基于 Docker 的虚拟化容器部署）、P19 | 待确认 | — | Dockerfile / compose | — | — | — | 未实现（配置文件待产出；本机 daemon 未启动） | 待确认 |
| REQ-J-05 | CI/CD 流水线 | P11（阿里云效）、P19（CICD 流水线） | 待确认 | — | CI 工作流文件 | — | — | — | 未实现 | 待确认 |
| REQ-J-06 | Kubernetes 集群发布 | P11/P20（了解层级） | 待确认 | — | K8s 清单 | — | — | — | 未实现 | 待确认 |

---

## 状态汇总（阶段 0 时点）

| 分类 | 数量 |
|---|---|
| 必做 | 44 |
| 待确认 | 8 |
| 扩展 | 7 |
| 受阻（缺外部条件） | 2 |
| 已实现 | 0 |

（统计口径见上表"优先级"列；REQ-G-01~05 属于无 SDK 条件下的替代方案，计入必做。）
