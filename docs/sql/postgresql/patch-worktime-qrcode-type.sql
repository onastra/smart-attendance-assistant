-- ============================================================
-- 补丁：打卡类型绑定二维码 + 全局上下班时间配置（2026-10-30）
--
-- 背景：
-- 1. 管理员/主管签发二维码时需要指定该码用于「上班/下班/外勤」打卡，
--    打卡时以码上绑定的类型为准（AUTO 表示沿用旧行为：按打卡时间自动判定）。
-- 2. 迟到/早退判定所用的上下班时间与宽限分钟数原先只存在于 yml 配置，
--    调整必须改配置并重启；现落库为全局单行配置表，Web 端可在线修改、立即生效，
--    "给所有员工设置上下班时间"（系统当前口径：全局统一班次）。
--
-- 幂等：可重复执行。
-- ============================================================

-- 1) 二维码绑定打卡类型：AUTO=自动判定（默认，兼容存量码）
ALTER TABLE att_qrcode
    ADD COLUMN IF NOT EXISTS checkin_type VARCHAR(16) NOT NULL DEFAULT 'AUTO';
COMMENT ON COLUMN att_qrcode.checkin_type IS
    '该码绑定的打卡类型：AUTO/ON_DUTY/OFF_DUTY/FIELD，打卡时非 AUTO 以码为准';

-- 2) 全局考勤时间配置（单行表，id 恒为 1）
CREATE TABLE IF NOT EXISTS att_work_config (
    id                         INT         PRIMARY KEY DEFAULT 1,
    work_start_time            TIME        NOT NULL DEFAULT '09:00',
    work_end_time              TIME        NOT NULL DEFAULT '18:00',
    late_grace_minutes         INT         NOT NULL DEFAULT 15,
    early_leave_grace_minutes  INT         NOT NULL DEFAULT 0,
    updated_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 BIGINT               DEFAULT NULL,
    CONSTRAINT ck_work_config_id CHECK (id = 1)
);
COMMENT ON TABLE att_work_config IS
    '全局上下班时间配置（单行）：迟到=上班打卡晚于 work_start_time+late_grace_minutes，早退=下班打卡早于 work_end_time-early_leave_grace_minutes';

INSERT INTO att_work_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- 3) 校验输出
SELECT 'att_qrcode.checkin_type' AS item,
       COUNT(*) AS rows_with_column
FROM information_schema.columns
WHERE table_name = 'att_qrcode' AND column_name = 'checkin_type';

SELECT * FROM att_work_config;
