-- =============================================================
-- 阶段 5 增量脚本：知识库（模块 F）与人脸识别适配层（模块 G）
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺口，非虚构）：
--   1) 现有权限码只有 kb:doc:list / kb:doc:upload / kb:ask，
--      缺「删除文档」「重建索引」「查看问答日志」三类写/敏操作。
--      删除会连带清掉切片（不可恢复），重建会重写 kb_chunk，
--      问答日志含全部员工提问原文 —— 都不能让"能提问的人"顺手拿到。
--   2) face:enroll 目前只有 ADMIN 持有，但人脸录入是"员工录自己的脸"，
--      普通员工拿不到就没法演示模块 G 的录入→验证闭环。
--      → 把 face:enroll 下放给 EMPLOYEE，同时由服务层强制"只能录自己"
--        （管理员凭 system:user:list 才能代人录入）。
--   3) kb_document.content_hash 只建了普通列，没有索引。上传前的重复检测
--      要靠它查询，数据量上来后是全表扫。
--
-- 幂等性：
--   - sys_permission.perm_code 有唯一索引，sys_role_permission 以 (role_id, permission_id)
--     为主键，INSERT IGNORE 可安全重复执行；
--   - 索引创建用 information_schema 预检，已存在则跳过（MySQL 无 CREATE INDEX IF NOT EXISTS）。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. 补齐知识库权限码 ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    ('kb:doc:delete',  '删除知识库文档', 'knowledge'),
    ('kb:doc:rebuild', '重建文档索引',   'knowledge'),
    ('kb:qa:log',      '查看问答日志',   'knowledge');

-- ---------- 2. content_hash 加索引（支撑上传去重）----------
SET @schema_name = DATABASE();
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME   = 'kb_document'
      AND INDEX_NAME   = 'idx_content_hash'
);
SET @ddl = IF(@idx_exists = 0,
    "CREATE INDEX idx_content_hash ON kb_document (content_hash)",
    "SELECT 'skip: kb_document.idx_content_hash already exists' AS info");
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. ADMIN：始终持有全部权限 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 4. MANAGER：可上传/删除/重建文档，能看看板但看不到全员问答日志 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('kb:doc:delete', 'kb:doc:rebuild');

-- ---------- 5. EMPLOYEE：可录入并验证自己的脸（模块 G 演示闭环）----------
-- 问答日志仍不给普通员工：里面是全员提问原文。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN ('face:enroll');

-- ---------- 6. 校验输出（人工核对用）----------
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT p.perm_code, p.perm_name, GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
LEFT JOIN sys_role r           ON r.id = rp.role_id
WHERE p.perm_code LIKE 'kb:%' OR p.perm_code LIKE 'face:%'
GROUP BY p.id, p.perm_code, p.perm_name
ORDER BY p.perm_code;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;
