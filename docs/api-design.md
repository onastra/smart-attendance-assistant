> 服务根路径：`http://localhost:8080`，统一前缀 `/api`。
> 接口文档：`springdoc-openapi` 自动生成，启动后访问 `http://localhost:8080/swagger-ui.html`（`/v3/api-docs` 为 JSON）。
> 本文档为**阶段 0 设计稿**，实现过程中以 OpenAPI 自动生成的为准，二者冲突时同步更新本文档。

---

## 1. 统一约定

### 1.1 响应结构

```jsonc
{
  "code": 0,               // 0 表示成功，非 0 见错误码表
  "message": "OK",
  "data": { },             // 业务数据；失败时为 null
  "traceId": "8f3c...",    // 链路 ID，日志可追踪
  "timestamp": 1761460000000
}
```

### 1.2 分页结构

统一入参：`pageNum`（默认 1）、`pageSize`（默认 10，上限 100）。统一出参：

```jsonc
{
  "code": 0, "message": "OK",
  "data": {
    "pageNum": 1, "pageSize": 10, "total": 137, "pages": 14,
    "list": [ ]
  }
}
```

### 1.3 认证

- 登录 `POST /api/auth/login` → `{ token, tokenType: "Bearer", expiresIn, user: {...}, permissions: [...] }`
- 其余受保护接口请求头：`Authorization: Bearer <jwt>`
- 鉴权失败：无/过期 token → `401`；已登录但无权限 → `403`（权限由后端拦截器校验，前端隐藏按钮仅作体验优化，**不构成安全边界**）

### 1.4 校验与错误码

| code | 含义 | HTTP | 处理建议 |
|---|---|---|---|
| 0 | 成功 | 200 | — |
| 40001 | 参数校验失败（含字段级错误） | 400 | 前端定位到字段 |
| 40101 | 未登录 / token 失效 | 401 | 跳登录页 |
| 40301 | 无权限 | 403 | 提示无权限 |
| 40401 | 资源不存在 | 404 | — |
| 40901 | 业务冲突（重复、状态不允许） | 409 | 展示业务提示 |
| 42901 | 请求过于频繁 / 重复提交 | 429 | 稍后重试 |
| 50001 | 系统内部错误 | 500 | 展示 traceId |
| 50301 | 外部依赖未配置（如未配置模型 Key、地图 Key） | 503 | **明确提示"能力未启用"，不静默降级为假数据** |

字段级校验失败时 `data` 返回：`{ "fieldErrors": [{ "field": "phone", "message": "手机号格式不正确" }] }`。

### 1.5 演示数据标记

凡是由 `data.sql` 生成或由模拟服务产生的结果，响应必须带标记：
- 统计类：`"dataSource": "SAMPLE"` / `"REAL"`
- 人脸/AI 类：`"mock": true`、`"provider": "noop"`

---

## 2. 接口清单（按模块）

### 2.1 认证与用户（模块 A）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录 | 公开 |
| POST | `/api/auth/logout` | 登出（token 加入黑名单，Redis 未启用时用短时白名单替代） | 登录 |
| GET | `/api/auth/profile` | 当前用户 + 角色 + 权限码 | 登录 |
| PUT | `/api/auth/password` | 修改密码（校验原密码） | 登录 |
| GET | `/api/users` | 用户分页 | `system:user:list` |
| POST | `/api/users` | 新建用户 | `system:user:create` |
| PUT | `/api/users/{id}` | 编辑用户 / 重置密码 / 停用 | `system:user:update` |
| PUT | `/api/users/{id}/roles` | 分配角色 | `system:user:grant` |

### 2.2 角色与权限（模块 A）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/roles` | 角色列表（含权限树） | `system:role:list` |
| POST/PUT | `/api/roles`、`/api/roles/{id}` | 新增/编辑角色 | `system:role:update` |
| PUT | `/api/roles/{id}/permissions` | 分配权限 | `system:role:grant` |
| GET | `/api/permissions` | 权限码清单（系统内置 + 字典） | 登录 |

### 2.3 组织与员工（模块 A）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/companies`、`/api/companies/{id}` | 公司/子公司 |
| GET/POST/PUT | `/api/departments`、`/api/departments/{id}` | 部门（含子树，删除前校验子部门与员工） |
| GET/POST/PUT | `/api/positions`、`/api/positions/{id}` | 岗位 |
| GET | `/api/employees` | 员工分页（支持姓名/工号/部门/状态筛选） |
| POST / PUT | `/api/employees`、`/api/employees/{id}` | 新增/编辑（工号唯一校验） |
| PUT | `/api/employees/{id}/status` | 启用/停用 |
| POST/DELETE | `/api/employees/{id}/places`、`/api/employees/{id}/places/{placeId}` | 员工-场所绑定 |

### 2.4 办公场所（模块 B）

| 方法 | 路径 | 说明 | 备注 |
|---|---|---|---|
| GET | `/api/places` | 分页（名称/公司/状态筛选） | — |
| POST / PUT | `/api/places`、`/api/places/{id}` | 新增/编辑 | 重复名称校验 |
| PUT | `/api/places/{id}/status` | 停用/启用 | 停用后不可作为打卡场所 |
| PUT | `/api/places/{id}/location` | 经纬度写入（人工或地图采集结果） | 越界校验 |
| POST | `/api/places/{id}/code` | 生成/续期公司 ID 码 | 全局唯一 |
| GET | `/api/map/adapter/status` | 地图适配层状态 | 无 Key → `{provider:"none", enabled:false}` |

### 2.5 二维码与打卡（模块 C，核心链路）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/qrcodes` | 生成二维码（参数：placeId、有效期秒、是否一次性、类型） | 管理员 |
| GET | `/api/qrcodes/{id}/image` | 返回 PNG 图片流 | 管理员 |
| GET | `/api/qrcodes` | 二维码列表与状态 | 管理员 |
| POST | `/api/qrcodes/{id}/invalidate` | 手动失效 | 管理员 |
| POST | `/api/checkin/request-id` | H5 端获取本次打卡幂等 requestId | 登录 |
| POST | `/api/checkin` | 提交打卡（二维码 payload 或手动输入 content） | 登录 |
| GET | `/api/my/attendance/records` | 本人考勤记录分页 | 登录 |
| GET | `/api/admin/attendance/records` | 管理员查询（员工/部门/场所/日期/状态筛选） | 管理员 |

打卡成功响应示例：

```jsonc
{
  "code": 0, "message": "打卡成功",
  "data": {
    "recordId": 1024, "employeeName": "张明", "placeName": "总部-A 座",
    "checkinType": "ON_DUTY", "checkinTime": "2026-10-26 09:02:11",
    "result": "NORMAL", "source": "H5"
  }
}
```

失败响应示例（`50301 / 40901`）：

```jsonc
{ "code": 40901, "message": "该二维码已过期，请向管理员获取最新二维码", "data": { "reason": "QR_EXPIRED" } }
```

### 2.6 统计（模块 D）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/statistics/summary?scope=&startDate=&endDate=` | 出勤率/迟到/早退/缺勤/平均工时 |
| GET | `/api/statistics/trend?type=day|week|month` | 趋势数据（ECharts） |
| GET | `/api/statistics/department` | 部门维度对比 |
| GET | `/api/statistics/place-heat?date=` | 场所按时段分布（无地图 Key 时返回时段数据而非地图图层） |
| POST | `/api/statistics/daily-rebuild?date=` | 重算指定日汇总（幂等） |

### 2.7 审批（模块 E）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/applications` | 提交请假/加班申请 |
| GET | `/api/my/applications` | 本人申请列表 |
| GET | `/api/applications/pending` | 待我审批（管理员/主管） |
| POST | `/api/applications/{id}/submit` | 草稿提交 |
| POST | `/api/applications/{id}/approve` | `{action: APPROVE|REJECT, comment}` |
| POST | `/api/applications/{id}/cancel` | 本人撤销（仅 SUBMITTED 状态） |

### 2.8 知识库与 AI 问答（模块 F）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/kb/documents`（multipart） | 上传并解析入库 |
| GET | `/api/kb/documents` | 文档列表（受可见范围约束） |
| DELETE | `/api/kb/documents/{id}` | 删除文档与切片 |
| POST | `/api/kb/documents/{id}/reindex` | 重建索引/重切 |
| POST | `/api/kb/ask` | `{question, topK}` → 答案 + 来源片段 + `mode` |
| GET | `/api/kb/status` | 解析/检索/模型能力状态（是否配置了 Key） |
| GET | `/api/kb/qa-logs` | 问答日志（管理员） |

`POST /api/kb/ask` 响应示例（未配置模型 Key 的降级模式）：

```jsonc
{
  "code": 0, "message": "OK",
  "data": {
    "mode": "retrieval-only",
    "answer": null,
    "sources": [
      { "documentId": 12, "documentTitle": "员工考勤管理制度.pdf", "chunkIndex": 7, "score": 3.41, "snippet": "第三章 考勤规则……" }
    ],
    "notice": "未检测到大模型配置，当前仅返回原文检索结果；如需生成式回答，请配置 LZ_LLM_API_KEY。"
  }
}
```

### 2.9 人脸识别（模块 G）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/face/status` | 返回当前 provider 与是否为模拟实现 |
| POST | `/api/face/enroll` | 录入（模拟实现返回 `mock:true`，不存 original image） |
| POST | `/api/face/verify` | 人脸打卡校验（响应必须含 `mock`） |

⚠️ 模拟实现在 UI 与日志中一律标注"模拟识别"，不得描述为真实活体检测。

### 2.10 运维与任务（模块 H）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/system/health` | 应用与依赖状态（DB/Redis/Nacos/LLM/Map） |
| GET | `/api/system/jobs` | 定时任务列表与最近执行结果 |
| POST | `/api/system/jobs/{name}/run` | 手动触发（幂等） |

---

## 3. 接口设计规则（写入约定）

1. 所有写操作必须校验权限后再执行业务，禁止"先执行再判断"。
2. 分页接口统一走 `PageQuery` → `PageResult`，禁止裸 List 返回大数据集合。
3. 时间参数统一 `yyyy-MM-dd HH:mm:ss` 或 `yyyy-MM-dd`，由后端 `@DateTimeFormat` 解析。
4. 出参使用 VO，禁止把 Entity 直接返回（避免泄露 `password_hash` 等字段）。
5. 所有异常走 `@RestControllerAdvice` 统一转 `code/message`，禁止把堆栈直接返回前端。
6. 每个接口必须有可复现的测试用例（见 `docs/test-plan.md`），至少覆盖：成功、参数错误、无权限、业务冲突四类。
