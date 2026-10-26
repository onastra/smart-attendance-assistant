package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日汇总重算的中间行：某员工某天的流水聚合结果。
 *
 * <p>由一条 SQL 直接聚合出来（LEFT JOIN 在职员工表，无打卡的员工也会出现，
 * 记录数为 0），服务层在这之上做状态判定后写入 att_daily_stat。</p>
 */
@Data
public class DailyStatRow {

    private Long employeeId;
    private String employeeName;
    private Long deptId;

    /** 当日首次上班打卡（ON_DUTY 或 FIELD） */
    private LocalDateTime firstCheckin;

    /** 当日末次下班打卡（OFF_DUTY） */
    private LocalDateTime lastCheckout;

    /** 当日打卡总条数，0 表示没来 */
    private long recordCount;

    /** 当日演示数据条数：>0 且等于总条数时，汇总行的 source 记为 SAMPLE */
    private long sampleCount;

    /** 用于工时兜底：只有上班卡没有下班卡时工时为 0，不能算成负数 */
    public boolean hasCheckout() {
        return lastCheckout != null;
    }
}
