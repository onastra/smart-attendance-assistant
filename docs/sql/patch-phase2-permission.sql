-- =============================================================
-- 阶段 2 增量脚本：补全权限码并同步角色授权
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺陷，非虚构）：
--   初始化时 sys_permission 只有 list / update 两类权限，
--   缺少 create / delete。若直接给接口标注 @RequirePermission("xxx:create")，
--   由于权限码不存在，任何角色都会被拦截器拒绝，功能等于做不出来。
--
-- 幂等性：
--   sys_permission.perm_code 有唯一索引，sys_role_permission 以 (role_id, permission_id)
--   为主键，因此 INSERT IGNORE 重复执行不会产生脏数据也不会报错，可安全多次运行。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. 补全缺失的权限码 ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    -- 员工档案
    ('hr:employee:create',   '新建员工',   'hr'),
    ('hr:employee:delete',   '删除员工',   'hr'),
    -- 办公场所
    ('place:create',         '新建场所',   'place'),
    ('place:delete',         '删除场所',   'place'),
    ('place:bind',           '绑定员工与场所', 'place'),
    -- 角色
    ('system:role:create',   '新建角色',   'system'),
    ('system:role:delete',   '删除角色',   'system'),
    -- 组织架构
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
    ('system:position:delete','删除岗位',  'system');

-- ---------- 2. ADMIN：始终持有全部权限（新增权限码后自动同步）----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 3. MANAGER：部门主管（管理本部门员工与场所，可查看组织）----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
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
      );

-- ---------- 4. EMPLOYEE：普通员工（仅本人相关的查询与提交）----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN (
        'attendance:checkin',      'attendance:record:query',
        'application:create',      'application:query',
        'face:verify',
        'kb:ask',                  'kb:doc:list'
      );

-- ---------- 5. 校验输出（人工核对用）----------
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;
