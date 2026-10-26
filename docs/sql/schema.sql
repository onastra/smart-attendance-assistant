-- =============================================================
-- 联智·企业级 AI Agent 智能考勤与业务助手 —— 数据库初始化脚本
-- 目标版本：MySQL 5.7（本机实测 5.7.19）
-- 说明：
--   1) 本脚本使用 DROP TABLE IF EXISTS，可重复执行；再次执行会清空业务数据。
--   2) 演示数据在 data.sql 中单独提供，先 schema 再 data。
--   3) 表之间不建物理外键，关联校验在应用层完成（便于分模块解耦与演示数据清理），
--      唯一性由唯一索引保证。
--   4) MySQL 5.7 不支持窗口函数与 CTE，所有统计请用 GROUP BY 实现。
-- =============================================================

SET NAMES utf8mb4;

-- ---------------- 组织与账号 ----------------
DROP TABLE IF EXISTS org_company;
CREATE TABLE org_company (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company_name  VARCHAR(100) NOT NULL COMMENT '公司名称',
    company_code  VARCHAR(64)  NOT NULL COMMENT '公司编码（唯一）',
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '上级公司，0 表示总公司',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    remark        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    created_by    BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_code (company_code),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公司/子公司';

DROP TABLE IF EXISTS org_department;
CREATE TABLE org_department (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company_id         BIGINT       NOT NULL COMMENT '所属公司',
    dept_name          VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '上级部门，0 表示一级部门',
    leader_employee_id BIGINT                DEFAULT NULL COMMENT '部门负责人（员工档案）',
    sort_no            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_id (company_id),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门';

DROP TABLE IF EXISTS org_position;
CREATE TABLE org_position (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    company_id    BIGINT       NOT NULL COMMENT '所属公司',
    position_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_position (company_id, position_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(64) NOT NULL COMMENT '角色编码，如 ADMIN / MANAGER / EMPLOYEE',
    role_name   VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255)         DEFAULT NULL COMMENT '说明',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    perm_code  VARCHAR(100) NOT NULL COMMENT '权限码，如 attendance:record:query',
    perm_name  VARCHAR(100) NOT NULL COMMENT '权限名称',
    module     VARCHAR(64)  NOT NULL COMMENT '所属模块',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限';

DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL COMMENT '角色 ID',
    permission_id BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (role_id, permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联';

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username       VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要，严禁存明文',
    nickname       VARCHAR(64)           DEFAULT NULL COMMENT '昵称/真实姓名',
    employee_id    BIGINT                DEFAULT NULL COMMENT '关联员工档案',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    last_login_at  DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    last_login_ip  VARCHAR(64)           DEFAULT NULL COMMENT '最近登录 IP',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录账号';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联';

-- ---------------- 员工与场所 ----------------
DROP TABLE IF EXISTS hr_employee;
CREATE TABLE hr_employee (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    emp_no         VARCHAR(32)  NOT NULL COMMENT '工号（唯一）',
    name           VARCHAR(64)  NOT NULL COMMENT '姓名',
    gender         TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0 未知 1 男 2 女',
    phone          VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    email          VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    company_id     BIGINT                DEFAULT NULL COMMENT '所属公司',
    dept_id        BIGINT                DEFAULT NULL COMMENT '所属部门',
    position_id    BIGINT                DEFAULT NULL COMMENT '岗位',
    hire_date      DATE                  DEFAULT NULL COMMENT '入职日期',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 在职 0 停用',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_no (emp_no),
    KEY idx_dept_id (dept_id),
    KEY idx_status (status),
    KEY idx_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工档案';

DROP TABLE IF EXISTS hr_employee_place;
CREATE TABLE hr_employee_place (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    employee_id BIGINT   NOT NULL COMMENT '员工',
    place_id    BIGINT   NOT NULL COMMENT '办公场所',
    bind_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    status      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 有效 0 解除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_place (employee_id, place_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工-办公场所绑定';

DROP TABLE IF EXISTS biz_place;
CREATE TABLE biz_place (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    place_name     VARCHAR(100)  NOT NULL COMMENT '场所名称',
    company_id     BIGINT                 DEFAULT NULL COMMENT '所属公司',
    province       VARCHAR(64)            DEFAULT NULL COMMENT '省',
    city           VARCHAR(64)            DEFAULT NULL COMMENT '市',
    address        VARCHAR(255)           DEFAULT NULL COMMENT '详细地址',
    lng            DECIMAL(10, 7)         DEFAULT NULL COMMENT '经度（支持手动录入）',
    lat            DECIMAL(10, 7)         DEFAULT NULL COMMENT '纬度（支持手动录入）',
    place_code     VARCHAR(64)            DEFAULT NULL COMMENT '公司 ID 码/业务识别码',
    cover_radius_m INT          NOT NULL DEFAULT 200 COMMENT '允许打卡半径（米）',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    remark         VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_place_code (place_code),
    KEY idx_company_id (company_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '办公场所';

-- ---------------- 考勤 ----------------
DROP TABLE IF EXISTS att_qrcode;
CREATE TABLE att_qrcode (
    id          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    place_id    BIGINT     NOT NULL COMMENT '所属场所',
    biz_id      VARCHAR(64) NOT NULL COMMENT '业务随机串 nonce，用于防重放',
    payload     VARCHAR(512) NOT NULL COMMENT '二维码内容（含 placeId/bizId/expireAt）',
    sign        VARCHAR(128) NOT NULL COMMENT 'HMAC-SHA256 签名，服务端二次校验',
    once_only   TINYINT    NOT NULL DEFAULT 0 COMMENT '是否一次性：1 是 0 否',
    status      VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT '状态：VALID/INVALID/EXPIRED/USED',
    issue_time  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    expire_time DATETIME   NOT NULL COMMENT '过期时间',
    used_time   DATETIME            DEFAULT NULL COMMENT '首次使用时间',
    created_by  BIGINT              DEFAULT NULL COMMENT '签发人',
    created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_id (biz_id),
    KEY idx_place_status (place_id, status),
    KEY idx_expire_time (expire_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考勤二维码';

DROP TABLE IF EXISTS att_record;
CREATE TABLE att_record (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    employee_id   BIGINT      NOT NULL COMMENT '员工',
    place_id      BIGINT      NOT NULL COMMENT '打卡场所',
    qrcode_id     BIGINT               DEFAULT NULL COMMENT '使用的二维码',
    checkin_type  VARCHAR(16) NOT NULL COMMENT '打卡类型：ON_DUTY/OFF_DUTY/FIELD',
    checkin_time  DATETIME    NOT NULL COMMENT '打卡时间',
    source        VARCHAR(16) NOT NULL DEFAULT 'H5' COMMENT '来源：H5/FACE_MOCK/ADMIN',
    result        VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '结果：NORMAL/LATE/EARLY/ABNORMAL',
    lng           DECIMAL(10, 7)       DEFAULT NULL COMMENT '打卡经度',
    lat           DECIMAL(10, 7)       DEFAULT NULL COMMENT '打卡纬度',
    client_ip     VARCHAR(64)          DEFAULT NULL COMMENT '客户端 IP',
    request_id    VARCHAR(64) NOT NULL COMMENT '幂等请求号，重复提交不会产生新记录',
    remark        VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_employee_time (employee_id, checkin_time),
    KEY idx_place_time (place_id, checkin_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打卡记录';

DROP TABLE IF EXISTS att_daily_stat;
CREATE TABLE att_daily_stat (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    employee_id    BIGINT   NOT NULL COMMENT '员工',
    stat_date      DATE     NOT NULL COMMENT '统计日期',
    first_checkin  DATETIME          DEFAULT NULL COMMENT '首次上班打卡',
    last_checkout  DATETIME          DEFAULT NULL COMMENT '末次下班打卡',
    work_minutes   INT      NOT NULL DEFAULT 0 COMMENT '工时（分钟）',
    status         VARCHAR(16) NOT NULL DEFAULT 'ABSENT' COMMENT 'NORMAL/LATE/EARLY/ABSENT/LEAVE/REST',
    source         VARCHAR(16) NOT NULL DEFAULT 'REAL' COMMENT 'REAL=真实聚合，SAMPLE=演示数据',
    rebuilt_at     DATETIME          DEFAULT NULL COMMENT '最近重算时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_date (employee_id, stat_date),
    KEY idx_stat_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考勤日汇总（定时任务生成，可重算）';

-- ---------------- 审批 ----------------
DROP TABLE IF EXISTS biz_application;
CREATE TABLE biz_application (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    application_no      VARCHAR(32)   NOT NULL COMMENT '申请单号（唯一）',
    employee_id         BIGINT        NOT NULL COMMENT '申请人',
    type                VARCHAR(16)   NOT NULL COMMENT '类型：LEAVE/OVERTIME',
    sub_type            VARCHAR(32)            DEFAULT NULL COMMENT '子类型：事假/病假/年假等',
    start_time          DATETIME      NOT NULL COMMENT '开始时间',
    end_time            DATETIME      NOT NULL COMMENT '结束时间',
    duration_hours      DECIMAL(6, 2) NOT NULL DEFAULT 0 COMMENT '时长（小时）',
    reason              VARCHAR(500)  NOT NULL COMMENT '事由',
    attachment_file_id  BIGINT                 DEFAULT NULL COMMENT '附件（sys_file.id）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED' COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED',
    current_approver_id BIGINT                 DEFAULT NULL COMMENT '当前审批人',
    submitted_at        DATETIME               DEFAULT NULL COMMENT '提交时间',
    finished_at         DATETIME               DEFAULT NULL COMMENT '审批完成时间',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_no (application_no),
    KEY idx_employee_status (employee_id, status),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '请假/加班申请单';

DROP TABLE IF EXISTS biz_application_approval;
CREATE TABLE biz_application_approval (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    application_id BIGINT      NOT NULL COMMENT '申请单',
    approver_id    BIGINT      NOT NULL COMMENT '审批人',
    action         VARCHAR(16) NOT NULL COMMENT 'APPROVE/REJECT',
    comment        VARCHAR(500)         DEFAULT NULL COMMENT '审批意见（驳回时必填）',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_id (application_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流水';

-- ---------------- 知识库 ----------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title         VARCHAR(200) NOT NULL COMMENT '文档标题',
    file_type     VARCHAR(16)  NOT NULL COMMENT '文件类型：PDF/DOCX/XLSX/PPTX/TXT/MD',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_id       BIGINT                DEFAULT NULL COMMENT '关联 sys_file.id',
    content_hash  VARCHAR(64)           DEFAULT NULL COMMENT '内容指纹，用于去重',
    visibility    VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT '可见范围：PUBLIC/DEPT/PRIVATE',
    owner_dept_id BIGINT                DEFAULT NULL COMMENT '可见部门（DEPT 时生效）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PARSING' COMMENT 'PARSING/READY/FAILED',
    parse_message VARCHAR(500)          DEFAULT NULL COMMENT '解析失败原因（必须如实填写）',
    chunk_count   INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    created_by    BIGINT                DEFAULT NULL COMMENT '上传人',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_visibility (visibility, owner_dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档';

DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id    BIGINT   NOT NULL COMMENT '所属文档',
    chunk_index    INT      NOT NULL COMMENT '切片序号',
    section        VARCHAR(128)      DEFAULT NULL COMMENT '来源位置：页码/章节',
    content        TEXT     NOT NULL COMMENT '切片正文',
    token_estimate INT      NOT NULL DEFAULT 0 COMMENT '估算长度，用于截断控制',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_document_index (document_id, chunk_index),
    -- 中文全文检索：MySQL 5.7 内置 ngram 解析器
    FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库切片';

DROP TABLE IF EXISTS kb_qa_log;
CREATE TABLE kb_qa_log (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT            DEFAULT NULL COMMENT '提问用户',
    question   VARCHAR(500) NOT NULL COMMENT '问题原文',
    hit_count  INT      NOT NULL DEFAULT 0 COMMENT '命中切片数',
    top_score  DECIMAL(8, 4)     DEFAULT NULL COMMENT '最高匹配分',
    mode       VARCHAR(24)       DEFAULT NULL COMMENT 'retrieval-only / llm-augmented',
    latency_ms INT               DEFAULT NULL COMMENT '耗时（毫秒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '问答日志（不记录答案正文与任何密钥）';

-- ---------------- 生物识别 ----------------
DROP TABLE IF EXISTS face_feature;
CREATE TABLE face_feature (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    employee_id  BIGINT      NOT NULL COMMENT '员工',
    provider     VARCHAR(32) NOT NULL DEFAULT 'noop' COMMENT '识别服务：noop/arcsoft',
    feature_hash VARCHAR(128)         DEFAULT NULL COMMENT '特征值哈希；默认不存原始人脸图片',
    mock_flag    TINYINT     NOT NULL DEFAULT 1 COMMENT '1=模拟实现产生的数据',
    enroll_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 有效 0 失效',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_provider (employee_id, provider),
    KEY idx_mock_flag (mock_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '人脸特征（最小化存储）';

-- ---------------- 文件、日志与任务 ----------------
DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    original_name  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_path   VARCHAR(500) NOT NULL COMMENT '存储相对路径（不直接对外暴露）',
    size           BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    mime           VARCHAR(100)          DEFAULT NULL COMMENT 'MIME 类型',
    uploader_id    BIGINT                DEFAULT NULL COMMENT '上传人',
    visibility     VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT '可见范围：PUBLIC/PRIVATE',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_uploader (uploader_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件记录（下载走鉴权接口）';

DROP TABLE IF EXISTS log_operation;
CREATE TABLE log_operation (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT                DEFAULT NULL COMMENT '操作人',
    module     VARCHAR(64)  NOT NULL COMMENT '业务模块',
    action     VARCHAR(64)  NOT NULL COMMENT '操作类型',
    target_id  VARCHAR(64)           DEFAULT NULL COMMENT '操作对象 ID',
    ip         VARCHAR(64)           DEFAULT NULL COMMENT '来源 IP',
    success    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功',
    message    VARCHAR(500)          DEFAULT NULL COMMENT '补充说明',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

DROP TABLE IF EXISTS job_exec_log;
CREATE TABLE job_exec_log (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_name      VARCHAR(100) NOT NULL COMMENT '任务名',
    start_time    DATETIME NOT NULL COMMENT '开始时间',
    end_time      DATETIME          DEFAULT NULL COMMENT '结束时间',
    success       TINYINT           DEFAULT NULL COMMENT '是否成功',
    affected_rows INT               DEFAULT NULL COMMENT '影响行数',
    message       VARCHAR(500)      DEFAULT NULL COMMENT '异常信息',
    PRIMARY KEY (id),
    KEY idx_job_start (job_name, start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务执行日志';
