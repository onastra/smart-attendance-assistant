-- =============================================================
-- PostgreSQL 版演示数据（对应 MySQL 版 data.sql + patch-phase2~6 + patch-phaseE 的合并结果）
-- 演示数据全部为虚构员工，不含任何真实个人信息
-- 口令：admin / manager01 使用 Admin@123；emp01~emp03 使用 Emp@123
--      口令摘要为 BCrypt(strength=10)，应用侧用 BCryptPasswordEncoder 校验
-- 幂等性：唯一键冲突走 ON CONFLICT DO NOTHING（等价 MySQL 的 INSERT IGNORE），可重复执行
-- =============================================================

-- ---------------- 组织 ----------------
INSERT INTO org_company (id, company_name, company_code, parent_id, status, remark) VALUES
    (1, '联智科技有限公司（总部）', 'LZ-HEAD', 0, 1, '虚构演示主体'),
    (2, '联智智能技术（子公司）', 'LZ-SUB-01', 1, 1, '虚构演示主体')
ON CONFLICT DO NOTHING;

INSERT INTO org_department (id, company_id, dept_name, parent_id, leader_employee_id, sort_no, status) VALUES
    (1, 1, '研发部', 0, 1, 10, 1),
    (2, 1, '市场部', 0, 3, 20, 1),
    (3, 1, '人事行政部', 0, 5, 30, 1)
ON CONFLICT DO NOTHING;

INSERT INTO org_position (id, company_id, position_name, status) VALUES
    (1, 1, '后端工程师', 1),
    (2, 1, '前端工程师', 1),
    (3, 1, '产品经理', 1),
    (4, 1, '人事专员', 1)
ON CONFLICT DO NOTHING;

-- ---------------- 权限与角色（基础 25 条 + patch-phase2/3/4/5/6-file/phaseE 合并）----------------
INSERT INTO sys_permission (id, perm_code, perm_name, module) VALUES
    (1,  'system:user:list',        '查询用户',   'system'),
    (2,  'system:user:create',      '新建用户',   'system'),
    (3,  'system:user:update',      '编辑用户',   'system'),
    (4,  'system:user:grant',       '分配角色',   'system'),
    (5,  'system:role:list',        '查询角色',   'system'),
    (6,  'system:role:update',      '编辑角色',   'system'),
    (7,  'system:role:grant',       '分配权限',   'system'),
    (10, 'hr:employee:list',        '查询员工',   'hr'),
    (11, 'hr:employee:update',      '编辑员工',   'hr'),
    (20, 'place:list',              '查询场所',   'place'),
    (21, 'place:update',            '编辑场所',   'place'),
    (22, 'place:code:generate',     '生成公司ID码', 'place'),
    (30, 'attendance:qrcode:create','生成二维码', 'attendance'),
    (31, 'attendance:qrcode:list',  '查询二维码', 'attendance'),
    (32, 'attendance:checkin',      '提交打卡',   'attendance'),
    (33, 'attendance:record:query', '查询考勤记录', 'attendance'),
    (34, 'statistics:view',         '查看统计',   'statistics'),
    (40, 'application:create',      '提交申请',   'approval'),
    (41, 'application:query',       '查询申请',   'approval'),
    (42, 'application:approve',     '审批申请',   'approval'),
    (50, 'kb:doc:upload',           '上传知识文档', 'knowledge'),
    (51, 'kb:doc:list',             '查询知识文档', 'knowledge'),
    (52, 'kb:ask',                  '知识问答',   'knowledge'),
    (60, 'face:enroll',             '人脸录入',   'face'),
    (61, 'face:verify',             '人脸校验',   'face')
ON CONFLICT DO NOTHING;

-- 关键：显式 id 插入不会推进自增序列，必须先同步，否则后续补丁权限码自动生成 id 会与显式 id 冲突
SELECT setval(pg_get_serial_sequence('sys_permission', 'id'), (SELECT COALESCE(MAX(id), 1) FROM sys_permission));

-- patch-phase2：补全 create/delete/list 类权限码
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('hr:employee:create',   '新建员工',   'hr'),
    ('hr:employee:delete',   '删除员工',   'hr'),
    ('place:create',         '新建场所',   'place'),
    ('place:delete',         '删除场所',   'place'),
    ('place:bind',           '绑定员工与场所', 'place'),
    ('system:role:create',   '新建角色',   'system'),
    ('system:role:delete',   '删除角色',   'system'),
    ('system:company:list',  '查询公司',   'system'),
    ('system:company:create','新建公司',   'system'),
    ('system:company:update','编辑公司',   'system'),
    ('system:company:delete','删除公司',   'system'),
    ('system:dept:list',     '查询部门',   'system'),
    ('system:dept:create',   '新建部门',   'system'),
    ('system:dept:update',   '编辑部门',   'system'),
    ('system:dept:delete',   '删除部门',   'system'),
    ('system:position:list',  '查询岗位',  'system'),
    ('system:position:create','新建岗位',  'system'),
    ('system:position:update','编辑岗位',  'system'),
    ('system:position:delete','删除岗位',  'system')
ON CONFLICT (perm_code) DO NOTHING;

-- patch-phase3：二维码失效 + 管理端考勤查询
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('attendance:qrcode:invalidate', '失效二维码', 'attendance'),
    ('attendance:record:admin',      '查询他人考勤记录', 'attendance')
ON CONFLICT (perm_code) DO NOTHING;

-- patch-phase4：重算日汇总
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('statistics:rebuild', '重算考勤日汇总', 'statistics')
ON CONFLICT (perm_code) DO NOTHING;

-- patch-phase5：知识库删除/重建/问答日志
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('kb:doc:delete',  '删除知识库文档', 'knowledge'),
    ('kb:doc:rebuild', '重建文档索引',   'knowledge'),
    ('kb:qa:log',      '查看问答日志',   'knowledge')
ON CONFLICT (perm_code) DO NOTHING;

-- patch-phase6-file：受控文件
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('file:download', '下载文件',   'file'),
    ('file:upload',   '上传文件',   'file'),
    ('file:delete',   '删除文件',   'file'),
    ('file:admin',    '文件全量管理', 'file')
ON CONFLICT (perm_code) DO NOTHING;

-- patch-phaseE：审批（application:approve 与基础数据重复，冲突自动跳过）
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
    ('application:submit',  '提交/查看我的申请', 'approval'),
    ('application:approve', '审批请假加班',     'approval'),
    ('application:list',    '审批记录全量查看', 'approval')
ON CONFLICT (perm_code) DO NOTHING;

INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
    (1, 'ADMIN',    '系统管理员', '拥有全部权限', 1),
    (2, 'MANAGER',  '部门主管',   '管理部门员工、审批与统计', 1),
    (3, 'EMPLOYEE', '普通员工',   '本人打卡、查询与申请', 1)
ON CONFLICT DO NOTHING;

-- ADMIN：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission
ON CONFLICT DO NOTHING;

-- 基础授权：部门主管
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 10), (2, 11), (2, 20), (2, 21), (2, 31), (2, 33), (2, 34), (2, 41), (2, 42), (2, 51), (2, 52)
ON CONFLICT DO NOTHING;

-- 基础授权：普通员工
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (3, 32), (3, 33), (3, 40), (3, 41), (3, 52), (3, 61)
ON CONFLICT DO NOTHING;

-- patch-phase2 授权：MANAGER
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN (
        'hr:employee:list',   'hr:employee:create', 'hr:employee:update',
        'place:list',         'place:create',       'place:update',       'place:bind',
        'system:company:list','system:dept:list',   'system:position:list',
        'application:query',  'application:approve',
        'attendance:qrcode:list', 'attendance:record:query',
        'statistics:view',
        'kb:ask',             'kb:doc:list'
      )
ON CONFLICT DO NOTHING;

-- patch-phase2 授权：EMPLOYEE
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN (
        'attendance:checkin',      'attendance:record:query',
        'application:create',      'application:query',
        'face:verify',
        'kb:ask',                  'kb:doc:list'
      )
ON CONFLICT DO NOTHING;

-- patch-phase3 授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN (
        'attendance:qrcode:list', 'attendance:qrcode:create', 'attendance:qrcode:invalidate',
        'attendance:checkin',     'attendance:record:query',  'attendance:record:admin',
        'statistics:view'
      )
ON CONFLICT DO NOTHING;

-- patch-phase4 授权：MANAGER 只能看看板不能重算
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER' AND p.perm_code IN ('statistics:view')
ON CONFLICT DO NOTHING;

-- patch-phase5 授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('kb:doc:delete', 'kb:doc:rebuild')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE' AND p.perm_code IN ('face:enroll')
ON CONFLICT DO NOTHING;

-- patch-phase6-file 授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('file:download', 'file:upload', 'file:delete')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN ('file:download', 'file:upload', 'file:delete')
ON CONFLICT DO NOTHING;

-- patch-phaseE 授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('application:submit', 'application:approve')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE' AND p.perm_code = 'application:submit'
ON CONFLICT DO NOTHING;

-- ---------------- 员工（虚构）----------------
INSERT INTO hr_employee (id, emp_no, name, gender, phone, email, company_id, dept_id, position_id, hire_date, status) VALUES
    (1, 'LZ0001', '张明', 1, '13800000001', 'zhangming@example.com', 1, 1, 1, DATE '2023-03-01', 1),
    (2, 'LZ0002', '李慧', 2, '13800000002', 'lihui@example.com',     1, 1, 2, DATE '2023-07-15', 1),
    (3, 'LZ0003', '王强', 1, '13800000003', 'wangqiang@example.com', 1, 2, 3, DATE '2022-11-20', 1),
    (4, 'LZ0004', '赵敏', 2, '13800000004', 'zhaomin@example.com',   2, 2, 3, DATE '2024-01-08', 1),
    (5, 'LZ0005', '陈静', 2, '13800000005', 'chenjing@example.com',  1, 3, 4, DATE '2021-09-01', 1)
ON CONFLICT DO NOTHING;

-- ---------------- 办公场所 ----------------
INSERT INTO biz_place (id, place_name, company_id, province, city, address, lng, lat, place_code, cover_radius_m, status) VALUES
    (1, '总部 A 座研发中心', 1, '江苏省', '南京市', '雨花台区软件大道 101 号', 118.7416000, 31.9963000, 'LZ-PLACE-A0001', 300, 1),
    (2, '总部 B 座市场中心', 1, '江苏省', '南京市', '雨花台区软件大道 102 号', 118.7442000, 31.9975000, 'LZ-PLACE-B0001', 300, 1),
    (3, '子公司智能办公区', 2, '江苏省', '苏州市', '工业园区星湖街 328 号',   120.7020000, 31.3160000, 'LZ-PLACE-S0001', 500, 1)
ON CONFLICT DO NOTHING;

-- 员工与场所绑定
INSERT INTO hr_employee_place (id, employee_id, place_id, status) VALUES
    (1, 1, 1, 1), (2, 2, 1, 1), (3, 3, 2, 1), (4, 4, 3, 1), (5, 5, 1, 1)
ON CONFLICT DO NOTHING;

-- ---------------- 登录账号 ----------------
-- 口令摘要：$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG = Admin@123
--           $2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo. = Emp@123
INSERT INTO sys_user (id, username, password_hash, nickname, employee_id, status) VALUES
    (1, 'admin',     '$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG', '系统管理员', NULL, 1),
    (2, 'manager01', '$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG', '部门主管-张明', 1, 1),
    (3, 'emp01',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '张明', 1, 1),
    (4, 'emp02',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '李慧', 2, 1),
    (5, 'emp03',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '王强', 3, 1)
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2), (2, 3),
    (3, 3), (4, 3), (5, 3)
ON CONFLICT DO NOTHING;

-- ---------------- 示例打卡记录（统计报表演示数据，source=H5）----------------
-- MySQL 版的 DATE_FORMAT(DATE_SUB(NOW(),...)) 在 PG 中用日期算术等价改写
INSERT INTO att_record (id, employee_id, place_id, checkin_type, checkin_time, source, result, request_id, remark) VALUES
    (1, 1, 1, 'ON_DUTY',  (CURRENT_DATE - 1) + TIME '08:52:00', 'H5', 'NORMAL', 'DEMO-SEED-0001', '演示数据'),
    (2, 1, 1, 'OFF_DUTY', (CURRENT_DATE - 1) + TIME '18:36:00', 'H5', 'NORMAL', 'DEMO-SEED-0002', '演示数据'),
    (3, 2, 1, 'ON_DUTY',  (CURRENT_DATE - 1) + TIME '09:12:00', 'H5', 'LATE',   'DEMO-SEED-0003', '演示数据'),
    (4, 3, 2, 'ON_DUTY',  (CURRENT_DATE - 1) + TIME '08:48:00', 'H5', 'NORMAL', 'DEMO-SEED-0004', '演示数据')
ON CONFLICT DO NOTHING;

-- ---------------- 同步自增序列（显式 id 插入后必须执行）----------------
SELECT setval(pg_get_serial_sequence('org_company', 'id'),        (SELECT COALESCE(MAX(id), 1) FROM org_company));
SELECT setval(pg_get_serial_sequence('org_department', 'id'),     (SELECT COALESCE(MAX(id), 1) FROM org_department));
SELECT setval(pg_get_serial_sequence('org_position', 'id'),       (SELECT COALESCE(MAX(id), 1) FROM org_position));
SELECT setval(pg_get_serial_sequence('sys_permission', 'id'),     (SELECT COALESCE(MAX(id), 1) FROM sys_permission));
SELECT setval(pg_get_serial_sequence('sys_role', 'id'),           (SELECT COALESCE(MAX(id), 1) FROM sys_role));
SELECT setval(pg_get_serial_sequence('hr_employee', 'id'),        (SELECT COALESCE(MAX(id), 1) FROM hr_employee));
SELECT setval(pg_get_serial_sequence('biz_place', 'id'),          (SELECT COALESCE(MAX(id), 1) FROM biz_place));
SELECT setval(pg_get_serial_sequence('hr_employee_place', 'id'),  (SELECT COALESCE(MAX(id), 1) FROM hr_employee_place));
SELECT setval(pg_get_serial_sequence('sys_user', 'id'),           (SELECT COALESCE(MAX(id), 1) FROM sys_user));
SELECT setval(pg_get_serial_sequence('att_record', 'id'),         (SELECT COALESCE(MAX(id), 1) FROM att_record));
