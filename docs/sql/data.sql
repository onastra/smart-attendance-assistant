-- =============================================================
-- 演示数据（全部为虚构员工，不含任何真实个人信息）
-- 前置：已执行 docs/sql/schema.sql
-- 口令：admin / manager01 使用 Admin@123；emp01~emp03 使用 Emp@123
--      口令摘要为 BCrypt(strength=10)，应用侧用 BCryptPasswordEncoder 校验
-- =============================================================

SET NAMES utf8mb4;

-- ---------------- 组织 ----------------
INSERT INTO org_company (id, company_name, company_code, parent_id, status, remark) VALUES
    (1, '联智科技有限公司（总部）', 'LZ-HEAD', 0, 1, '虚构演示主体'),
    (2, '联智智能技术（子公司）', 'LZ-SUB-01', 1, 1, '虚构演示主体');

INSERT INTO org_department (id, company_id, dept_name, parent_id, leader_employee_id, sort_no, status) VALUES
    (1, 1, '研发部', 0, 1, 10, 1),
    (2, 1, '市场部', 0, 3, 20, 1),
    (3, 1, '人事行政部', 0, 5, 30, 1);

INSERT INTO org_position (id, company_id, position_name, status) VALUES
    (1, 1, '后端工程师', 1),
    (2, 1, '前端工程师', 1),
    (3, 1, '产品经理', 1),
    (4, 1, '人事专员', 1);

-- ---------------- 权限与角色 ----------------
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
    (61, 'face:verify',             '人脸校验',   'face');

INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
    (1, 'ADMIN',    '系统管理员', '拥有全部权限', 1),
    (2, 'MANAGER',  '部门主管',   '管理部门员工、审批与统计', 1),
    (3, 'EMPLOYEE', '普通员工',   '本人打卡、查询与申请', 1);

-- 管理员：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 部门主管：员工/场所只读与统计、审批、查询考勤
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 10), (2, 11), (2, 20), (2, 21), (2, 31), (2, 33), (2, 34), (2, 41), (2, 42), (2, 51), (2, 52);

-- 普通员工：打卡、本人记录、申请、知识问答、人脸校验
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (3, 32), (3, 33), (3, 40), (3, 41), (3, 52), (3, 61);

-- ---------------- 员工（虚构）----------------
INSERT INTO hr_employee (id, emp_no, name, gender, phone, email, company_id, dept_id, position_id, hire_date, status) VALUES
    (1, 'LZ0001', '张明', 1, '13800000001', 'zhangming@example.com', 1, 1, 1, '2023-03-01', 1),
    (2, 'LZ0002', '李慧', 2, '13800000002', 'lihui@example.com',     1, 1, 2, '2023-07-15', 1),
    (3, 'LZ0003', '王强', 1, '13800000003', 'wangqiang@example.com', 1, 2, 3, '2022-11-20', 1),
    (4, 'LZ0004', '赵敏', 2, '13800000004', 'zhaomin@example.com',   2, 2, 3, '2024-01-08', 1),
    (5, 'LZ0005', '陈静', 2, '13800000005', 'chenjing@example.com',  1, 3, 4, '2021-09-01', 1);

-- ---------------- 办公场所 ----------------
INSERT INTO biz_place (id, place_name, company_id, province, city, address, lng, lat, place_code, cover_radius_m, status) VALUES
    (1, '总部 A 座研发中心', 1, '江苏省', '南京市', '雨花台区软件大道 101 号', 118.7416000, 31.9963000, 'LZ-PLACE-A0001', 300, 1),
    (2, '总部 B 座市场中心', 1, '江苏省', '南京市', '雨花台区软件大道 102 号', 118.7442000, 31.9975000, 'LZ-PLACE-B0001', 300, 1),
    (3, '子公司智能办公区', 2, '江苏省', '苏州市', '工业园区星湖街 328 号',   120.7020000, 31.3160000, 'LZ-PLACE-S0001', 500, 1);

-- 员工与场所绑定
INSERT INTO hr_employee_place (id, employee_id, place_id, status) VALUES
    (1, 1, 1, 1), (2, 2, 1, 1), (3, 3, 2, 1), (4, 4, 3, 1), (5, 5, 1, 1);

-- ---------------- 登录账号 ----------------
-- 口令摘要：$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG = Admin@123
--           $2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo. = Emp@123
INSERT INTO sys_user (id, username, password_hash, nickname, employee_id, status) VALUES
    (1, 'admin',     '$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG', '系统管理员', NULL, 1),
    (2, 'manager01', '$2a$10$jSmPDuQCgvhOwZ3s892BgOwj.gRoKLZClFe5x.ap1vT.4dQgBXTNG', '部门主管-张明', 1, 1),
    (3, 'emp01',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '张明', 1, 1),
    (4, 'emp02',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '李慧', 2, 1),
    (5, 'emp03',     '$2a$10$PrQDUykWILhrxMSu.SnQWudWkcZ1SKBqncbBE7XhAAxJZxgJJzLo.', '王强', 3, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2), (2, 3),
    (3, 3), (4, 3), (5, 3);

-- ---------------- 示例打卡记录（用于阶段 4 统计报表演示数据）----------------
-- request_id 保证幂等；source=H5 表示来自移动端页面，真实的人脸模拟打卡会写 FACE_MOCK
INSERT INTO att_record (id, employee_id, place_id, checkin_type, checkin_time, source, result, request_id, remark) VALUES
    (1, 1, 1, 'ON_DUTY',  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 08:52:00'), 'H5', 'NORMAL', 'DEMO-SEED-0001', '演示数据'),
    (2, 1, 1, 'OFF_DUTY', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 18:36:00'), 'H5', 'NORMAL', 'DEMO-SEED-0002', '演示数据'),
    (3, 2, 1, 'ON_DUTY',  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 09:12:00'), 'H5', 'LATE',   'DEMO-SEED-0003', '演示数据'),
    (4, 3, 2, 'ON_DUTY',  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 08:48:00'), 'H5', 'NORMAL', 'DEMO-SEED-0004', '演示数据');

-- 说明：以上 4 条为演示数据，统计接口会把 source 与 demo 标记透出，
--       演示数据与真实数据的区分方式见 docs/test-plan.md 中 TC-D-08。
