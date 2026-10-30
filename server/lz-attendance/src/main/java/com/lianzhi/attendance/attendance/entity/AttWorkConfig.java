package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 全局上下班时间配置（att_work_config，单行表 id=1）。
 *
 * <p>系统当前口径是"全局统一班次"：所有员工共用一份上下班时间与宽限分钟数，
 * 迟到/早退的判定边界全部由这四个值推导——
 * 迟到 = 上班打卡晚于 workStartTime + lateGraceMinutes；
 * 早退 = 下班打卡早于 workEndTime - earlyLeaveGraceMinutes。</p>
 *
 * <p>配置落库而非只放在 yml：管理员在 Web 端修改后立即生效（打卡即时判定与
 * 日汇总重算共用本表），无需改配置重启；yml 里的 lz.attendance.work-* 仅作为
 * 表为空时的兜底默认值。</p>
 */
@Data
public class AttWorkConfig {

    public static final int SINGLETON_ID = 1;

    private Integer id;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    private Integer lateGraceMinutes;

    private Integer earlyLeaveGraceMinutes;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    // ===== 展示辅助字段（SQL 别名填充）=====
    private String updatedByName;
}
