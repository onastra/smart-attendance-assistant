> 目标数据库：MySQL **5.7.19**（本机实测版本）。
> 字符集：`utf8mb4 / utf8mb4_general_ci`；引擎：`InnoDB`。
> 脚本位置：`docs/sql/schema.sql`（建表，幂等：`DROP TABLE IF EXISTS` + 注释说明）、`docs/sql/data.sql`（演示数据，虚构员工）。
> ⚠️ MySQL 5.7 约束：**不支持窗口函数与 CTE**，所有统计必须用 `GROUP BY` / 自关联实现。

---

## 1. 实体总览

| 前缀 | 域 | 说明 |
|---|---|---|
| `sys_` | 系统与认证 | 用户、角色、权限、文件、日志 |
| `org_` | 组织 | 公司、部门、岗位 |
| `hr_` | 人员 | 员工、员工-场所绑定 |
| `biz_` | 业务单据 | 办公场所、请假/加班申请及其审批轨迹 |
| `att_` | 考勤 | 二维码、打卡记录、日统计汇总 |
| `kb_` | 知识库 | 文档、切片、问答日志 |
| `face_` | 生物识别 | 人脸特征（最小化存储） |
| `job_` | 定时任务 | 执行日志 |

## 2. 表清单（23 张）

### 2.1 系统与安全

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `sys_user` | id, username, password_hash, nickname, employee_id, status, last_login_at, created_at | UK(username), IDX(status) | 登录账号，密码 BCrypt，不存明文 |
| `sys_role` | id, role_code, role_name, description, status | UK(role_code) | `ADMIN`/`MANAGER`/`EMPLOYEE` 可配置 |
| `sys_permission` | id, perm_code, perm_name, module | UK(perm_code) | 如 `attendance:record:query` |
| `sys_role_permission` | role_id, permission_id | PK(role_id, permission_id) | 角色-权限关联 |
| `sys_user_role` | user_id, role_id | PK(user_id, role_id) | 用户-角色关联（多角色） |
| `sys_file` | id, original_name, storage_path, size, mime, uploader_id, visibility, created_at | IDX(uploader_id) | 本地存储 + 鉴权下载 |
| `log_operation` | id, user_id, module, action, target_id, ip, success, message, created_at | IDX(user_id), IDX(created_at) | 关键业务操作留痕 |

### 2.2 组织与人员

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `org_company` | id, company_name, company_code, parent_id, status | UK(company_code), IDX(parent_id) | 公司/子公司树 |
| `org_department` | id, company_id, dept_name, parent_id, leader_employee_id, status | IDX(company_id) | 部门树 |
| `org_position` | id, position_name, company_id, status | UK(company_id, position_name) | 岗位 |
| `hr_employee` | id, emp_no, name, gender, phone, email, company_id, dept_id, position_id, hire_date, status, user_id | UK(emp_no), IDX(dept_id), IDX(status) | 员工主数据 |
| `hr_employee_place` | id, employee_id, place_id, bind_time, status | UK(employee_id, place_id) | 员工-办公场所绑定 |

### 2.3 办公场所

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `biz_place` | id, place_name, company_id, province/city/address, lng, lat, place_code, cover_radius_m, status, remark | UK(place_code), IDX(company_id), IDX(status) | 场所坐标支持手动录入（无地图 Key 时的替代方式） |

### 2.4 考勤

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `att_qrcode` | id, place_id, biz_id(nonce), payload, sign, qr_type(固定/动态), once_only, status(有效/已失效/已过期), issue_time, expire_time, created_by | IDX(place_id, status), IDX(expire_time) | 二维码主数据；签名由服务端 HMAC 生成并校验 |
| `att_record` | id, employee_id, place_id, qrcode_id, checkin_type(上班/下班/外勤), checkin_time, source(H5/后台补录/模拟人脸), result(正常/迟到/早退/异常), lng, lat, client_ip, remark, request_id | IDX(employee_id, checkin_time), **UK(request_id)** 幂等去重, IDX(place_id, checkin_time) | 打卡流水，不可硬删除 |
| `att_daily_stat` | id, employee_id, stat_date, first_checkin, last_checkout, work_minutes, status(正常/迟到/早退/缺勤/请假/休息), source（是否演示数据） | **UK(employee_id, stat_date)**，IDX(stat_date) | 由 Spring Task 汇总，支持重算 |

### 2.5 审批

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `biz_application` | id, application_no, employee_id, type(LEAVE/OVERTIME), sub_type, start_time, end_time, duration_hours, reason, attachment_file_id, status(DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED), submitted_at, current_approver_id | UK(application_no), IDX(employee_id, status), IDX(status) | 请假/加班申请单 |
| `biz_application_approval` | id, application_id, approver_id, action(APPROVE/REJECT), comment, created_at | IDX(application_id) | 审批流水，重复审批由状态机拦截 |

### 2.6 知识库

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `kb_document` | id, title, file_type, file_size, file_id(sys_file), content_hash, visibility(PUBLIC/DEPT/PRIVATE), owner_dept_id, status(PARSING/READY/FAILED), parse_message, chunk_count, created_by, created_at | IDX(status), IDX(visibility, owner_dept_id) | 文档元数据，解析失败必须留原因 |
| `kb_chunk` | id, document_id, chunk_index, section(page/章节), content, token_estimate, created_at | IDX(document_id, chunk_index), **FULLTEXT(content) WITH PARSER ngram** | 切片表，ngram 全文索引支撑中文检索 |
| `kb_qa_log` | id, user_id, question, hit_count, top_score, mode(retrieval-only/llm-augmented), latency_ms, created_at | IDX(user_id, created_at) | **不存完整答案正文与任何密钥** |

### 2.7 生物识别与任务

| 表 | 关键字段 | 约束/索引 | 说明 |
|---|---|---|---|
| `face_feature` | id, employee_id, provider, feature_hash, mock_flag, enroll_time, status | UK(employee_id, provider), IDX(mock_flag) | 默认**不存原始人脸图**，仅存特征值哈希；`mock_flag` 标注数据由模拟服务产生 |
| `job_exec_log` | id, job_name, start_time, end_time, success, affected_rows, message | IDX(job_name, start_time) | 定时任务执行留痕，支撑幂等与失败追踪 |

## 3. 关键关系（ER 摘要）

```
org_company 1 ──< org_department 1 ──< hr_employee >── user_id ── sys_user
org_company 1 ──< biz_place 1 ──< att_qrcode 1 ──< att_record
hr_employee 1 ──< hr_employee_place >── 1 biz_place
hr_employee 1 ──< att_record
hr_employee 1 ──< biz_application 1 ──< biz_application_approval
kb_document 1 ──< kb_chunk
hr_employee 1 ──< face_feature
sys_user   >──< sys_role >──< sys_permission
```

## 4. 设计取舍说明

1. **`att_daily_stat` 冗余汇总表**：MySQL 5.7 无物化视图，统计页直接聚合大表在演示数据量下可行，但为满足"趋势图 + 跨日重算 + 幂等"要求，采用 Spring Task 每日汇总到冗余表，并在查询时优先读汇总表、提供 `rebuild` 接口重算。
2. **幂等去重**：`att_record.request_id` 唯一约束由服务端生成（H5 端每次进入打卡页获取一次），防止网络重试与重复提交产生双份记录。
3. **不硬删除**：菜单/员工/场所/文档均用 `status` 停用字段，保留历史可追溯性。
4. **中文检索**：`FULLTEXT(content) WITH PARSER ngram` 依赖 MySQL 5.7 内置 ngram 插件，`ngram_token_size` 默认 2；未启用备用方案为 `LIKE` 兜底（检索器内部降级，会记录 WARN）。
5. **演示数据隔离**：演示数据统一来源 `data.sql`，`att_daily_stat.source` 字段标记统计是否为真实聚合结果；不得使用写死数字冒充统计。

## 5. 初始化与执行说明

```bash
# 1) 建库（需 MySQL 凭据，口令通过环境变量传入，不写入脚本）
mysql -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 建表
mysql -h127.0.0.1 -P3306 -uroot -p lianzhi < docs/sql/schema.sql

# 3) 演示数据（虚构员工，不含任何真实个人信息）
mysql -h127.0.0.1 -P3306 -uroot -p lianzhi < docs/sql/data.sql
```

> 应用的连接参数通过环境变量注入：`LZ_DB_URL`、`LZ_DB_USERNAME`、`LZ_DB_PASSWORD`；本地覆盖文件 `server/lz-app/src/main/resources/application-local.yml` 已加入 `.gitignore`。
