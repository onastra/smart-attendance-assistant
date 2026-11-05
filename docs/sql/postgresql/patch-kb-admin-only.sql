-- =====================================================================
-- 补丁：知识库写操作收敛为管理员专属
-- 背景（真实缺陷，2026-11 发现）：
--   1) 普通员工账号仍可上传知识文档——线上库的 sys_role_permission 中
--      kb:doc:upload 被授予了非 ADMIN 角色（历史补丁/角色管理界面授予）；
--   2) 文档列表此前对所有人返回全量文档（仅带 canRead 标记），
--      员工能看到"仅自己/指定部门"的文档元信息。
--   后端已加双重防线：写操作在 KnowledgeService 内按 ADMIN 角色硬校验；
--   列表 SQL 按 PUBLIC/PRIVATE(本人)/DEPT(本部门) 过滤。本补丁负责把
--   数据库里的授权口径一并修正，避免权限管理界面显示混乱。
-- 执行方式：Supabase Dashboard → SQL Editor 直接运行。
-- 幂等：可重复执行。
-- =====================================================================

-- 1) 回收：非 ADMIN 角色一律不得拥有知识库写权限码
DELETE FROM sys_role_permission rp
USING sys_role r, sys_permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.role_code <> 'ADMIN'
  AND p.perm_code IN ('kb:doc:upload', 'kb:doc:delete', 'kb:doc:rebuild');

-- 2) 确保 ADMIN 拥有全部知识库权限（含查询与写）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.perm_code IN ('kb:doc:list', 'kb:doc:upload', 'kb:doc:delete',
                      'kb:doc:rebuild', 'kb:ask')
ON CONFLICT DO NOTHING;

-- 3) 校验输出（人工核对：写权限码应只出现在 ADMIN 一行）
SELECT p.perm_code, p.perm_name,
       string_agg(r.role_code, ', ' ORDER BY r.role_code) AS roles
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
LEFT JOIN sys_role r ON r.id = rp.role_id
WHERE p.perm_code LIKE 'kb:%'
GROUP BY p.id, p.perm_code, p.perm_name
ORDER BY p.perm_code;
