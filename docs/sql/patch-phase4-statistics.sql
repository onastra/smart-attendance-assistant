-- =============================================================
-- 阶段 4 增量脚本：统计与可视化（日汇总 + 统计接口 + 看板）
-- 目标版本：MySQL 5.7（本机 5.7.19）
--
-- 背景（真实缺陷 / 口径补充，非虚构）：
--   1) att_record.source 建表注释只写了 H5/FACE_MOCK/ADMIN 三种来源，
--      但阶段 4 补录的 14 天演示数据需要一个能自证身份的取值，
--      否则"真实统计与示例数据"（需求 IDX-D8）在流水层面无法区分。
--      列本身是 VARCHAR(16) 无需改结构，这里只更新注释，把 SAMPLE 纳入约定。
--   2) 现有权限码只有 statistics:view（查看看板），没有"重算日汇总"的权限。
--      重算会改写 att_daily_stat，属于写操作，不能让所有能看看板的人触发
--      → 新增 statistics:rebuild，只给 ADMIN。
--
-- 幂等性：
--   - MODIFY COLUMN 重复执行结果一致（不丢数据，只改注释）；
--   - sys_permission.perm_code 有唯一索引，sys_role_permission 以 (role_id, permission_id)
--     为主键，INSERT IGNORE 可安全重复执行。
-- =============================================================

SET NAMES utf8mb4;

-- ---------- 1. att_record.source 注释纳入 SAMPLE ----------
ALTER TABLE att_record
    MODIFY COLUMN source VARCHAR(16) NOT NULL DEFAULT 'H5'
    COMMENT '来源：H5=扫码打卡 / FACE_MOCK=人脸模拟适配层 / ADMIN=后台补录 / SAMPLE=演示补录数据';

-- ---------- 2. att_daily_stat 补 checkin_count ----------
-- 日汇总原本只有工时，看板的趋势图要看"打卡人次"时只能回流水表再聚合一次，
-- 等于把汇总表的意义又丢掉了。这里补一列，重算时直接落库。
SET @schema_name = DATABASE();
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME   = 'att_daily_stat'
      AND COLUMN_NAME  = 'checkin_count'
);
SET @ddl = IF(@col_exists = 0,
    "ALTER TABLE att_daily_stat ADD COLUMN checkin_count INT NOT NULL DEFAULT 0 COMMENT '当日打卡次数' AFTER work_minutes",
    "SELECT 'skip: att_daily_stat.checkin_count already exists' AS info");
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 新增「重算日汇总」权限码 ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, module) VALUES
    ('statistics:rebuild', '重算考勤日汇总', 'statistics');

-- ---------- 3. ADMIN：始终持有全部权限 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ---------- 4. MANAGER：能看看板，但不能触发重算 ----------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN ('statistics:view');

-- ---------- 5. 校验输出（人工核对用）----------
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.id;
