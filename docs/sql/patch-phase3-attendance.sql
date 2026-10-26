-- =============================================================
-- 阶段 3 增量脚本：考勤核心闭环（二维码签发 + H5 打卡 + 记录查询）
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺陷，非虚构）：
--   1) docs/database-design.md 描述的 att_qrcode 含 qr_type(固定/动态)，
--      但阶段 1 落库的 schema.sql 漏建该字段，动态码/长期码无法区分 → 补列。
--   2) 现有 sys_permission 只有 attendance:qrcode:create / :list、
--      attendance:checkin、attendance:record:query，
--      缺「二维码失效」权限码。若直接给接口标注 @RequirePermission，
--      权限码不存在会导致所有角色都被拦截器拒绝 → 补权限码。
--
-- 幂等性：
--   - 加列用 information_schema 判断存在性（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS）；
--   - sys_permission.perm_code 有唯一索引，sys_role_permission 以 (role_id, permission_id)
--     为主键，INSERT IGNORE 可安全重复执行。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. 补 att_qrcode.qr_type ----------
SET @schema_name = DATABASE();
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME   = 'att_qrcode'
      AND COLUMN_NAME  = 'qr_type'
);
SET @ddl = IF(@col_exists = 0,
    "ALTER TABLE att_qrcode ADD COLUMN qr_type VARCHAR(16) NOT NULL DEFAULT 'DYNAMIC' COMMENT '二维码类型：DYNAMIC 动态码 / FIXED 长期固定码' AFTER once_only",
    "SELECT 'skip: att_qrcode.qr_type already exists' AS info");
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 补全权限码 ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    ('attendance:qrcode:invalidate', '失效二维码', 'attendance'),
    -- 管理端考勤查询：与 attendance:record:query（本人查询）分开。
    -- 若两者共用一个权限码，普通员工就能调管理端接口看到同部门同事的考勤。
    ('attendance:record:admin',     '查询他人考勤记录', 'attendance');

-- ---------- 3. ADMIN：始终持有全部权限 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 4. MANAGER：可签发/失效二维码并查询本部门考勤 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN (
        'attendance:qrcode:list', 'attendance:qrcode:create', 'attendance:qrcode:invalidate',
        'attendance:checkin',     'attendance:record:query',  'attendance:record:admin',
        'statistics:view'
      );

-- ---------- 5. EMPLOYEE：仅本人打卡与本人记录查询 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN (
        'attendance:checkin',  'attendance:record:query'
      );

-- ---------- 6. 校验输出（人工核对用）----------
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;

SHOW COLUMNS FROM att_qrcode LIKE 'qr_type';
