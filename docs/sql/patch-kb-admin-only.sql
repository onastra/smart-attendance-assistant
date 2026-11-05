-- =====================================================================
-- 补丁：知识库写操作收敛为管理员专属（MySQL 版）
-- 背景：普通员工仍可上传知识文档；列表此前对所有人返回全量文档。
--   后端已加双重防线（写操作按 ADMIN 角色硬校验、列表按可见范围过滤），
--   本补丁把数据库授权口径一并修正。
-- 执行方式：mysql < patch-kb-admin-only.sql 或客户端直接运行。
-- 幂等：可重复执行。
-- =====================================================================

-- 1) 回收：非 ADMIN 角色一律不得拥有知识库写权限码
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code <> 'ADMIN'
  AND p.perm_code IN ('kb:doc:upload', 'kb:doc:delete', 'kb:doc:rebuild');

-- 2) 确保 ADMIN 拥有全部知识库权限（含查询与写）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.perm_code IN ('kb:doc:list', 'kb:doc:upload', 'kb:doc:delete',
                      'kb:doc:rebuild', 'kb:ask');

-- 3) 校验输出（人工核对：写权限码应只出现在 ADMIN 一行）
SELECT p.perm_code, p.perm_name,
       GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
LEFT JOIN sys_role r ON r.id = rp.role_id
WHERE p.perm_code LIKE 'kb:%'
GROUP BY p.id, p.perm_code, p.perm_name
ORDER BY p.perm_code;
