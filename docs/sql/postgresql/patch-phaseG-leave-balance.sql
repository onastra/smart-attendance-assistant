-- =============================================================
-- 阶段 G 增量脚本：请假额度与加班调休核算（PostgreSQL / Supabase）
--
-- 背景（对照需求书缺口）：
--   需求书要求"请假审批、加班核算、调休管理等全流程自动化、闭环式管理"。
--   此前 biz_application 只记录申请与审批结果，没有额度概念：
--   请假不限量、加班通过后无任何核算产出。本脚本补齐"额度"一环。
--
-- 口径：
--   - 每人每年一行：年假额度以小时计（默认 40h = 5 个工作日）；
--   - 已批"请假"按 duration_hours 累加 leave_used_hours（允许超额，报表体现）；
--   - 已批"调休"假别扣减调休余额，余额不足则审批失败回滚；
--   - 已批"加班"按 duration_hours 1:1 计入 compens_earned_hours（加班折调休）。
--
-- 幂等性：
--   - 表用 IF NOT EXISTS；演示额度用 ON CONFLICT DO NOTHING，可安全重复执行。
-- =============================================================

CREATE TABLE IF NOT EXISTS att_leave_balance (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id          BIGINT        NOT NULL,
    year                 INT           NOT NULL,
    leave_quota_hours    NUMERIC(7,1)  NOT NULL DEFAULT 0,
    leave_used_hours     NUMERIC(7,1)  NOT NULL DEFAULT 0,
    compens_earned_hours NUMERIC(7,1)  NOT NULL DEFAULT 0,
    compens_used_hours   NUMERIC(7,1)  NOT NULL DEFAULT 0,
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_balance_emp_year UNIQUE (employee_id, year)
);

COMMENT ON TABLE att_leave_balance IS '请假/调休额度（阶段 G：加班核算与调休闭环）';

CREATE INDEX IF NOT EXISTS idx_balance_employee ON att_leave_balance (employee_id);

-- ---------- 演示账号初始额度（当前年份，首次执行即生效） ----------
INSERT INTO att_leave_balance (employee_id, year, leave_quota_hours)
SELECT e.id, EXTRACT(YEAR FROM NOW())::int, 40
FROM hr_employee e
WHERE e.status = 1
ON CONFLICT (employee_id, year) DO NOTHING;

-- ---------- 校验输出 ----------
SELECT e.name AS employee_name, b.year, b.leave_quota_hours,
       b.leave_used_hours,
       b.compens_earned_hours - b.compens_used_hours AS compens_available
FROM att_leave_balance b
JOIN hr_employee e ON e.id = b.employee_id
ORDER BY e.id;
