-- =============================================================
-- 阶段 6 增量脚本：sys_file 受控存储与鉴权下载
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺口，非虚构）：
--   1) sys_file 表在阶段 1 就建好了，知识库上传也确实往里写了记录，
--      但全项目没有任何下载接口——文件"只进不出"，记录成了死数据。
--      阶段 6 要求"本地受控存储 + 鉴权下载"，所以补齐 lz-file 模块。
--   2) sys_file.visibility 原设计只有 PUBLIC / PRIVATE 两档，表达不了
--      "本部门可见"。知识库文档有三档可见范围（PUBLIC/DEPT/PRIVATE），
--      文件却只有两档，DEPT 文档的原文件要么被迫公开、要么本部门的人
--      反而下载不了 → 新增 owner_dept_id 列，让文件可见范围与文档对齐。
--   3) 知识库上传时 visibility 是硬编码 'PRIVATE'，与文档实际可见范围脱节。
--      属于代码侧修复（见 KnowledgeService.upload），本脚本只准备数据结构。
--
-- 幂等性：
--   - 加列用 information_schema 预检，已存在则跳过（MySQL 无 ADD COLUMN IF NOT EXISTS）；
--   - sys_permission.perm_code 有唯一索引，sys_role_permission 以 (role_id, permission_id)
--     为主键，INSERT IGNORE 可安全重复执行。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. sys_file 增加部门归属列 ----------
SET @schema_name = DATABASE();
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME   = 'sys_file'
      AND COLUMN_NAME  = 'owner_dept_id'
);
SET @ddl = IF(@col_exists = 0,
    "ALTER TABLE sys_file ADD COLUMN owner_dept_id BIGINT DEFAULT NULL COMMENT 'visibility=DEPT 时生效的部门；其余为 NULL' AFTER visibility",
    "SELECT 'skip: sys_file.owner_dept_id already exists' AS info");
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 下载需要按上传人检索自己的文件 ----------
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME   = 'sys_file'
      AND INDEX_NAME   = 'idx_visibility'
);
SET @ddl = IF(@idx_exists = 0,
    "CREATE INDEX idx_visibility ON sys_file (visibility)",
    "SELECT 'skip: sys_file.idx_visibility already exists' AS info");
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 存量数据修正：让文件可见范围追平文档 ----------
-- 阶段 5 上传文档时 visibility 被硬编码为 PRIVATE，导致已有文档（含 PUBLIC 文档）
-- 的原文件谁都下载不到。这里按文档的可见范围回填，顺带补齐部门归属。
UPDATE sys_file f
JOIN kb_document d ON d.file_id = f.id
SET f.visibility    = d.visibility,
    f.owner_dept_id = d.owner_dept_id
WHERE f.visibility <> d.visibility
   OR NOT (f.owner_dept_id <=> d.owner_dept_id);

-- ---------- 4. 文件权限码 ----------
-- file:download 是所有登录用户都要有的（否则知识库文档谁都下不了），
-- 真正的边界在服务层按 visibility 判定，不在权限码层面。
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    ('file:download', '下载文件',   'file'),
    ('file:upload',   '上传文件',   'file'),
    ('file:delete',   '删除文件',   'file'),
    ('file:admin',    '文件全量管理', 'file');

-- ---------- 4. ADMIN：始终持有全部权限 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 5. MANAGER：可上传/下载/删除，但不给 file:admin ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('file:download', 'file:upload', 'file:delete');

-- ---------- 6. EMPLOYEE：可上传与下载（下载范围由服务层按 visibility 收紧）----------
-- file:delete 也要给：员工能传却不能删自己的文件，只能堆在库里等管理员清理。
-- 服务层已限制"只能删自己上传的"，给这个权限码不等于能删别人的。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN ('file:download', 'file:upload', 'file:delete');

-- ---------- 7. 校验输出（人工核对用）----------
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT p.perm_code, p.perm_name, GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
LEFT JOIN sys_role r           ON r.id = rp.role_id
WHERE p.perm_code LIKE 'file:%'
GROUP BY p.id, p.perm_code, p.perm_name
ORDER BY p.perm_code;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;
