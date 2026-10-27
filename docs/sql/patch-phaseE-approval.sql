-- =============================================================
-- 阶段 E 增量脚本：请假/加班审批权限码（模块 E）
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺口，非虚构）：
--   模块 E 在阶段 0 被决议"暂缓"，biz_application / biz_application_approval 两张表
--   已在 schema.sql 建好，但一直没有任何权限码与业务代码。
--   本脚本补齐三个权限码并建立角色-权限关系，使审批功能可真实落地。
--
-- 幂等性：
--   - sys_permission.perm_code 有唯一索引，INSERT IGNORE 可安全重复执行；
--   - sys_role_permission 以 (role_id, permission_id) 为主键，INSERT IGNORE 同样安全。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. 审批权限码 ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    ('application:submit',  '提交/查看我的申请', 'approval'),
    ('application:approve', '审批请假加班',     'approval'),
    ('application:list',    '审批记录全量查看', 'approval');

-- ---------- 2. ADMIN：始终持有全部权限（含本次新增）----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 3. MANAGER：可提交本人申请 + 审批他人 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('application:submit', 'application:approve');

-- ---------- 4. EMPLOYEE：仅可提交/查看本人申请 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code = 'application:submit';

-- ---------- 5. 校验输出（人工核对用）----------
SELECT p.perm_code, p.perm_name, GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
LEFT JOIN sys_role r           ON r.id = rp.role_id
WHERE p.perm_code LIKE 'application:%'
GROUP BY p.id, p.perm_code, p.perm_name
ORDER BY p.perm_code;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;
